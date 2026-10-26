package schultz.thomas.schub.bff.api.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import schultz.thomas.schub.bff.business.service.PlayerCardRenderer;
import schultz.thomas.schub.bff.business.service.PlayerSeoService;
import schultz.thomas.schub.bff.business.service.TierCombosSeoService;
import schultz.thomas.schub.bff.config.security.DiscordOAuthProperties;
import schultz.thomas.schub.bff.config.security.FrontRegistry;
import schultz.thomas.schub.bff.config.security.FrontsProperties;

import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeoControllerTest {

    private PlayerSeoService seo;
    private MockMvc mvc;

    private TierCombosSeoService combos;

    @BeforeEach
    void setUp() {
        seo = mock(PlayerSeoService.class);
        combos = mock(TierCombosSeoService.class);
        FrontRegistry fronts = new FrontRegistry(new FrontsProperties(List.of(
                new FrontsProperties.Front("premadelab", "https://premadelab.eu",
                        "https://premadelab.eu/api/auth/discord/callback", "/"))),
                new DiscordOAuthProperties(null, null, null, null, null, null, null, true));
        mvc = MockMvcBuilders.standaloneSetup(new SeoController(seo, new PlayerCardRenderer(), fronts,
                mock(schultz.thomas.schub.bff.business.service.TeamSeoService.class), combos, 20)).build();
        PlayerSeoService.Summary resume = new PlayerSeoService.Summary(
                "Le <b>Nom", "EUW", "Le <b>Nom-EUW",
                new PlayerSeoService.Rank("RANKED_SOLO_5x5", "GOLD", "II", 45, 30, 25),
                55, 0.545, 3.14, List.of("Ahri", "Lux"));
        when(seo.summary("Le <b>Nom-EUW")).thenReturn(Optional.of(resume));
        when(seo.lookup("Le <b>Nom-EUW")).thenReturn(new PlayerSeoService.Lookup(true, Optional.of(resume)));
    }

    @Test
    @DisplayName("le head porte titre, description et aperçu Open Graph, échappés, avec des URL absolues du front")
    void head() throws Exception {
        String html = mvc.perform(get("/seo/players/{slug}/head", "Le <b>Nom-EUW")
                        .header("X-Forwarded-Host", "premadelab.eu"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("<title>Le &lt;b&gt;Nom#EUW — stats, duos et parties en premade | PremadeLab</title>")
                .contains("Gold II · 45 LP")
                .contains("55 parties analysées, 55 % de victoires, KDA 3,1")
                .contains("og:image\" content=\"https://premadelab.eu/api/seo/players/Le%20%3Cb%3ENom-EUW/card.png")
                .contains("<link rel=\"canonical\" href=\"https://premadelab.eu/players/Le%20%3Cb%3ENom-EUW\" />")
                .contains("hreflang=\"en\" href=\"https://premadelab.eu/en/players/Le%20%3Cb%3ENom-EUW\"")
                .doesNotContain("noindex")
                .doesNotContain("<b>");
    }

    @Test
    @DisplayName("une page de combos par palier se lit sans JavaScript, avec ses liens vers les autres paliers (Schub#112)")
    void combosParPalier() throws Exception {
        when(combos.page("SILVER_GOLD", "ADC_SUP")).thenReturn(java.util.Optional.of(new TierCombosSeoService.Page(
                "SILVER_GOLD", "ADC_SUP", 1200, List.of(
                new TierCombosSeoService.Line(List.of("Ashe", "Thresh"), 40, 26)))));

        String head = mvc.perform(get("/seo/combos/{tier}/{kind}/head", "silver-gold", "adc-sup")
                        .header("X-Forwarded-Host", "premadelab.eu"))
                .andReturn().getResponse().getContentAsString();
        String body = mvc.perform(get("/seo/combos/{tier}/{kind}/body", "silver-gold", "adc-sup"))
                .andReturn().getResponse().getContentAsString();

        assertThat(head).contains("<title>Meilleurs duos bas (tireur + support) en Argent–Or — League of Legends | PremadeLab</title>")
                .contains("<link rel=\"canonical\" href=\"https://premadelab.eu/combos/silver-gold/adc-sup\" />");
        assertThat(body).contains("<li>Ashe + Thresh — 65 % de victoires sur 40 parties</li>")
                .contains("href=\"/combos/diamond/adc-sup\"")
                .contains("href=\"/combos/silver-gold/jgl-mid\"");
    }

    @Test
    @DisplayName("une page de combos inconnue n'est pas indexée")
    void combosInconnus() throws Exception {
        String head = mvc.perform(get("/seo/combos/{tier}/{kind}/head", "bronze", "adc-sup"))
                .andReturn().getResponse().getContentAsString();

        assertThat(head).contains("noindex");
    }

    @Test
    @DisplayName("la version anglaise se déclare canonique pour elle-même")
    void headAnglais() throws Exception {
        String html = mvc.perform(get("/seo/players/{slug}/head", "Le <b>Nom-EUW").param("lang", "/en")
                        .header("X-Forwarded-Host", "premadelab.eu"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("<link rel=\"canonical\" href=\"https://premadelab.eu/en/players/Le%20%3Cb%3ENom-EUW\" />")
                .contains("games analysed");
    }

    @Test
    @DisplayName("le plan du site est un index : les pages de contenu d'un côté, les profils de l'autre")
    void planDuSite() throws Exception {
        String xml = mvc.perform(get("/seo/sitemap.xml").header("X-Forwarded-Host", "premadelab.eu"))
                .andReturn().getResponse().getContentAsString();

        assertThat(xml).contains("<sitemapindex")
                .contains("<sitemap><loc>https://premadelab.eu/sitemap-pages.xml</loc></sitemap>")
                .contains("<sitemap><loc>https://premadelab.eu/sitemap-players.xml</loc></sitemap>")
                .doesNotContain("/players/");
    }

    @Test
    @DisplayName("les pages publiques sont listées dans les deux langues, chacune annonçant l'autre")
    void planDesPages() throws Exception {
        String xml = mvc.perform(get("/seo/sitemap-pages.xml").header("X-Forwarded-Host", "premadelab.eu"))
                .andReturn().getResponse().getContentAsString();

        String alternatives = "<xhtml:link rel=\"alternate\" hreflang=\"fr\" href=\"https://premadelab.eu/ruler\"/>"
                + "<xhtml:link rel=\"alternate\" hreflang=\"en\" href=\"https://premadelab.eu/en/ruler\"/>"
                + "<xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"https://premadelab.eu/ruler\"/>";
        assertThat(xml).contains("xmlns:xhtml=\"http://www.w3.org/1999/xhtml\"")
                .contains("<url><loc>https://premadelab.eu/ruler</loc>" + alternatives + "</url>")
                .contains("<url><loc>https://premadelab.eu/en/ruler</loc>" + alternatives + "</url>")
                .contains("<loc>https://premadelab.eu/en/presentation</loc>")
                .contains("<loc>https://premadelab.eu/privacy</loc>")
                .contains("<loc>https://premadelab.eu/en</loc>")
                .doesNotContain("/players/");
    }

    @Test
    @DisplayName("les profils ont leur fichier, chacun une fois, en français")
    void planDesProfils() throws Exception {
        when(seo.indexableProfiles(org.mockito.ArgumentMatchers.anyInt())).thenReturn(List.of(
                new PlayerSeoService.IndexedProfile("Le Nom#EUW", false),
                new PlayerSeoService.IndexedProfile("Apex#EUW", true)));

        String xml = mvc.perform(get("/seo/sitemap-players.xml").header("X-Forwarded-Host", "premadelab.eu"))
                .andReturn().getResponse().getContentAsString();

        assertThat(xml).contains("<loc>https://premadelab.eu/players/Le%20Nom-EUW</loc></url>")
                .contains("<loc>https://premadelab.eu/players/Apex-EUW</loc><priority>0.8</priority>")
                .doesNotContain("/en/players/")
                .doesNotContain("/presentation");
    }

    @Test
    @DisplayName("un profil sous 20 parties n'est pas indexé")
    void sousLeSeuil() throws Exception {
        PlayerSeoService.Summary maigre = new PlayerSeoService.Summary("Maigre", "EUW", "Maigre-EUW", null, 19, 0.5,
                2.0, List.of("Ahri"));
        when(seo.lookup("Maigre-EUW")).thenReturn(new PlayerSeoService.Lookup(true, Optional.of(maigre)));

        String html = mvc.perform(get("/seo/players/{slug}/head", "Maigre-EUW"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("noindex");
    }

    @Test
    @DisplayName("un profil sans partie relevée n'est pas indexé, et sa description reste propre")
    void sansPartie() throws Exception {
        PlayerSeoService.Summary vide = new PlayerSeoService.Summary("Vide", "EUW", "Vide-EUW", null, 0, null, null,
                List.of());
        when(seo.lookup("Vide-EUW")).thenReturn(new PlayerSeoService.Lookup(true, Optional.of(vide)));

        String html = mvc.perform(get("/seo/players/{slug}/head", "Vide-EUW"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("noindex").contains("content=\"Vide#EUW : non classé.\"");
    }

    @Test
    @DisplayName("un joueur introuvable n'est pas indexé")
    void introuvable() throws Exception {
        when(seo.lookup("Personne-000")).thenReturn(new PlayerSeoService.Lookup(false, Optional.empty()));

        String html = mvc.perform(get("/seo/players/{slug}/head", "Personne-000"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).contains("noindex").contains("Personne#000");
    }

    private static boolean policesLisibles() {
        try (var police = SeoControllerTest.class.getResourceAsStream("/fonts/DejaVuSans.ttf")) {
            java.awt.Font.createFont(java.awt.Font.TRUETYPE_FONT, police);
            return true;
        } catch (Exception | Error illisible) {
            return false;
        }
    }

    @Test
    @DisplayName("une panne passagère ne retire pas la page de l'index")
    void panne() throws Exception {
        when(seo.lookup("Occupe-EUW")).thenReturn(new PlayerSeoService.Lookup(true, Optional.empty()));

        String html = mvc.perform(get("/seo/players/{slug}/head", "Occupe-EUW"))
                .andReturn().getResponse().getContentAsString();

        assertThat(html).doesNotContain("noindex").contains("rel=\"canonical\"");
    }

    @Test
    @DisplayName("la carte est une image PNG de 1200 × 630")
    void carte() throws Exception {
        // Le conteneur d'édition n'a pas les bibliothèques de polices ; les images du BFF (dev et prod) les ont.
        assumeTrue(policesLisibles(), "polices illisibles par ce JDK");
        byte[] png = mvc.perform(get("/seo/players/{slug}/card.png", "Le <b>Nom-EUW"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(png));
        assertThat(image.getWidth()).isEqualTo(1200);
        assertThat(image.getHeight()).isEqualTo(630);
    }
}

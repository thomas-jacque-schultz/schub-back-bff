package schultz.thomas.schub.bff.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;
import schultz.thomas.schub.bff.business.service.PlayerCardRenderer;
import schultz.thomas.schub.bff.business.service.PlayerSeoService;
import schultz.thomas.schub.bff.business.service.PlayerSeoService.Rank;
import schultz.thomas.schub.bff.business.service.PlayerSeoService.Summary;
import schultz.thomas.schub.bff.config.security.FrontRegistry;
import schultz.thomas.schub.bff.config.security.FrontsProperties.Front;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

// Les pages de joueur de PremadeLab lisibles sans JavaScript : nginx insère ces fragments par SSI.
@RestController
@RequestMapping("/seo")
public class SeoController {

    private static final MediaType HTML = new MediaType("text", "html", StandardCharsets.UTF_8);
    private static final Set<String> SANS_DIVISION = Set.of("MASTER", "GRANDMASTER", "CHALLENGER");
    private static final List<String> PAGES = List.of("/", "/presentation", "/contact", "/terms", "/privacy");
    // Une URL par profil, en français : la version anglaise est annoncée par le hreflang de la page.
    private static final int PROFILS_MAX = 49_000;
    private static final String OR = "#C9A227";
    private static final String TEXTE = "#F2E9EE";

    private final PlayerSeoService seo;
    private final PlayerCardRenderer cards;
    private final FrontRegistry fronts;

    public SeoController(PlayerSeoService seo, PlayerCardRenderer cards, FrontRegistry fronts) {
        this.seo = seo;
        this.cards = cards;
        this.fronts = fronts;
    }

    @GetMapping("/players/{slug}/head")
    public ResponseEntity<String> head(@PathVariable String slug, @RequestParam(required = false) String lang,
                                       HttpServletRequest request) {
        boolean en = anglais(lang);
        String origine = origine(request);
        PlayerSeoService.Lookup recherche = seo.lookup(slug);
        Optional<Summary> resume = recherche.summary();

        StringBuilder html = new StringBuilder();
        if (!recherche.found()) {
            html.append(balise("title", texte(slug.replaceFirst("-([^-]*)$", "#$1")) + " | PremadeLab"))
                    .append("<meta name=\"robots\" content=\"noindex\" />");
            return fragment(html.toString());
        }
        String profil = "/players/" + UriUtils.encodePathSegment(resume.map(Summary::slug).orElse(slug),
                StandardCharsets.UTF_8);
        String chemin = (en ? "/en" : "") + profil;
        html.append("<link rel=\"canonical\" href=\"").append(texte(origine + chemin)).append("\" />")
                .append(alternative("fr", origine + profil))
                .append(alternative("en", origine + "/en" + profil))
                .append(alternative("x-default", origine + profil));
        // Une panne passagère ne retire pas la page de l'index : titre seul, React complète au montage.
        if (resume.isEmpty()) {
            html.append(balise("title", texte(slug.replaceFirst("-([^-]*)$", "#$1")) + " | PremadeLab"));
            return fragment(html.toString());
        }
        Summary joueur = resume.get();
        String titre = en
                ? joueur.riotId() + " — stats, duos and premade games | PremadeLab"
                : joueur.riotId() + " — stats, duos et parties en premade | PremadeLab";
        String description = description(joueur, en);
        String image = origine + "/api/seo/players/" + UriUtils.encodePathSegment(joueur.slug(), StandardCharsets.UTF_8)
                + "/card.png";

        html.append(balise("title", texte(titre)))
                .append(meta("name", "description", description))
                .append(meta("property", "og:type", "profile"))
                .append(meta("property", "og:locale", en ? "en_GB" : "fr_FR"))
                .append(meta("property", "og:site_name", "PremadeLab"))
                .append(meta("property", "og:title", titre))
                .append(meta("property", "og:description", description))
                .append(meta("property", "og:url", origine + chemin))
                .append(meta("property", "og:image", image))
                .append(meta("property", "og:image:width", "1200"))
                .append(meta("property", "og:image:height", "630"))
                .append(meta("name", "twitter:card", "summary_large_image"))
                .append(meta("name", "twitter:image", image));
        return fragment(html.toString());
    }

    @GetMapping("/players/{slug}/body")
    public ResponseEntity<String> body(@PathVariable String slug, @RequestParam(required = false) String lang) {
        boolean en = anglais(lang);
        Optional<Summary> resume = seo.summary(slug);
        StringBuilder html = new StringBuilder(
                "<main style=\"max-width:960px;margin:48px auto;padding:0 16px;font-family:sans-serif;color:" + TEXTE + "\">")
                .append("<p style=\"color:" + OR + "\">PremadeLab</p>");
        if (resume.isEmpty()) {
            html.append(balise("h1", texte(slug.replaceFirst("-([^-]*)$", "#$1"))));
        } else {
            Summary joueur = resume.get();
            html.append(balise("h1", texte(joueur.riotId())));
            String rang = rang(joueur.solo(), en);
            if (rang != null) {
                html.append(balise("p", texte(rang)));
            }
            html.append("<ul>");
            for (String ligne : chiffres(joueur, en)) {
                html.append(balise("li", texte(ligne)));
            }
            html.append("</ul>");
        }
        html.append("<p><a style=\"color:" + OR + "\" href=\"").append(en ? "/en" : "/").append("\">")
                .append(en ? "Look up another player" : "Chercher un autre joueur").append("</a> · ")
                .append("<a style=\"color:" + OR + "\" href=\"").append(en ? "/en" : "").append("/presentation\">")
                .append(en ? "What PremadeLab does" : "Ce que fait PremadeLab").append("</a></p></main>");
        return fragment(html.toString());
    }

    @GetMapping(value = "/players/{slug}/card.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> card(@PathVariable String slug, @RequestParam(required = false) String lang) {
        boolean en = anglais(lang);
        return seo.summary(slug)
                .map(joueur -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                        .contentType(MediaType.IMAGE_PNG)
                        .body(cards.render(joueur.riotId(), rang(joueur.solo(), en), chiffres(joueur, en))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap(HttpServletRequest request) {
        String origine = origine(request);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        List<String> chemins = new ArrayList<>(PAGES);
        PAGES.forEach(page -> chemins.add("/en" + (page.equals("/") ? "" : page)));
        for (String riotId : seo.trackedRiotIds(PROFILS_MAX)) {
            chemins.add("/players/" + UriUtils.encodePathSegment(riotId.replaceFirst("#([^#]*)$", "-$1"),
                    StandardCharsets.UTF_8));
        }
        for (String chemin : chemins) {
            xml.append("  <url><loc>").append(texte(origine + chemin)).append("</loc></url>\n");
        }
        xml.append("</urlset>\n");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
                .contentType(MediaType.APPLICATION_XML)
                .body(xml.toString());
    }

    // L'origine vient de la liste des fronts, jamais de l'en-tête Host. Hôte inconnu : URL relatives.
    private String origine(HttpServletRequest request) {
        return fronts.of(request).map(Front::origin).orElse("");
    }

    private static String description(Summary joueur, boolean en) {
        StringBuilder texte = new StringBuilder(joueur.riotId());
        String rang = rang(joueur.solo(), en);
        texte.append(en ? ": " : " : ").append(rang == null ? (en ? "unranked" : "non classé") : rang).append(". ");
        texte.append(String.join(", ", chiffres(joueur, en))).append('.');
        return texte.toString();
    }

    private static List<String> chiffres(Summary joueur, boolean en) {
        Locale locale = en ? Locale.UK : Locale.FRANCE;
        List<String> lignes = new ArrayList<>();
        if (joueur.games() > 0) {
            lignes.add(en ? joueur.games() + " games analysed" : joueur.games() + " parties analysées");
        }
        if (joueur.winRate() != null) {
            long pourcent = Math.round(joueur.winRate() * 100);
            lignes.add(en ? pourcent + "% win rate" : pourcent + " % de victoires");
        }
        if (joueur.kda() != null) {
            lignes.add("KDA " + String.format(locale, "%.1f", joueur.kda()));
        }
        if (!joueur.champions().isEmpty()) {
            lignes.add((en ? "Champions: " : "Champions : ") + String.join(", ", joueur.champions()));
        }
        return lignes;
    }

    private static String rang(Rank rang, boolean en) {
        if (rang == null) {
            return null;
        }
        String palier = rang.tier().charAt(0) + rang.tier().substring(1).toLowerCase(Locale.ROOT);
        String division = SANS_DIVISION.contains(rang.tier()) ? "" : " " + rang.division();
        return palier + division + " · " + rang.leaguePoints() + " LP";
    }

    private static boolean anglais(String lang) {
        return lang != null && lang.contains("en");
    }

    private static String texte(String brut) {
        return HtmlUtils.htmlEscape(brut, StandardCharsets.UTF_8.name());
    }

    private static String balise(String nom, String contenu) {
        return "<" + nom + ">" + contenu + "</" + nom + ">";
    }

    private static String alternative(String langue, String url) {
        return "<link rel=\"alternate\" hreflang=\"" + langue + "\" href=\"" + texte(url) + "\" />";
    }

    private static String meta(String attribut, String nom, String contenu) {
        return "<meta " + attribut + "=\"" + nom + "\" content=\"" + texte(contenu) + "\" />";
    }

    private static ResponseEntity<String> fragment(String html) {
        return ResponseEntity.ok().contentType(HTML).body(html);
    }
}

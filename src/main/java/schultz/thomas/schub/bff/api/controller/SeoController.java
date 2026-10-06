package schultz.thomas.schub.bff.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
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
import schultz.thomas.schub.bff.business.service.TeamSeoService;
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
    private final TeamSeoService teams;
    private final int minIndexedGames;

    // Même seuil que riot.seo.min-games du connecteur, qui choisit les profils du plan du site.
    public SeoController(PlayerSeoService seo, PlayerCardRenderer cards, FrontRegistry fronts, TeamSeoService teams,
                         @Value("${seo.min-indexed-games:20}") int minIndexedGames) {
        this.seo = seo;
        this.cards = cards;
        this.fronts = fronts;
        this.teams = teams;
        this.minIndexedGames = minIndexedGames;
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
                .append(meta("name", "description", description));
        // Sous le seuil, la page est trop maigre : un robot jugerait le site sur des pages creuses (Schub#91).
        if (joueur.games() < minIndexedGames) {
            html.append("<meta name=\"robots\" content=\"noindex\" />");
        }
        html
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
            List<String> compagnons = seo.companions(slug);
            if (!compagnons.isEmpty()) {
                html.append(balise("h2", en ? "Often plays with" : "Joue souvent avec")).append("<ul>");
                for (String compagnon : compagnons) {
                    String lien = (en ? "/en" : "") + "/players/" + UriUtils.encodePathSegment(
                            compagnon.replaceFirst("#([^#]*)$", "-$1"), StandardCharsets.UTF_8);
                    html.append("<li><a style=\"color:" + OR + "\" href=\"").append(texte(lien)).append("\">")
                            .append(texte(compagnon)).append("</a></li>");
                }
                html.append("</ul>");
            }
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

    // Équipe privée ou absente : titre seul et noindex ; React affiche la page ou la 404 au montage.
    @GetMapping("/teams/{teamId}/head")
    public ResponseEntity<String> teamHead(@PathVariable String teamId, @RequestParam(required = false) String lang,
                                           HttpServletRequest request) {
        boolean en = anglais(lang);
        String origine = origine(request);
        Optional<TeamSeoService.PublicTeam> equipe = teams.team(teamId);
        if (equipe.isEmpty()) {
            return fragment(balise("title", "PremadeLab") + "<meta name=\"robots\" content=\"noindex\" />");
        }
        TeamSeoService.PublicTeam t = equipe.get();
        String page = "/teams/" + UriUtils.encodePathSegment(teamId, StandardCharsets.UTF_8);
        String titre = t.name() + (en ? " — League of Legends premade | PremadeLab" : " — premade League of Legends | PremadeLab");
        String description = descriptionEquipe(t, en);
        String image = origine + "/api/seo/teams/" + UriUtils.encodePathSegment(teamId, StandardCharsets.UTF_8)
                + "/card.png";
        return fragment(new StringBuilder()
                .append("<link rel=\"canonical\" href=\"").append(texte(origine + (en ? "/en" : "") + page)).append("\" />")
                .append(alternative("fr", origine + page))
                .append(alternative("en", origine + "/en" + page))
                .append(alternative("x-default", origine + page))
                .append(balise("title", texte(titre)))
                .append(meta("name", "description", description))
                .append(meta("property", "og:type", "website"))
                .append(meta("property", "og:site_name", "PremadeLab"))
                .append(meta("property", "og:title", titre))
                .append(meta("property", "og:description", description))
                .append(meta("property", "og:image", image))
                .append(meta("property", "og:image:width", "1200"))
                .append(meta("property", "og:image:height", "630"))
                .append(meta("name", "twitter:card", "summary_large_image"))
                .append(meta("name", "twitter:image", image))
                .toString());
    }

    @GetMapping("/teams/{teamId}/body")
    public ResponseEntity<String> teamBody(@PathVariable String teamId, @RequestParam(required = false) String lang) {
        boolean en = anglais(lang);
        StringBuilder html = new StringBuilder(
                "<main style=\"max-width:960px;margin:48px auto;padding:0 16px;font-family:sans-serif;color:" + TEXTE + "\">")
                .append("<p style=\"color:" + OR + "\">PremadeLab</p>");
        teams.team(teamId).ifPresent(t -> {
            html.append(balise("h1", texte(t.name())))
                    .append(balise("p", texte(descriptionEquipe(t, en))))
                    .append("<ul>");
            for (String membre : t.members()) {
                String lien = (en ? "/en" : "") + "/players/" + UriUtils.encodePathSegment(
                        membre.replaceFirst("#([^#]*)$", "-$1"), StandardCharsets.UTF_8);
                html.append("<li><a style=\"color:" + OR + "\" href=\"").append(texte(lien)).append("\">")
                        .append(texte(membre)).append("</a></li>");
            }
            html.append("</ul>");
        });
        html.append("<p><a style=\"color:" + OR + "\" href=\"").append(en ? "/en" : "/").append("\">")
                .append(en ? "Look up a player" : "Chercher un joueur").append("</a></p></main>");
        return fragment(html.toString());
    }

    @GetMapping(value = "/teams/{teamId}/card.png", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> teamCard(@PathVariable String teamId, @RequestParam(required = false) String lang) {
        boolean en = anglais(lang);
        return teams.team(teamId)
                .map(t -> ResponseEntity.ok()
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                        .contentType(MediaType.IMAGE_PNG)
                        .body(cards.render(t.name(), en ? "League of Legends premade" : "Premade League of Legends",
                                chiffresEquipe(t, en))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private static String descriptionEquipe(TeamSeoService.PublicTeam t, boolean en) {
        List<String> chiffres = chiffresEquipe(t, en);
        return t.name() + (en ? ": " : " : ") + String.join(", ", chiffres) + ".";
    }

    private static List<String> chiffresEquipe(TeamSeoService.PublicTeam t, boolean en) {
        List<String> lignes = new ArrayList<>();
        lignes.add(en ? t.games() + " games together" : t.games() + " parties ensemble");
        if (t.games() > 0) {
            long pourcent = Math.round(100.0 * t.wins() / t.games());
            lignes.add(en ? pourcent + "% win rate" : pourcent + " % de victoires");
        }
        if (t.duoA() != null) {
            lignes.add((en ? "Best duo: " : "Meilleur duo : ") + t.duoA() + " + " + t.duoB());
        }
        if (!t.champions().isEmpty()) {
            lignes.add((en ? "Champions: " : "Champions : ") + String.join(", ", t.champions().subList(0,
                    Math.min(3, t.champions().size()))));
        }
        return lignes;
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap(HttpServletRequest request) {
        String origine = origine(request);
        StringBuilder xml = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
                .append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">\n");
        List<String> chemins = new ArrayList<>(PAGES);
        PAGES.forEach(page -> chemins.add("/en" + (page.equals("/") ? "" : page)));
        for (String chemin : chemins) {
            xml.append("  <url><loc>").append(texte(origine + chemin)).append("</loc></url>\n");
        }
        for (TeamSeoService.Listed equipe : teams.listed()) {
            xml.append("  <url><loc>").append(texte(origine + "/teams/"
                    + UriUtils.encodePathSegment(equipe.id(), StandardCharsets.UTF_8))).append("</loc>")
                    .append("<priority>0.8</priority></url>\n");
        }
        for (PlayerSeoService.IndexedProfile profil : seo.indexableProfiles(PROFILS_MAX)) {
            String chemin = "/players/" + UriUtils.encodePathSegment(
                    profil.riotId().replaceFirst("#([^#]*)$", "-$1"), StandardCharsets.UTF_8);
            xml.append("  <url><loc>").append(texte(origine + chemin)).append("</loc>")
                    .append(profil.featured() ? "<priority>0.8</priority>" : "")
                    .append("</url>\n");
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
        String rang = rang(joueur.solo(), en);
        String texte = joueur.riotId() + (en ? ": " : " : ") + (rang == null ? (en ? "unranked" : "non classé") : rang) + ".";
        List<String> chiffres = chiffres(joueur, en);
        return chiffres.isEmpty() ? texte : texte + " " + String.join(", ", chiffres) + ".";
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

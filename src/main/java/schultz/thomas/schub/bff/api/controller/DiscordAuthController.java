package schultz.thomas.schub.bff.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.bff.business.service.DiscordOAuthService;
import schultz.thomas.schub.bff.business.service.IdentityService;
import schultz.thomas.schub.bff.config.security.AuthCookies;
import schultz.thomas.schub.bff.config.security.DiscordOAuthProperties;
import schultz.thomas.schub.bff.config.security.JwtProperties;
import schultz.thomas.schub.bff.config.security.JwtService;
import schultz.thomas.schub.bff.data.client.UserIdentityDto;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * La connexion Discord — lot A.3.
 *
 * <pre>
 *   GET /auth/discord           → 302 vers Discord (scope `identify` seul)
 *   GET /auth/discord/callback  → échange du code, /users/@me, appel au cœur, cookie, 302 vers /
 * </pre>
 *
 * <p><strong>Le BFF reste sans état.</strong> Il ne lit pas Mongo et ne décide de rien sur
 * l'identité : il demande au cœur qui est ce compte Discord, et le cœur le crée au rôle
 * {@code VISITEUR} s'il est inconnu (décision n°2). Le cœur garde la propriété de l'identité.</p>
 *
 * <p><strong>Où vit le {@code state}</strong> : dans un cookie {@code httpOnly} de dix minutes,
 * faute de session serveur où le déposer — voir {@link AuthCookies}. Le vérifieur PKCE
 * l'accompagne dans un second cookie de même durée. Les deux sont effacés dès l'entrée dans le
 * callback, avant même d'être validés : ils sont à usage unique, et un {@code state} qui traîne
 * derrière un échec peut être rejoué.</p>
 *
 * <p>Ce contrôleur est volontairement séparé de {@link AuthController}, qui porte le compte par
 * mot de passe : celui-ci se supprimera d'un bloc au lot A.6, sans toucher à celui-là.</p>
 */
@RestController
@RequestMapping("/auth/discord")
public class DiscordAuthController {

    private static final Logger log = LoggerFactory.getLogger(DiscordAuthController.class);

    private final DiscordOAuthService discord;
    private final DiscordOAuthProperties properties;
    private final IdentityService identityService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthCookies cookies;

    public DiscordAuthController(DiscordOAuthService discord,
                                 DiscordOAuthProperties properties,
                                 IdentityService identityService,
                                 JwtService jwtService,
                                 JwtProperties jwtProperties,
                                 AuthCookies cookies) {
        this.discord = discord;
        this.properties = properties;
        this.identityService = identityService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.cookies = cookies;
    }

    /**
     * Démarre le flux. Le front n'a qu'à envoyer le navigateur ici — il n'orchestre rien, et
     * c'est précisément ce que le cookie permet.
     */
    @GetMapping
    public ResponseEntity<?> start() {
        if (!properties.configured()) {
            // 503 et pas 500 : ce n'est pas un bug, c'est un déploiement incomplet. Le message
            // nomme les variables manquantes sans jamais citer leur valeur.
            log.error("Connexion Discord non configurée — DISCORD_OAUTH_CLIENT_ID, "
                    + "DISCORD_OAUTH_CLIENT_SECRET et DISCORD_OAUTH_REDIRECT_URI sont requis");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "La connexion Discord n'est pas configurée sur ce déploiement"));
        }

        String state = discord.randomUrlSafeValue();
        String verifier = discord.randomUrlSafeValue();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookies.oauthState(state))
                .header(HttpHeaders.SET_COOKIE, cookies.oauthVerifier(verifier))
                .location(URI.create(discord.authorizationUrl(state, verifier)))
                .build();
    }

    /**
     * Le retour de Discord.
     *
     * <p>Les codes de réponse sont volontairement francs plutôt qu'enrobés dans une redirection :
     * un {@code state} qui ne correspond pas est une tentative, pas un incident d'usage, et une
     * panne du cœur doit se voir. Seul le refus explicite de l'utilisateur devant l'écran
     * d'autorisation renvoie au site, puisque c'est un choix et non une erreur.</p>
     */
    @GetMapping("/callback")
    public ResponseEntity<?> callback(HttpServletRequest request,
                                      @RequestParam(required = false) String code,
                                      @RequestParam(required = false) String state,
                                      @RequestParam(required = false) String error) {
        Optional<String> expectedState = cookies.read(request, AuthCookies.OAUTH_STATE);
        Optional<String> verifier = cookies.read(request, AuthCookies.OAUTH_VERIFIER);
        String[] cleared = cookies.clearedOauthStep();

        if (error != null && !error.isBlank()) {
            // « access_denied » veut dire que la personne a cliqué sur Annuler. On la ramène chez
            // elle, sans jeton. Le paramètre est repris tel quel de Discord, donc jamais réinjecté
            // dans l'URL de retour : il finirait dans les journaux du proxy.
            log.info("Autorisation Discord non accordée");
            return redirectHome(cleared).build();
        }

        if (expectedState.isEmpty() || state == null || !constantTimeEquals(expectedState.get(), state)) {
            // Sans cette vérification, n'importe qui peut faire aboutir un callback dans le
            // navigateur d'un tiers et l'asseoir dans une session qui n'est pas la sienne.
            log.warn("Callback OAuth refusé : state absent ou non concordant");
            return withCleared(ResponseEntity.status(HttpStatus.BAD_REQUEST), cleared)
                    .body(Map.of("error", "Requête de connexion invalide"));
        }

        if (code == null || code.isBlank()) {
            return withCleared(ResponseEntity.status(HttpStatus.BAD_REQUEST), cleared)
                    .body(Map.of("error", "Requête de connexion invalide"));
        }

        DiscordOAuthService.DiscordProfile profile;
        try {
            profile = discord.exchangeCodeForProfile(code, verifier.orElse(null));
        } catch (DiscordOAuthService.DiscordOAuthException ex) {
            return withCleared(ResponseEntity.status(HttpStatus.BAD_GATEWAY), cleared)
                    .body(Map.of("error", ex.getMessage()));
        }

        // Le cœur crée le compte au rôle VISITEUR s'il est inconnu, et rafraîchit pseudo et
        // avatar au passage. S'il est injoignable, on échoue franchement : émettre un jeton sans
        // permission produirait une session connectée refusée partout, plus dure à diagnostiquer
        // qu'un refus net.
        Optional<UserIdentityDto> identity =
                identityService.identity(profile.discordId(), profile.username(), profile.avatarUrl());
        if (identity.isEmpty()) {
            return withCleared(ResponseEntity.status(HttpStatus.BAD_GATEWAY), cleared)
                    .body(Map.of("error", "schub-core unreachable — impossible de déterminer les permissions"));
        }

        UserIdentityDto dto = identity.get();
        String token = jwtService.generateToken(
                profile.discordId(),
                dto.user() != null ? dto.user().id() : null,
                dto.user() != null ? dto.user().discordUsername() : profile.username(),
                List.of(),
                dto.permissionsOrEmpty());

        log.info("Connexion Discord réussie pour l'acteur {}", profile.discordId());
        return redirectHome(cleared)
                .header(HttpHeaders.SET_COOKIE, cookies.session(token, jwtProperties.expirationSeconds()))
                .build();
    }

    private ResponseEntity.BodyBuilder redirectHome(String[] clearedCookies) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.FOUND);
        withCleared(builder, clearedCookies);
        return builder.header(HttpHeaders.LOCATION, properties.postLoginRedirect());
    }

    private ResponseEntity.BodyBuilder withCleared(ResponseEntity.BodyBuilder builder, String[] clearedCookies) {
        for (String cookie : clearedCookies) {
            builder.header(HttpHeaders.SET_COOKIE, cookie);
        }
        return builder;
    }

    /**
     * Comparaison sans fuite de temps.
     *
     * <p>Le gain est théorique sur un {@code state} à usage unique et de courte vie, mais une
     * comparaison de secret en temps variable est le genre de détail qu'on copie ensuite ailleurs
     * sans y repenser.</p>
     */
    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}

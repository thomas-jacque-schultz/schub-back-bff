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
import schultz.thomas.schub.bff.config.security.FrontRegistry;
import schultz.thomas.schub.bff.config.security.FrontsProperties.Front;
import schultz.thomas.schub.bff.config.security.JwtProperties;
import schultz.thomas.schub.bff.config.security.JwtService;
import schultz.thomas.schub.bff.data.client.UserIdentityDto;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/auth/discord")
public class DiscordAuthController {

    private static final Logger log = LoggerFactory.getLogger(DiscordAuthController.class);

    // Le state porte la clé du front d'origine : le callback y ramène, sur ce même domaine.
    private static final char STATE_SEPARATOR = '.';

    private final DiscordOAuthService discord;
    private final DiscordOAuthProperties properties;
    private final IdentityService identityService;
    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthCookies cookies;
    private final FrontRegistry fronts;

    public DiscordAuthController(DiscordOAuthService discord,
                                 DiscordOAuthProperties properties,
                                 IdentityService identityService,
                                 JwtService jwtService,
                                 JwtProperties jwtProperties,
                                 AuthCookies cookies,
                                 FrontRegistry fronts) {
        this.discord = discord;
        this.properties = properties;
        this.identityService = identityService;
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.cookies = cookies;
        this.fronts = fronts;
    }

    @GetMapping
    public ResponseEntity<?> start(HttpServletRequest request) {
        if (!properties.configured()) {
            log.error("Connexion Discord non configurée — DISCORD_OAUTH_CLIENT_ID, "
                    + "DISCORD_OAUTH_CLIENT_SECRET et DISCORD_OAUTH_REDIRECT_URI sont requis");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "La connexion Discord n'est pas configurée sur ce déploiement"));
        }

        Optional<Front> front = fronts.of(request);
        if (front.isEmpty()) {
            log.warn("Connexion Discord demandée depuis un hôte hors de la liste des fronts");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("error", "Ce site n'est pas autorisé à ouvrir une session"));
        }

        String state = discord.randomUrlSafeValue() + STATE_SEPARATOR + front.get().key();
        String verifier = discord.randomUrlSafeValue();

        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.SET_COOKIE, cookies.oauthState(state))
                .header(HttpHeaders.SET_COOKIE, cookies.oauthVerifier(verifier))
                .location(URI.create(discord.authorizationUrl(state, verifier, front.get().redirectUri())))
                .build();
    }

    @GetMapping("/callback")
    public ResponseEntity<?> callback(HttpServletRequest request,
                                      @RequestParam(required = false) String code,
                                      @RequestParam(required = false) String state,
                                      @RequestParam(required = false) String error) {
        Optional<String> expectedState = cookies.read(request, AuthCookies.OAUTH_STATE);
        Optional<String> verifier = cookies.read(request, AuthCookies.OAUTH_VERIFIER);
        String[] cleared = cookies.clearedOauthStep();

        if (error != null && !error.isBlank()) {
            log.info("Autorisation Discord non accordée");
            return redirectTo(landing(expectedState.flatMap(this::frontOf).or(() -> fronts.of(request))), cleared)
                    .build();
        }

        if (expectedState.isEmpty() || state == null || !constantTimeEquals(expectedState.get(), state)) {
            log.warn("Callback OAuth refusé : state absent ou non concordant");
            return withCleared(ResponseEntity.status(HttpStatus.BAD_REQUEST), cleared)
                    .body(Map.of("error", "Requête de connexion invalide"));
        }

        Optional<Front> front = frontOf(state);
        if (front.isEmpty() || code == null || code.isBlank()) {
            return withCleared(ResponseEntity.status(HttpStatus.BAD_REQUEST), cleared)
                    .body(Map.of("error", "Requête de connexion invalide"));
        }

        DiscordOAuthService.DiscordProfile profile;
        try {
            profile = discord.exchangeCodeForProfile(code, verifier.orElse(null), front.get().redirectUri());
        } catch (DiscordOAuthService.DiscordOAuthException ex) {
            return withCleared(ResponseEntity.status(HttpStatus.BAD_GATEWAY), cleared)
                    .body(Map.of("error", ex.getMessage()));
        }

        Optional<UserIdentityDto> identity =
                identityService.discordLogin(profile.discordId(), profile.username(), profile.avatarUrl())
                        .filter(found -> found.user() != null && found.user().id() != null);
        if (identity.isEmpty()) {
            return withCleared(ResponseEntity.status(HttpStatus.BAD_GATEWAY), cleared)
                    .body(Map.of("error", "schub-core unreachable — impossible de déterminer les permissions"));
        }

        UserIdentityDto dto = identity.get();
        String token = jwtService.generateToken(
                dto.user().id(),
                dto.user().discordUsername() != null ? dto.user().discordUsername() : profile.username(),
                List.of(),
                dto.permissionsOrEmpty());

        log.info("Connexion Discord réussie pour l'utilisateur {} sur le front {}", dto.user().id(), front.get().key());
        return redirectTo(front.get().postLoginRedirect(), cleared)
                .header(HttpHeaders.SET_COOKIE, cookies.session(token, jwtProperties.expirationSeconds()))
                .build();
    }

    private Optional<Front> frontOf(String state) {
        int separator = state.lastIndexOf(STATE_SEPARATOR);
        return separator < 0 ? Optional.empty() : fronts.byKey(state.substring(separator + 1));
    }

    private static String landing(Optional<Front> front) {
        return front.map(Front::postLoginRedirect).orElse("/");
    }

    private ResponseEntity.BodyBuilder redirectTo(String location, String[] clearedCookies) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(HttpStatus.FOUND);
        withCleared(builder, clearedCookies);
        return builder.header(HttpHeaders.LOCATION, location);
    }

    private ResponseEntity.BodyBuilder withCleared(ResponseEntity.BodyBuilder builder, String[] clearedCookies) {
        for (String cookie : clearedCookies) {
            builder.header(HttpHeaders.SET_COOKIE, cookie);
        }
        return builder;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}

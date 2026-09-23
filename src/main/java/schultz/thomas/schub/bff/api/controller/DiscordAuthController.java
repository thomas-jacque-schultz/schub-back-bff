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

    @GetMapping
    public ResponseEntity<?> start() {
        if (!properties.configured()) {
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
            return redirectHome(cleared).build();
        }

        if (expectedState.isEmpty() || state == null || !constantTimeEquals(expectedState.get(), state)) {
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

    private boolean constantTimeEquals(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }
}

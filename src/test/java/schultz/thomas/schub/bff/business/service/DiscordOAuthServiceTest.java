package schultz.thomas.schub.bff.business.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;
import schultz.thomas.schub.bff.config.security.DiscordOAuthProperties;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DiscordOAuthServiceTest {

    private static final String SECRET_CLIENT = "un-secret-qui-ne-doit-jamais-fuiter";

    private final DiscordOAuthProperties properties = new DiscordOAuthProperties(
            "449160985417089024",
            SECRET_CLIENT,
            "http://localhost:18090/api/auth/discord/callback",
            "https://discord.com/oauth2/authorize",
            "https://discord.com/api/oauth2/token",
            "https://discord.com/api/users/@me",
            "/",
            true);

    private final DiscordOAuthService service =
            new DiscordOAuthService(properties, mock(RestTemplate.class));

    @Test
    @DisplayName("l'URL d'autorisation ne demande que `identify`")
    void scopeIdentifySeul() {
        String url = URLDecoder.decode(
                service.authorizationUrl("un-state", "un-verifieur"), StandardCharsets.UTF_8);

        assertThat(url).contains("scope=identify");
        assertThat(url).doesNotContain("email").doesNotContain("guilds");
        assertThat(url).contains("response_type=code").contains("state=un-state");
    }

    @Test
    @DisplayName("PKCE : le challenge S256 part avec la demande, jamais le vérifieur")
    void pkceS256() {
        String verifier = service.randomUrlSafeValue();

        String url = URLDecoder.decode(service.authorizationUrl("un-state", verifier), StandardCharsets.UTF_8);

        assertThat(url).contains("code_challenge_method=S256");
        assertThat(url).contains("code_challenge=" + service.codeChallenge(verifier));
        assertThat(url).doesNotContain(verifier);
    }

    @Test
    @DisplayName("le challenge S256 est déterministe et ne ressemble pas au vérifieur")
    void challengeDeterministe() {
        String verifier = "un-verifieur-de-test";

        assertThat(service.codeChallenge(verifier)).isEqualTo(service.codeChallenge(verifier));
        assertThat(service.codeChallenge(verifier)).isNotEqualTo(verifier);
    }

    @Test
    @DisplayName("deux valeurs aléatoires successives diffèrent, et sont sûres pour une URL")
    void valeursAleatoires() {
        String premiere = service.randomUrlSafeValue();
        String seconde = service.randomUrlSafeValue();

        assertThat(premiere).isNotEqualTo(seconde);
        assertThat(premiere).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    @DisplayName("le secret client n'apparaît ni dans l'URL d'autorisation, ni dans un toString")
    void leSecretNeSortPas() {
        assertThat(properties.toString()).doesNotContain(SECRET_CLIENT).contains("masqué");
        assertThat(service.authorizationUrl("s", "v")).doesNotContain(SECRET_CLIENT);
    }

    @Test
    @DisplayName("sans client id ni secret, la configuration se déclare incomplète")
    void configurationIncomplete() {
        DiscordOAuthProperties vide = new DiscordOAuthProperties(
                "", "", "http://x/cb", "https://a", "https://t", "https://u", "/", true);

        assertThat(vide.configured()).isFalse();
        assertThat(properties.configured()).isTrue();
    }
}

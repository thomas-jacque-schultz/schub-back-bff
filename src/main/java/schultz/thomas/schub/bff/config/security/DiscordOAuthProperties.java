package schultz.thomas.schub.bff.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

// Le scope n'est pas configurable : figé à identify dans DiscordOAuthService.
@ConfigurationProperties(prefix = "discord.oauth")
public record DiscordOAuthProperties(
        String clientId,
        String clientSecret,
        // Identique au caractère près à celle déclarée dans le portail développeur Discord.
        String redirectUri,
        String authorizationUri,
        String tokenUri,
        String userInfoUri,
        String postLoginRedirect,
        boolean pkceEnabled
) {

    public boolean configured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }

    // Le toString() par défaut d'un record imprimerait le secret.
    @Override
    public String toString() {
        return "DiscordOAuthProperties[clientId=" + clientId + ", redirectUri=" + redirectUri
                + ", pkceEnabled=" + pkceEnabled + ", clientSecret=(masqué)]";
    }
}

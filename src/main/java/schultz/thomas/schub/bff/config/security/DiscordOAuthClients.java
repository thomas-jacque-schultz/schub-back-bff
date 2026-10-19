package schultz.thomas.schub.bff.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

// Un client OAuth par front, à la clé du front (Schub#101) : premadelab.eu présente l'application Discord PremadeLab.
// Un front sans client configuré garde celui de discord.oauth.
@ConfigurationProperties(prefix = "discord.oauth")
public record DiscordOAuthClients(Map<String, Client> clients) {

    public DiscordOAuthClients {
        clients = clients == null ? Map.of() : Map.copyOf(clients);
    }

    // userInstall : la connexion installe aussi l’application sur le compte, sans quoi son bot ne peut pas écrire en MP (Schub#100).
    public record Client(String clientId, String clientSecret, boolean userInstall) {

        public boolean configured() {
            return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }

        @Override
        public String toString() {
            return "Client[clientId=" + clientId + ", clientSecret=(masqué)]";
        }
    }
}

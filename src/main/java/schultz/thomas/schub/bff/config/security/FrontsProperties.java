package schultz.thomas.schub.bff.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

// Les fronts servis par ce BFF. Liste vide : un seul front, décrit par discord.oauth.redirect-uri.
@ConfigurationProperties(prefix = "auth")
public record FrontsProperties(List<Front> fronts) {

    public FrontsProperties {
        fronts = fronts == null ? List.of() : List.copyOf(fronts);
    }

    // redirectUri : identique au caractère près à celle déclarée dans le portail développeur Discord.
    public record Front(String key, String origin, String redirectUri, String postLoginRedirect) {
    }
}

package schultz.thomas.schub.bff.config.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import schultz.thomas.schub.bff.config.security.FrontsProperties.Front;

import java.net.URI;
import java.util.List;
import java.util.Optional;

// Un front se reconnaît à l'hôte de la requête, contre cette liste seulement : aucune URL n'est construite depuis Host.
@Component
public class FrontRegistry {

    static final String DEFAULT_KEY = "default";
    private static final String FORWARDED_HOST = "X-Forwarded-Host";

    private final List<Front> fronts;

    public FrontRegistry(FrontsProperties properties, DiscordOAuthProperties discord) {
        this.fronts = properties.fronts().isEmpty() ? single(discord) : properties.fronts();
    }

    private static List<Front> single(DiscordOAuthProperties discord) {
        if (discord.redirectUri() == null || discord.redirectUri().isBlank()) {
            return List.of();
        }
        URI callback = URI.create(discord.redirectUri());
        String origin = callback.getScheme() + "://" + callback.getRawAuthority();
        String landing = discord.postLoginRedirect() == null ? "/" : discord.postLoginRedirect();
        return List.of(new Front(DEFAULT_KEY, origin, discord.redirectUri(), landing));
    }

    public List<Front> all() {
        return fronts;
    }

    public Optional<Front> byKey(String key) {
        return fronts.stream().filter(front -> front.key().equals(key)).findFirst();
    }

    public Optional<Front> of(HttpServletRequest request) {
        String host = firstValue(request.getHeader(FORWARDED_HOST));
        if (host == null) {
            host = request.getHeader("Host");
        }
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        String wanted = host.trim();
        return fronts.stream()
                .filter(front -> wanted.equalsIgnoreCase(URI.create(front.origin()).getRawAuthority()))
                .findFirst();
    }

    private static String firstValue(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        return header.split(",")[0].trim();
    }
}

package schultz.thomas.schub.bff.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

/**
 * Authentification des appels internes. Tous les services Schub exigent ce secret ; aucun appel
 * entre services n'aboutit sans lui (plan §5).
 *
 * <p>Partagée par les clients du cœur et du connecteur Discord : le secret est le même pour
 * toute la maille interne, c'est un seul périmètre de confiance.</p>
 */
@Configuration
public class InternalSecretFeignConfig {

    @Value("${schub.internal-secret}")
    private String internalSecret;

    @Bean
    public RequestInterceptor internalSecretInterceptor() {
        return template -> {
            template.header("X-Internal-Secret", internalSecret);
            template.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        };
    }
}

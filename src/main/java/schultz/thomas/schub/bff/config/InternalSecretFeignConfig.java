package schultz.thomas.schub.bff.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Authentification des appels internes, et transmission de l'acteur.
 *
 * <p>Tous les services Schub exigent {@code X-Internal-Secret} ; aucun appel entre services
 * n'aboutit sans lui (plan §5). Partagée par les clients du cœur et du connecteur Discord : le
 * secret est le même pour toute la maille interne, c'est un seul périmètre de confiance.</p>
 *
 * <p>Depuis le 18-09 s'y ajoute {@code X-Actor-Id} : l'identifiant Discord de la personne au nom
 * de qui l'appel est fait. C'est ce qui permet au cœur d'appliquer le contrôle fin — lui seul
 * connaît les appartenances. <strong>Et c'est précisément le secret interne qui rend cette
 * assertion croyable</strong> : sans lui, n'importe qui affirmerait n'importe quelle identité
 * (plan §A.2).</p>
 *
 * <p>Absent sur les routes publiques, où il n'y a personne derrière la requête — le cœur traite
 * alors l'appel comme venant d'un service, et ne sert que ce qui n'est pas de l'infrastructure.</p>
 */
@Configuration
public class InternalSecretFeignConfig {

    public static final String ACTOR_ID_HEADER = "X-Actor-Id";

    @Value("${schub.internal-secret}")
    private String internalSecret;

    @Bean
    public RequestInterceptor internalSecretInterceptor() {
        return template -> {
            template.header("X-Internal-Secret", internalSecret);
            template.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);

            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null && authentication.isAuthenticated() && authentication.getName() != null) {
                template.header(ACTOR_ID_HEADER, authentication.getName());
            }
        };
    }
}

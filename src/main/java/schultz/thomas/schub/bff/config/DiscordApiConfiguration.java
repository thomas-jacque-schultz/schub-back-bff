package schultz.thomas.schub.bff.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Le client HTTP vers l'API Discord publique.
 *
 * <p><strong>Pourquoi pas Feign</strong> : tous les clients Feign du BFF portent
 * {@code InternalSecretFeignConfig}, qui joint le secret partagé des services Schub à chaque
 * appel. Discord n'est pas un service Schub. Un client Feign ici enverrait le secret interne à
 * un tiers — c'est le genre de fuite qu'on ne remarque jamais, parce que tout fonctionne.</p>
 *
 * <p>Les délais sont courts et explicites : le callback OAuth se déroule pendant qu'un navigateur
 * attend une redirection. Sans délai, une lenteur de Discord se transforme en page blanche.</p>
 */
@Configuration
public class DiscordApiConfiguration {

    @Bean
    public RestTemplate discordRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}

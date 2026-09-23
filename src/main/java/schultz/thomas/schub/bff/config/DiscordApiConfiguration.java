package schultz.thomas.schub.bff.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

// Pas Feign : les clients Feign du BFF joignent X-Internal-Secret, qui partirait chez Discord.
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

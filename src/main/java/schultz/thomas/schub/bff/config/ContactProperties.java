package schultz.thomas.schub.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "contact")
public record ContactProperties(
        String recipientId,
        int maxMessageLength,
        int perIpPerHour,
        int globalPerHour,
        Turnstile turnstile
) {

    public record Turnstile(String secret, String verifyUrl) {

        public boolean enabled() {
            return secret != null && !secret.isBlank();
        }
    }

    public boolean hasRecipient() {
        return recipientId != null && !recipientId.isBlank();
    }
}

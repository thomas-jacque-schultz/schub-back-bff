package schultz.thomas.schub.bff.business.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.bff.api.dto.ContactRequest;
import schultz.thomas.schub.bff.api.dto.DirectMessageAck;
import schultz.thomas.schub.bff.api.dto.DirectMessageRequest;
import schultz.thomas.schub.bff.config.ContactProperties;
import schultz.thomas.schub.bff.data.client.ConnectorDiscordFeignClient;

import java.util.regex.Pattern;

@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private static final int MIN_MESSAGE_LENGTH = 20;
    private static final int MAX_NAME_LENGTH = 120;

    private final ContactProperties properties;
    private final ContactRateLimiter rateLimiter;
    private final TurnstileVerifier turnstileVerifier;
    private final ConnectorDiscordFeignClient connectorDiscord;

    public ContactService(ContactProperties properties,
                          ContactRateLimiter rateLimiter,
                          TurnstileVerifier turnstileVerifier,
                          ConnectorDiscordFeignClient connectorDiscord) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.turnstileVerifier = turnstileVerifier;
        this.connectorDiscord = connectorDiscord;
    }

    public enum Outcome {
        DELIVERED,
        REJECTED,
        RATE_LIMITED,
        UNAVAILABLE
    }

    public Outcome submit(ContactRequest request, String remoteAddress) {
        if (!rateLimiter.tryAcquire(remoteAddress)) {
            return Outcome.RATE_LIMITED;
        }

        if (request == null) {
            return Outcome.REJECTED;
        }

        if (request.website() != null && !request.website().isBlank()) {
            log.info("Formulaire de contact refusé : champ leurre rempli ({})", remoteAddress);
            return Outcome.REJECTED;
        }

        if (!isWellFormed(request)) {
            return Outcome.REJECTED;
        }

        if (!turnstileVerifier.verify(request.turnstileToken(), remoteAddress)) {
            return Outcome.REJECTED;
        }

        if (!properties.hasRecipient()) {
            log.error("Formulaire de contact inutilisable : aucun destinataire configuré (contact.recipient-id)");
            return Outcome.UNAVAILABLE;
        }

        return deliver(request);
    }

    private boolean isWellFormed(ContactRequest request) {
        String name = trimmed(request.name());
        String email = trimmed(request.email());
        String message = trimmed(request.message());

        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH) {
            return false;
        }
        if (!EMAIL.matcher(email).matches()) {
            return false;
        }
        return message.length() >= MIN_MESSAGE_LENGTH && message.length() <= properties.maxMessageLength();
    }

    private Outcome deliver(ContactRequest request) {
        String name = trimmed(request.name());
        String email = trimmed(request.email());

        DirectMessageRequest payload = new DirectMessageRequest(
                properties.recipientId(),
                "Message depuis schultz-thomas.fr",
                """
                        **De** : %s
                        **Adresse** : %s

                        %s""".formatted(name, email, trimmed(request.message())),
                "Formulaire de contact du portfolio"
        );

        try {
            DirectMessageAck ack = connectorDiscord.sendDirectMessage(payload);
            if (ack == null || !ack.delivered()) {
                log.error("Message de contact perdu : le connecteur n'a pu le remettre par aucun chemin");
                return Outcome.UNAVAILABLE;
            }

            if (!"DIRECT_MESSAGE".equals(ack.via())) {
                log.warn("Message de contact remis par repli ({}) : les MP du destinataire sont probablement fermés",
                        ack.via());
            }

            return Outcome.DELIVERED;
        } catch (Exception failure) {
            // Jamais le message du visiteur dans les journaux : il contient son adresse.
            log.error("Le connecteur Discord n'a pas répondu au message de contact : {}", failure.getMessage());
            return Outcome.UNAVAILABLE;
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}

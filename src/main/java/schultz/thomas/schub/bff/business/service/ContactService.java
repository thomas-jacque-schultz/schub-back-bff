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

/**
 * Le formulaire de contact, côté serveur.
 *
 * <p>C'est <strong>la seule route publique du système qui déclenche une écriture</strong>, et
 * tout ce qui suit découle de cette phrase. Sans protection, le premier robot qui la trouve
 * transforme une messagerie Discord en boîte à spam — et on ne se désabonne pas d'un bot.</p>
 *
 * <h2>Les trois couches, dans l'ordre où elles s'appliquent</h2>
 * <ol>
 *   <li><strong>La limitation de débit</strong>, d'abord, parce qu'elle est la moins chère à
 *       évaluer et la seule qui ne se contourne pas. Elle est appliquée <em>avant</em> la
 *       validation : valider d'abord reviendrait à offrir un service de vérification gratuit à
 *       qui cherche la forme acceptée.</li>
 *   <li><strong>Le champ leurre</strong>, qui attrape le trafic automatisé aveugle sans rien
 *       demander au visiteur.</li>
 *   <li><strong>Turnstile</strong>, si et seulement si un secret est configuré.</li>
 * </ol>
 *
 * <p>Les trois refusent de la même façon vue de l'extérieur — un 400 sans détail. Dire à un
 * robot <em>laquelle</em> l'a arrêté, c'est lui dire quoi corriger.</p>
 */
@Service
public class ContactService {

    private static final Logger log = LoggerFactory.getLogger(ContactService.class);

    /**
     * Une vérification de forme, pas une validation d'adresse.
     *
     * <p>Seule une réponse au message prouve qu'une adresse existe. Ce motif écarte les saisies
     * manifestement fautives ; prétendre faire mieux avec une expression régulière est une
     * illusion classique, qui finit par rejeter des adresses valides.</p>
     */
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

    /** Les issues possibles, telles que le contrôleur doit les traduire en codes HTTP. */
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

        // Le leurre. Un champ que personne ne voit et que les robots remplissent : s'il porte
        // quoi que ce soit, la requête ne vient pas d'un navigateur piloté par un humain.
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
            // Mal configuré, et il faut le dire franchement plutôt que d'annoncer au visiteur un
            // envoi qui n'a eu lieu nulle part. C'est DISCORD_ADMIN_ID qui manque.
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
                // Le message est arrivé, mais pas là où il devait. C'est le signal qu'il faut
                // rouvrir les MP du compte destinataire : sans cette ligne, personne ne le
                // saurait jamais, puisque le visiteur, lui, a bien reçu une confirmation.
                log.warn("Message de contact remis par repli ({}) : les MP du destinataire sont probablement fermés",
                        ack.via());
            }

            return Outcome.DELIVERED;
        } catch (Exception failure) {
            // On ne recopie jamais le message du visiteur ici : il contient son adresse, et les
            // journaux d'un BFF ne sont pas un endroit où stocker des données personnelles. Le
            // connecteur, lui, le journalise en dernier recours — c'est là que le filet est.
            log.error("Le connecteur Discord n'a pas répondu au message de contact : {}", failure.getMessage());
            return Outcome.UNAVAILABLE;
        }
    }

    private static String trimmed(String value) {
        return value == null ? "" : value.trim();
    }
}

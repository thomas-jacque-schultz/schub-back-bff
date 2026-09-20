package schultz.thomas.schub.bff.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Les réglages du formulaire de contact.
 *
 * @param recipientId      l'identifiant Discord qui reçoit les messages
 * @param maxMessageLength au-delà, le message est refusé plutôt que tronqué
 * @param perIpPerHour     nombre de messages acceptés par adresse IP et par heure
 * @param globalPerHour    plafond tous visiteurs confondus, le garde-fou de dernier recours
 * @param turnstile        le rempart Cloudflare, actif seulement si un secret est fourni
 */
@ConfigurationProperties(prefix = "contact")
public record ContactProperties(
        String recipientId,
        int maxMessageLength,
        int perIpPerHour,
        int globalPerHour,
        Turnstile turnstile
) {

    /**
     * Turnstile — **optionnel par construction**.
     *
     * <p>La clé n'existe pas encore. Exiger sa présence rendrait le formulaire inutilisable en
     * développement et bloquerait la livraison sur une démarche administrative. Sans secret, la
     * vérification est donc désactivée, et le front, de son côté, n'affiche pas le widget.</p>
     *
     * <p><strong>Ce que ça n'autorise pas</strong> : les deux autres couches — le champ leurre et
     * la limitation de débit — restent actives en permanence. Turnstile est un renfort, jamais la
     * seule protection.</p>
     */
    public record Turnstile(String secret, String verifyUrl) {

        public boolean enabled() {
            return secret != null && !secret.isBlank();
        }
    }

    public boolean hasRecipient() {
        return recipientId != null && !recipientId.isBlank();
    }
}

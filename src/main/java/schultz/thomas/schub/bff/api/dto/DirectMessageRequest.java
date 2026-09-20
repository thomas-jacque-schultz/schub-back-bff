package schultz.thomas.schub.bff.api.dto;

/**
 * Le contrat du connecteur Discord pour un envoi en message privé.
 *
 * <p>Redéclaré ici plutôt que partagé : deux services ne partagent pas de classes dans Schub, ils
 * partagent un contrat HTTP. Un module commun de DTO ferait recompiler le connecteur à chaque
 * évolution du BFF, ce qui est exactement ce que la découpe cherchait à éviter.</p>
 */
public record DirectMessageRequest(String recipientId, String title, String body, String footer) {
}

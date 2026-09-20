package schultz.thomas.schub.bff.api.dto;

/** Ce que le connecteur répond : remis ou non, et par quel chemin. */
public record DirectMessageAck(boolean delivered, String via) {
}

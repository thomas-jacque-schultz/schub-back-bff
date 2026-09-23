package schultz.thomas.schub.bff.api.dto;

public record DirectMessageRequest(String recipientId, String title, String body, String footer) {
}

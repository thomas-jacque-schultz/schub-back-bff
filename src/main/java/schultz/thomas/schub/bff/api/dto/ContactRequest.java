package schultz.thomas.schub.bff.api.dto;

public record ContactRequest(
        String name,
        String email,
        String message,
        String website,
        String turnstileToken
) {
}

package schultz.thomas.schub.bff.business.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import schultz.thomas.schub.bff.api.dto.ContactRequest;
import schultz.thomas.schub.bff.api.dto.DirectMessageAck;
import schultz.thomas.schub.bff.api.dto.DirectMessageRequest;
import schultz.thomas.schub.bff.config.ContactProperties;
import schultz.thomas.schub.bff.data.client.ConnectorDiscordFeignClient;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContactServiceTest {

    private static final class FakeConnector implements ConnectorDiscordFeignClient {

        private final List<DirectMessageRequest> sent = new ArrayList<>();
        private DirectMessageAck answer = new DirectMessageAck(true, "DIRECT_MESSAGE");
        private RuntimeException failure;

        @Override
        public byte[] getGuildsChannels() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void subscribeChannels(Object body) {
            throw new UnsupportedOperationException();
        }

        @Override
        public DirectMessageAck sendDirectMessage(DirectMessageRequest request) {
            if (failure != null) {
                throw failure;
            }
            sent.add(request);
            return answer;
        }
    }

    private static final String VALID_MESSAGE = "Bonjour, je vous écris au sujet de votre plateforme Schub.";

    private FakeConnector connector;
    private ContactService service;

    private static ContactProperties properties(int perIpPerHour) {
        return new ContactProperties(
                "227883780512153610",
                4000,
                perIpPerHour,
                60,
                new ContactProperties.Turnstile("", "https://example.invalid/siteverify"));
    }

    private static ContactRequest request(String website) {
        return new ContactRequest("Camille", "camille@example.org", VALID_MESSAGE, website, null);
    }

    @BeforeEach
    void setUp() {
        connector = new FakeConnector();
        ContactProperties props = properties(5);
        service = new ContactService(props, new ContactRateLimiter(props), new TurnstileVerifier(props, null), connector);
    }

    @Test
    @DisplayName("un message valide part chez le connecteur")
    void delivers() {
        assertThat(service.submit(request(""), "203.0.113.10"))
                .isEqualTo(ContactService.Outcome.DELIVERED);
        assertThat(connector.sent).hasSize(1);
        assertThat(connector.sent.get(0).recipientId()).isEqualTo("227883780512153610");
        assertThat(connector.sent.get(0).body()).contains("camille@example.org");
    }

    @Test
    @DisplayName("le champ leurre rempli refuse le message sans appeler le connecteur")
    void honeypot() {
        assertThat(service.submit(request("https://spam.example"), "203.0.113.11"))
                .isEqualTo(ContactService.Outcome.REJECTED);
        assertThat(connector.sent).isEmpty();
    }

    @Test
    @DisplayName("une adresse mal formée est refusée")
    void malformedEmail() {
        ContactRequest bad = new ContactRequest("Camille", "pas-une-adresse", VALID_MESSAGE, "", null);
        assertThat(service.submit(bad, "203.0.113.12")).isEqualTo(ContactService.Outcome.REJECTED);
        assertThat(connector.sent).isEmpty();
    }

    @Test
    @DisplayName("un message trop court est refusé")
    void tooShort() {
        ContactRequest bad = new ContactRequest("Camille", "camille@example.org", "salut", "", null);
        assertThat(service.submit(bad, "203.0.113.13")).isEqualTo(ContactService.Outcome.REJECTED);
    }

    @Test
    @DisplayName("la limitation de débit ferme la porte après le quota, et insister ne la rouvre pas")
    void rateLimited() {
        ContactProperties props = properties(2);
        ContactService limited = new ContactService(
                props, new ContactRateLimiter(props), new TurnstileVerifier(props, null), connector);

        assertThat(limited.submit(request(""), "203.0.113.20")).isEqualTo(ContactService.Outcome.DELIVERED);
        assertThat(limited.submit(request(""), "203.0.113.20")).isEqualTo(ContactService.Outcome.DELIVERED);
        assertThat(limited.submit(request(""), "203.0.113.20")).isEqualTo(ContactService.Outcome.RATE_LIMITED);
        assertThat(limited.submit(request(""), "203.0.113.20")).isEqualTo(ContactService.Outcome.RATE_LIMITED);

        assertThat(limited.submit(request(""), "203.0.113.21")).isEqualTo(ContactService.Outcome.DELIVERED);
    }

    @Test
    @DisplayName("un connecteur qui ne remet rien vaut 503, pas une fausse confirmation")
    void notDelivered() {
        connector.answer = new DirectMessageAck(false, "NONE");
        assertThat(service.submit(request(""), "203.0.113.30"))
                .isEqualTo(ContactService.Outcome.UNAVAILABLE);
    }

    @Test
    @DisplayName("un connecteur injoignable vaut 503")
    void connectorDown() {
        connector.failure = new IllegalStateException("connecteur injoignable");
        assertThat(service.submit(request(""), "203.0.113.31"))
                .isEqualTo(ContactService.Outcome.UNAVAILABLE);
    }

    @Test
    @DisplayName("sans destinataire configuré, on refuse au lieu d'annoncer un envoi imaginaire")
    void noRecipient() {
        ContactProperties props = new ContactProperties(
                "", 4000, 5, 60, new ContactProperties.Turnstile("", "https://example.invalid/siteverify"));
        ContactService orphan = new ContactService(
                props, new ContactRateLimiter(props), new TurnstileVerifier(props, null), connector);

        assertThat(orphan.submit(request(""), "203.0.113.40"))
                .isEqualTo(ContactService.Outcome.UNAVAILABLE);
        assertThat(connector.sent).isEmpty();
    }
}

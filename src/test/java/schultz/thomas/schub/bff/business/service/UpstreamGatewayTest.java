package schultz.thomas.schub.bff.business.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class UpstreamGatewayTest {

    private final UpstreamGateway gateway = new UpstreamGateway(new ObjectMapper());

    @Test
    @DisplayName("un corps en tableau passe tel quel : les créneaux d'entraînement en dépendent (Schub#116)")
    void corpsEnTableau() {
        Object corps = gateway.parseBody("""
                [{"day":"THURSDAY","date":null,"start":"21:00","end":"23:00"}]
                """.getBytes(StandardCharsets.UTF_8));

        assertThat(corps).isInstanceOf(List.class);
        assertThat((List<?>) corps).singleElement().isInstanceOf(Map.class);
    }

    @Test
    @DisplayName("un corps illisible répond 400, pas « service injoignable »")
    void corpsIllisible() {
        var reponse = gateway.call("schub-core", () -> {
            gateway.parseBody("{pas du json".getBytes(StandardCharsets.UTF_8));
            return new byte[0];
        });

        assertThat(reponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}

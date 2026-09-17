package schultz.thomas.schub.bff.business.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Traduction des pannes en amont, en un seul endroit.
 *
 * <p>Avant la phase 4, chaque méthode de passerelle répétait le même triplet try/catch/catch.
 * Il n'y a qu'une règle à appliquer, elle vit donc ici :</p>
 *
 * <ul>
 *   <li>Le service amont a répondu une erreur — on la relaie <em>telle quelle</em>, statut et
 *       corps compris. Un 404 du cœur doit arriver au front comme un 404, pas comme un 502 :
 *       sinon le front ne peut pas distinguer « ce serveur n'existe pas » de « le cœur est
 *       tombé ».</li>
 *   <li>Le service amont est injoignable — 502, et le nom du service dans le message. Avec
 *       plusieurs amonts, « upstream unreachable » ne suffit plus à savoir lequel est tombé.</li>
 * </ul>
 */
@Service
public class UpstreamGateway {

    private static final Logger log = LoggerFactory.getLogger(UpstreamGateway.class);

    private final ObjectMapper objectMapper;

    public UpstreamGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Appel qui renvoie un corps JSON. */
    public ResponseEntity<byte[]> call(String upstream, Supplier<byte[]> call) {
        return call(upstream, HttpStatus.OK, call);
    }

    public ResponseEntity<byte[]> call(String upstream, HttpStatus successStatus, Supplier<byte[]> call) {
        try {
            return ResponseEntity.status(successStatus)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(call.get());
        } catch (FeignException ex) {
            return relayError(ex);
        } catch (Exception ex) {
            return unreachable(upstream, ex);
        }
    }

    /** Appel sans corps de réponse : répond 204. */
    public ResponseEntity<byte[]> callVoid(String upstream, Runnable call) {
        try {
            call.run();
            return ResponseEntity.noContent().build();
        } catch (FeignException ex) {
            return relayError(ex);
        } catch (Exception ex) {
            return unreachable(upstream, ex);
        }
    }

    /**
     * Feign encode le corps depuis un objet ; le contrôleur, lui, reçoit des octets bruts qu'il
     * ne doit pas interpréter. On les repasse en Map pour que l'encodeur les réémette à
     * l'identique — le BFF ne connaît donc aucun champ du domaine, et une évolution du contrat
     * du cœur ne le traverse pas.
     */
    public Object parseBody(@Nullable byte[] body) {
        if (body == null || body.length == 0) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(body, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            throw new IllegalArgumentException("Corps de requête JSON invalide", ex);
        }
    }

    private ResponseEntity<byte[]> relayError(FeignException ex) {
        HttpStatus status = HttpStatus.resolve(ex.status());
        HttpStatus safeStatus = status != null ? status : HttpStatus.BAD_GATEWAY;
        byte[] responseBody = ex.responseBody().map(buffer -> {
            byte[] bytes = new byte[buffer.remaining()];
            buffer.get(bytes);
            return bytes;
        }).orElseGet(() -> "{\"error\":\"Upstream request failed\"}".getBytes(StandardCharsets.UTF_8));

        return ResponseEntity.status(safeStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .body(responseBody);
    }

    private ResponseEntity<byte[]> unreachable(String upstream, Exception ex) {
        log.error("[UpstreamGateway] {} injoignable : {}", upstream, ex.getMessage(), ex);
        String payload = "{\"error\":\"" + upstream + " unreachable\"}";
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload.getBytes(StandardCharsets.UTF_8));
    }
}

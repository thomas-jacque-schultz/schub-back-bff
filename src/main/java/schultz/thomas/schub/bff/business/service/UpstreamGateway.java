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

@Service
public class UpstreamGateway {

    private static final Logger log = LoggerFactory.getLogger(UpstreamGateway.class);

    private final ObjectMapper objectMapper;

    public UpstreamGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

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

    // Repassé en Map pour que Feign le réémette à l'identique sans que le BFF connaisse le contrat du cœur.
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

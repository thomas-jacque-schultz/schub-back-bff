package schultz.thomas.schub.bff.business.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import schultz.thomas.schub.bff.config.ContactProperties;

import java.util.Map;

@Service
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

    private final ContactProperties properties;
    private final RestTemplate restTemplate;

    public TurnstileVerifier(ContactProperties properties, RestTemplate discordRestTemplate) {
        this.properties = properties;
        // Ce client ne porte pas X-Internal-Secret, contrairement aux clients Feign.
        this.restTemplate = discordRestTemplate;
    }

    public boolean verify(String token, String remoteAddress) {
        if (!properties.turnstile().enabled()) {
            return true;
        }

        if (token == null || token.isBlank()) {
            log.info("Formulaire de contact refusé : jeton Turnstile absent alors que le rempart est actif");
            return false;
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("secret", properties.turnstile().secret());
        form.add("response", token);
        if (remoteAddress != null && !remoteAddress.isBlank()) {
            form.add("remoteip", remoteAddress);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = restTemplate.postForObject(
                    properties.turnstile().verifyUrl(),
                    new HttpEntity<>(form, headers),
                    Map.class);

            boolean success = body != null && Boolean.TRUE.equals(body.get("success"));
            if (!success) {
                log.info("Jeton Turnstile refusé : {}", body == null ? "réponse vide" : body.get("error-codes"));
            }
            return success;
        } catch (RestClientException failure) {
            // Fail-open voulu : une panne Cloudflare ne doit pas fermer le formulaire ; débit et leurre tiennent seuls.
            log.warn("Vérification Turnstile impossible ({}), le message est accepté sans elle",
                    failure.getMessage());
            return true;
        }
    }
}

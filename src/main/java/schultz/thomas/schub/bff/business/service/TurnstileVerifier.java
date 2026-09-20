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

/**
 * Le rempart Cloudflare Turnstile, **inactif tant qu'aucun secret n'est fourni**.
 *
 * <p>C'est la condition posée au §4 du plan : la clé n'existe pas encore, et le développement ne
 * doit pas l'attendre. Sans {@code TURNSTILE_SECRET}, {@link #verify} répond « accepté » sans
 * appeler personne — et le front, symétriquement, n'affiche pas le widget.</p>
 *
 * <p><strong>Ce que ça n'affaiblit pas</strong> : le champ leurre et la limitation de débit
 * restent actifs. Turnstile est un renfort ; il n'a jamais été la protection.</p>
 *
 * <p>Le secret ne sort jamais d'ici : il part dans le corps d'un {@code POST} vers Cloudflare, et
 * aucun journal de cette classe ne l'imprime.</p>
 */
@Service
public class TurnstileVerifier {

    private static final Logger log = LoggerFactory.getLogger(TurnstileVerifier.class);

    private final ContactProperties properties;
    private final RestTemplate restTemplate;

    public TurnstileVerifier(ContactProperties properties, RestTemplate discordRestTemplate) {
        this.properties = properties;
        // Le même client que pour l'API Discord : des délais courts vers un tiers, ce qui est
        // exactement ce dont on a besoin ici. Il ne porte PAS le secret interne des services
        // Schub — c'est la raison d'être de ce bean, et elle vaut pour Cloudflare comme pour
        // Discord.
        this.restTemplate = discordRestTemplate;
    }

    /**
     * @return vrai si le jeton est valide, ou si le rempart est désactivé
     */
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
            // Cloudflare injoignable : on LAISSE PASSER, et c'est un choix, pas un oubli.
            //
            // Refuser ferait dépendre le formulaire de contact de la disponibilité d'un tiers —
            // une panne chez Cloudflare fermerait la seule voie de contact du site. Les deux
            // autres couches tiennent pendant ce temps, et un incident de ce genre se lit dans
            // les journaux.
            log.warn("Vérification Turnstile impossible ({}), le message est accepté sans elle",
                    failure.getMessage());
            return true;
        }
    }
}

package schultz.thomas.schub.bff.business.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.regex.Pattern;

// Le laissez-passer d'un salon d'équipe du relais des jeux (Schub#171) : <charge>.<signature>, en base64url.
// La charge porte l'équipe et l'expiration ; la clé HMAC est dérivée du secret interne, que le relais connaît aussi.
// Format et dérivation à garder identiques à TeamPassVerifier (schub-together-games).
@Component
public class RelayPassSigner {

    static final Duration VALIDITE = Duration.ofMinutes(10);
    private static final String USAGE = "premadelab-relay-pass";
    private static final Pattern EQUIPE = Pattern.compile("[0-9a-f]{24}");

    private final byte[] cle;
    private final Clock clock;

    @Autowired
    public RelayPassSigner(@Value("${schub.internal-secret}") String internalSecret) {
        this(internalSecret, Clock.systemUTC());
    }

    RelayPassSigner(String internalSecret, Clock clock) {
        this.cle = hmac(internalSecret.getBytes(StandardCharsets.UTF_8), USAGE);
        this.clock = clock;
    }

    public Pass sign(String teamId) {
        if (!EQUIPE.matcher(teamId).matches()) {
            throw new IllegalArgumentException("Identifiant d'équipe invalide");
        }
        Instant expire = clock.instant().plus(VALIDITE);
        String charge = "{\"team\":\"" + teamId + "\",\"exp\":" + expire.getEpochSecond() + "}";
        Base64.Encoder base64 = Base64.getUrlEncoder().withoutPadding();
        String chargeEncodee = base64.encodeToString(charge.getBytes(StandardCharsets.UTF_8));
        return new Pass(chargeEncodee + "." + base64.encodeToString(hmac(cle, chargeEncodee)), expire);
    }

    private static byte[] hmac(byte[] cle, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(cle, "HmacSHA256"));
            return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException absent) {
            throw new IllegalStateException(absent);
        }
    }

    public record Pass(String pass, Instant expiresAt) {
    }
}

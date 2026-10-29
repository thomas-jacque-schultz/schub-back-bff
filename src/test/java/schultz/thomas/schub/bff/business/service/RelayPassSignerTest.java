package schultz.thomas.schub.bff.business.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RelayPassSignerTest {

    private static final String EQUIPE = "0123456789abcdef01234567";
    private static final Instant MAINTENANT = Instant.parse("2026-10-29T20:00:00Z");

    private final RelayPassSigner signer = new RelayPassSigner("secret-de-test",
            Clock.fixed(MAINTENANT, ZoneOffset.UTC));

    @Test
    @DisplayName("la charge dit l'équipe et l'expiration, la signature se recalcule avec la clé dérivée du secret")
    void formatAttenduParLeRelais() throws Exception {
        RelayPassSigner.Pass pass = signer.sign(EQUIPE);

        String[] parties = pass.pass().split("\\.");
        assertThat(new String(Base64.getUrlDecoder().decode(parties[0]), StandardCharsets.UTF_8))
                .isEqualTo("{\"team\":\"" + EQUIPE + "\",\"exp\":" + MAINTENANT.plusSeconds(600).getEpochSecond() + "}");
        byte[] cle = hmac("secret-de-test".getBytes(StandardCharsets.UTF_8), "premadelab-relay-pass");
        assertThat(parties[1]).isEqualTo(Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(cle, parties[0])));
        assertThat(pass.expiresAt()).isEqualTo(MAINTENANT.plus(RelayPassSigner.VALIDITE));
    }

    @Test
    @DisplayName("un autre secret donne une autre signature")
    void dependDuSecret() {
        RelayPassSigner autre = new RelayPassSigner("autre-secret", Clock.fixed(MAINTENANT, ZoneOffset.UTC));
        assertThat(autre.sign(EQUIPE).pass()).isNotEqualTo(signer.sign(EQUIPE).pass());
    }

    @Test
    @DisplayName("seul un identifiant d'équipe entre dans la charge")
    void refuseUnIdentifiantQuelconque() {
        assertThatThrownBy(() -> signer.sign("x\",\"team\":\"autre"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] hmac(byte[] cle, String message) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(cle, "HmacSHA256"));
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }
}

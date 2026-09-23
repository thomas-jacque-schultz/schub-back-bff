package schultz.thomas.schub.bff.config.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtServiceTest {

    private static final String SECRET = "un-secret-de-test-assez-long-pour-hmac-sha256-oui-vraiment";

    private final JwtService service = new JwtService(new JwtProperties(SECRET, 900, 450));

    @Test
    @DisplayName("le sujet est l'identifiant Discord, et les permissions voyagent dans le jeton")
    void contenuDuJeton() {
        String token = service.generateToken("227883780512153610", "66f0a1b2c3d4e5f6a7b8c9d0", "pisel",
                List.of("ROLE_ADMIN"), List.of("SERVER_VIEW", "PORT_RULE_EDIT"));

        assertThat(service.extractActorId(token)).isEqualTo("227883780512153610");
        assertThat(service.extractUserId(token)).isEqualTo("66f0a1b2c3d4e5f6a7b8c9d0");
        assertThat(service.extractUserId(token)).isNotEqualTo(service.extractActorId(token));
        assertThat(service.claims(token).get(JwtService.CLAIM_USERNAME)).isEqualTo("pisel");
        assertThat(service.extractRoles(token)).containsExactly("ROLE_ADMIN");
        assertThat(service.extractPermissions(token)).containsExactly("SERVER_VIEW", "PORT_RULE_EDIT");
        assertThat(service.isTokenValid(token)).isTrue();
    }

    @Test
    @DisplayName("un jeton frais ne se réémet pas")
    void jetonFrais() {
        assertThat(service.shouldRenew(service.generateToken("1", "user-1", "x", List.of(), List.of()))).isFalse();
    }

    @Test
    @DisplayName("un jeton qui a passé la moitié de sa vie se réémet")
    void renouvelleSelonLAgeEtNonLeResteAVivre() {
        JwtService emetteur = new JwtService(new JwtProperties(SECRET, 1000, 500));
        String token = emetteur.generateToken("1", "user-1", "x", List.of(), List.of("SERVER_VIEW"));

        assertThat(new JwtService(new JwtProperties(SECRET, 1000, 0)).shouldRenew(token)).isTrue();
        assertThat(new JwtService(new JwtProperties(SECRET, 1000, 500)).shouldRenew(token)).isFalse();
    }

    @Test
    @DisplayName("un jeton expiré est rejeté à la lecture, il ne se réémet jamais")
    void jetonExpire() {
        JwtService expire = new JwtService(new JwtProperties(SECRET, -1, 0));
        String token = expire.generateToken("1", "user-1", "x", List.of(), List.of());

        assertThatThrownBy(() -> service.shouldRenew(token)).isInstanceOf(ExpiredJwtException.class);
        assertThatThrownBy(() -> service.isTokenValid(token)).isInstanceOf(JwtException.class);
    }
}

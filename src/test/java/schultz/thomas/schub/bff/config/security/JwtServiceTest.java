package schultz.thomas.schub.bff.config.security;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Le contenu du jeton, et le seuil de réémission.
 *
 * <p>Le seuil est la partie qui se casse en silence : trop tôt, le BFF appelle le cœur à chaque
 * requête ; trop tard, la session d'un utilisateur actif expire sous ses doigts. Il n'a pas de
 * symptôme visible entre les deux, d'où ces tests.</p>
 */
class JwtServiceTest {

    private static final String SECRET = "un-secret-de-test-assez-long-pour-hmac-sha256-oui-vraiment";

    private final JwtService service = new JwtService(new JwtProperties(SECRET, 900));

    @Test
    @DisplayName("le sujet est l'identifiant Discord, et les permissions voyagent dans le jeton")
    void contenuDuJeton() {
        String token = service.generateToken("227883780512153610", "66f0a1b2c3d4e5f6a7b8c9d0", "pisel",
                List.of("ROLE_ADMIN"), List.of("SERVER_VIEW", "PORT_RULE_EDIT"));

        assertThat(service.extractActorId(token)).isEqualTo("227883780512153610");
        // Les deux identités du jeton, et c'est tout le sujet du correctif : le sujet est
        // l'identifiant Discord — ce que le BFF repasse au cœur — tandis que `userId` est l'id
        // interne, seul comparable aux `admins` d'un serveur.
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
    void jetonAMiVie() {
        // Le jeton est émis pour 100 s, puis relu par un service qui croit la durée de 1000 s :
        // il reste donc 100 s sur une vie théorique de 1000, soit bien moins de la moitié.
        // Une horloge déplacée plutôt qu'un sleep — le test reste instantané et déterministe.
        JwtService courtTerme = new JwtService(new JwtProperties(SECRET, 100));
        JwtService longTerme = new JwtService(new JwtProperties(SECRET, 1000));

        String token = courtTerme.generateToken("1", "user-1", "x", List.of(), List.of("SERVER_VIEW"));

        assertThat(longTerme.shouldRenew(token)).isTrue();
    }

    @Test
    @DisplayName("un jeton expiré est rejeté à la lecture, il ne se réémet jamais")
    void jetonExpire() {
        JwtService expire = new JwtService(new JwtProperties(SECRET, -1));
        String token = expire.generateToken("1", "user-1", "x", List.of(), List.of());

        // jjwt refuse un jeton expiré dès l'analyse plutôt que de rendre des claims périmées.
        // C'est ce qu'on veut : la réémission glissante ne peut pas ressusciter une session
        // morte, elle ne prolonge qu'un jeton encore valide. JwtAuthenticationFilter attrape
        // cette exception, vide le contexte, et la requête repart non authentifiée.
        assertThatThrownBy(() -> service.shouldRenew(token)).isInstanceOf(ExpiredJwtException.class);
        assertThatThrownBy(() -> service.isTokenValid(token)).isInstanceOf(JwtException.class);
    }
}

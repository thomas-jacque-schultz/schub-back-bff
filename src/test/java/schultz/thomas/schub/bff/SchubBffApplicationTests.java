package schultz.thomas.schub.bff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import schultz.thomas.schub.bff.api.controller.DiscordAuthController;
import schultz.thomas.schub.bff.business.service.DiscordOAuthService;
import schultz.thomas.schub.bff.config.security.AuthCookies;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Le contexte démarre-t-il ?
 *
 * <p>Cette classe existait, vide : elle ne chargeait donc rien. Le lot A.3 ajoute une poignée de
 * beans — propriétés OAuth, client HTTP vers Discord, fabrique de cookies — et un câblage
 * incorrect ne se verrait qu'au démarrage du conteneur, c'est-à-dire en dev ou en prod. Un test
 * de contexte coûte quelques secondes et supprime ce trou.</p>
 *
 * <p>Note : la connexion Discord n'est pas configurée dans cet environnement (ni client id, ni
 * secret). C'est voulu — le contexte doit démarrer sans, sans quoi un déploiement incomplet
 * empêcherait le BFF de se lancer au lieu de simplement refuser la connexion Discord.</p>
 */
@SpringBootTest(properties = {
        // Ces deux-là sont ce qui empêchait le contexte de démarrer : l'InMemoryUserDetailsManager
        // du compte local n'accepte pas un nom d'utilisateur nul, et l'environnement de test n'a
        // pas de AUTH_ADMIN_*. Passées ici plutôt que dans un application.properties de test, qui
        // masquerait celui de `main` au lieu de le compléter — le classpath de test l'emporte en
        // entier, et on perdrait au passage les URL des amonts.
        "auth.admin.username=test-admin",
        "auth.admin.password=test-password",
        "auth.jwt.secret=un-secret-de-test-assez-long-pour-hmac-sha256-oui-vraiment"
})
class SchubBffApplicationTests {

    @Autowired
    private DiscordAuthController discordAuthController;

    @Autowired
    private DiscordOAuthService discordOAuthService;

    @Autowired
    private AuthCookies authCookies;

    @Test
    @DisplayName("le contexte démarre, connexion Discord non configurée comprise")
    void contexteCharge() {
        assertThat(discordAuthController).isNotNull();
        assertThat(discordOAuthService).isNotNull();
        assertThat(authCookies).isNotNull();
    }
}

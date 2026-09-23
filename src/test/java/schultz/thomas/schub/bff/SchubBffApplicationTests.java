package schultz.thomas.schub.bff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import schultz.thomas.schub.bff.api.controller.DiscordAuthController;
import schultz.thomas.schub.bff.business.service.DiscordOAuthService;
import schultz.thomas.schub.bff.config.security.AuthCookies;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
        // En ligne et pas dans un application.properties de test : celui-ci masquerait celui de main en entier.
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

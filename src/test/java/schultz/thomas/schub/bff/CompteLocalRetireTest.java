package schultz.thomas.schub.bff;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "auth.jwt.secret=un-secret-de-test-assez-long-pour-hmac-sha256-oui-vraiment")
@AutoConfigureMockMvc
class CompteLocalRetireTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MockMvc mvc;

    @Test
    @DisplayName("aucun compte local : pas même celui que Spring Boot crée d'office")
    void aucunCompteLocal() {
        assertThat(context.getBeanNamesForType(UserDetailsService.class)).isEmpty();
    }

    @Test
    @DisplayName("la connexion par mot de passe n'existe plus et ne pose aucun cookie")
    void pasDeConnexionParMotDePasse() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"change-me\"}"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }
}

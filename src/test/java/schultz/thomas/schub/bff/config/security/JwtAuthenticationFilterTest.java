package schultz.thomas.schub.bff.config.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import schultz.thomas.schub.bff.business.service.IdentityService;
import schultz.thomas.schub.bff.data.client.UserIdentityDto;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Les deux transports du jeton, et la réémission glissante dans chacun.
 *
 * <p>C'est le point où la transition peut se casser en silence. Le front actuel envoie
 * {@code Authorization: Bearer} et lit {@code X-Auth-Token} ; le front migré enverra un cookie et
 * n'aura rien à lire. Si la réémission repose un cookie à qui attend un en-tête, ou l'inverse,
 * tout continue de fonctionner — jusqu'à ce que l'utilisateur soit déconnecté au bout de quinze
 * minutes d'activité ininterrompue, symptôme qu'on ne rattache pas spontanément à sa cause.</p>
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "un-secret-de-test-assez-long-pour-hmac-sha256-oui-vraiment";
    private static final String DISCORD_ID = "227883780512153610";
    private static final String USER_ID = "66f0a1b2c3d4e5f6a7b8c9d0";

    private IdentityService identityService;
    private JwtAuthenticationFilter filter;
    private JwtService lecteur;

    @BeforeEach
    void setUp() {
        identityService = mock(IdentityService.class);
        // renewAfter = 0 : tout jeton est renouvelable, ce que ces tests veulent exercer.
        lecteur = new JwtService(new JwtProperties(SECRET, 1000, 0));
        filter = new JwtAuthenticationFilter(lecteur, identityService,
                new AuthCookies(true), new JwtProperties(SECRET, 1000, 0));
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private String jetonAMiVie() {
        return new JwtService(new JwtProperties(SECRET, 1000, 0))
                .generateToken(DISCORD_ID, USER_ID, "pisel", List.of(), List.of("SERVER_VIEW"));
    }

    private String jetonFrais() {
        return lecteur.generateToken(DISCORD_ID, USER_ID, "pisel", List.of(), List.of("SERVER_VIEW"));
    }

    private void coeurRepond() {
        when(identityService.identity(anyString(), any())).thenReturn(Optional.of(new UserIdentityDto(
                new UserIdentityDto.Profile(USER_ID, DISCORD_ID, "pisel", null, "role-1", "OWNER"),
                List.of("SERVER_VIEW", "SERVER_START"))));
    }

    private MockHttpServletResponse passe(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest requeteCookie(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/game-servers");
        request.setCookies(new Cookie(AuthCookies.SESSION, token));
        return request;
    }

    private MockHttpServletRequest requeteBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/game-servers");
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    @Test
    @DisplayName("un jeton en cookie authentifie, exactement comme un Bearer")
    void cookieAuthentifie() throws Exception {
        MockHttpServletRequest request = requeteCookie(jetonFrais());

        passe(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo(DISCORD_ID);
        assertThat(request.getAttribute(JwtAuthenticationFilter.TOKEN_ATTRIBUTE)).isNotNull();
    }

    @Test
    @DisplayName("réémission en mode cookie : le jeton repart en Set-Cookie, pas en en-tête")
    void reemissionEnModeCookie() throws Exception {
        coeurRepond();

        MockHttpServletResponse response = passe(requeteCookie(jetonAMiVie()));

        assertThat(response.getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith(AuthCookies.SESSION + "=") && c.contains("HttpOnly"));
        assertThat(response.getHeader(JwtAuthenticationFilter.RENEWED_TOKEN_HEADER)).isNull();
    }

    @Test
    @DisplayName("réémission en mode Bearer : le jeton repart en X-Auth-Token, pas en cookie")
    void reemissionEnModeBearer() throws Exception {
        coeurRepond();

        MockHttpServletResponse response = passe(requeteBearer(jetonAMiVie()));

        assertThat(response.getHeader(JwtAuthenticationFilter.RENEWED_TOKEN_HEADER)).isNotNull();
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    @DisplayName("le jeton réémis conserve l'id interne et reprend les permissions du cœur")
    void reemissionRelitLesDroits() throws Exception {
        coeurRepond();

        MockHttpServletResponse response = passe(requeteBearer(jetonAMiVie()));
        String renouvele = response.getHeader(JwtAuthenticationFilter.RENEWED_TOKEN_HEADER);

        assertThat(lecteur.extractUserId(renouvele)).isEqualTo(USER_ID);
        // Le jeton présenté ne portait que SERVER_VIEW : le renouvellement relit le cœur plutôt
        // que de recopier, sans quoi un droit ajouté ou retiré ne prendrait jamais effet.
        assertThat(lecteur.extractPermissions(renouvele)).containsExactly("SERVER_VIEW", "SERVER_START");
    }

    @Test
    @DisplayName("cœur injoignable : on ne renouvelle pas, et on ne déconnecte pas non plus")
    void coeurInjoignablePendantLeRenouvellement() throws Exception {
        when(identityService.identity(anyString(), any())).thenReturn(Optional.empty());

        MockHttpServletResponse response = passe(requeteCookie(jetonAMiVie()));

        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
        assertThat(response.getHeader(JwtAuthenticationFilter.RENEWED_TOKEN_HEADER)).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("quand les deux transports sont présents, l'en-tête gagne — c'est le front non migré")
    void enTetePrioritaireSurCookie() throws Exception {
        coeurRepond();
        MockHttpServletRequest request = requeteBearer(jetonAMiVie());
        request.setCookies(new Cookie(AuthCookies.SESSION, jetonFrais()));

        MockHttpServletResponse response = passe(request);

        assertThat(response.getHeader(JwtAuthenticationFilter.RENEWED_TOKEN_HEADER)).isNotNull();
        assertThat(response.getHeaders("Set-Cookie")).isEmpty();
    }

    @Test
    @DisplayName("un cookie qui ne porte pas un jeton lisible n'authentifie personne, et ne lève rien")
    void cookieIllisible() throws Exception {
        MockHttpServletRequest request = requeteCookie("pas-du-tout-un-jwt");

        passe(request);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}

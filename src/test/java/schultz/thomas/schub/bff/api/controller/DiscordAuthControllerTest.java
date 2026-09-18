package schultz.thomas.schub.bff.api.controller;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import schultz.thomas.schub.bff.business.service.DiscordOAuthService;
import schultz.thomas.schub.bff.business.service.IdentityService;
import schultz.thomas.schub.bff.config.security.AuthCookies;
import schultz.thomas.schub.bff.config.security.DiscordOAuthProperties;
import schultz.thomas.schub.bff.config.security.JwtProperties;
import schultz.thomas.schub.bff.config.security.JwtService;
import schultz.thomas.schub.bff.data.client.UserIdentityDto;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Le flux OAuth, et surtout ses quatre façons de mal tourner sans bruit.
 *
 * <p>Un callback OAuth réussi se voit tout de suite ; ses échecs, non. Un {@code state} qui n'est
 * pas vérifié laisse passer un flux entier sans que rien ne paraisse anormal — c'est précisément
 * ce qui en fait une faille et pas un défaut de confort. De même, un cœur injoignable qui
 * aboutirait quand même à un jeton donnerait une session connectée refusée partout, symptôme bien
 * plus difficile à rattacher à sa cause qu'un refus net.</p>
 *
 * <p>Ce qui n'est <strong>pas</strong> couvert ici, et ne peut pas l'être : l'échange réel avec
 * Discord. Il demande un secret client et un navigateur, dont cet environnement ne dispose pas.
 * Tout ce qui touche au dialogue avec Discord est derrière {@link DiscordOAuthService}, qui est
 * simulé.</p>
 */
class DiscordAuthControllerTest {

    private static final String DISCORD_ID = "227883780512153610";
    private static final String USER_ID = "66f0a1b2c3d4e5f6a7b8c9d0";
    private static final String SECRET = "un-secret-de-test-assez-long-pour-hmac-sha256-oui-vraiment";
    private static final String STATE = "un-state-aleatoire";

    private DiscordOAuthService discord;
    private IdentityService identityService;
    private JwtService jwtService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        construire(proprietes(true));
    }

    private DiscordOAuthProperties proprietes(boolean configure) {
        return new DiscordOAuthProperties(
                configure ? "449160985417089024" : "",
                configure ? "un-secret-qui-ne-doit-jamais-fuiter" : "",
                "http://localhost:18090/api/auth/discord/callback",
                "https://discord.com/oauth2/authorize",
                "https://discord.com/api/oauth2/token",
                "https://discord.com/api/users/@me",
                "/",
                true);
    }

    private void construire(DiscordOAuthProperties properties) {
        discord = mock(DiscordOAuthService.class);
        identityService = mock(IdentityService.class);
        jwtService = new JwtService(new JwtProperties(SECRET, 900));
        AuthCookies cookies = new AuthCookies(true);

        when(discord.randomUrlSafeValue()).thenReturn(STATE);
        when(discord.authorizationUrl(anyString(), anyString()))
                .thenReturn("https://discord.com/oauth2/authorize?client_id=x&scope=identify&state=" + STATE);

        mvc = MockMvcBuilders.standaloneSetup(new DiscordAuthController(
                discord, properties, identityService, jwtService,
                new JwtProperties(SECRET, 900), cookies)).build();
    }

    private UserIdentityDto identite() {
        return new UserIdentityDto(
                new UserIdentityDto.Profile(USER_ID, DISCORD_ID, "pisel", null, "role-1", "OWNER"),
                List.of("SERVER_VIEW", "SERVER_START"));
    }

    // --- le départ ---

    @Test
    @DisplayName("le départ pose le state et le vérifieur PKCE avant de rediriger vers Discord")
    void departPoseLesCookies() throws Exception {
        var result = mvc.perform(get("/auth/discord"))
                .andExpect(status().isFound())
                .andReturn();

        List<String> setCookies = result.getResponse().getHeaders("Set-Cookie");
        assertThat(setCookies).anyMatch(c -> c.startsWith(AuthCookies.OAUTH_STATE + "="));
        assertThat(setCookies).anyMatch(c -> c.startsWith(AuthCookies.OAUTH_VERIFIER + "="));
        // Les deux attributs sans lesquels le cookie ne servirait à rien : illisible en
        // JavaScript, et joint au retour de Discord, qui est une navigation venue d'un autre site.
        assertThat(setCookies).allMatch(c -> c.contains("HttpOnly") && c.contains("SameSite=Lax"));
    }

    @Test
    @DisplayName("sans client id ni secret, la route répond 503 au lieu de rediriger vers rien")
    void departNonConfigure() throws Exception {
        construire(proprietes(false));

        mvc.perform(get("/auth/discord")).andExpect(status().isServiceUnavailable());
    }

    // --- le retour ---

    @Test
    @DisplayName("un callback sans state est refusé, et rien n'est demandé à Discord")
    void callbackSansState() throws Exception {
        mvc.perform(get("/auth/discord/callback").param("code", "un-code"))
                .andExpect(status().isBadRequest());

        verify(discord, never()).exchangeCodeForProfile(anyString(), any());
    }

    @Test
    @DisplayName("un callback dont le state ne correspond pas au cookie est refusé")
    void callbackStateDivergent() throws Exception {
        mvc.perform(get("/auth/discord/callback")
                        .param("code", "un-code")
                        .param("state", "state-de-l-attaquant")
                        .cookie(new Cookie(AuthCookies.OAUTH_STATE, STATE)))
                .andExpect(status().isBadRequest());

        verify(discord, never()).exchangeCodeForProfile(anyString(), any());
    }

    @Test
    @DisplayName("un callback refusé efface quand même le state : il est à usage unique")
    void callbackEffaceLeState() throws Exception {
        var result = mvc.perform(get("/auth/discord/callback")
                        .param("code", "un-code")
                        .param("state", "state-de-l-attaquant")
                        .cookie(new Cookie(AuthCookies.OAUTH_STATE, STATE)))
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .anyMatch(c -> c.startsWith(AuthCookies.OAUTH_STATE + "=;")
                        || c.startsWith(AuthCookies.OAUTH_STATE + "=; ")
                        || c.contains(AuthCookies.OAUTH_STATE + "=; Max-Age=0"));
    }

    @Test
    @DisplayName("un code refusé par Discord donne 502, pas une session")
    void callbackCodeInvalide() throws Exception {
        when(discord.exchangeCodeForProfile(anyString(), any()))
                .thenThrow(new DiscordOAuthService.DiscordOAuthException("Le code d'autorisation a été refusé"));

        var result = mvc.perform(get("/auth/discord/callback")
                        .param("code", "un-code-perime")
                        .param("state", STATE)
                        .cookie(new Cookie(AuthCookies.OAUTH_STATE, STATE)))
                .andExpect(status().isBadGateway())
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .noneMatch(c -> c.startsWith(AuthCookies.SESSION + "=ey"));
    }

    @Test
    @DisplayName("le cœur injoignable pendant le callback : 502 franc, pas un jeton sans permission")
    void callbackCoeurInjoignable() throws Exception {
        when(discord.exchangeCodeForProfile(anyString(), any()))
                .thenReturn(new DiscordOAuthService.DiscordProfile(DISCORD_ID, "pisel", null));
        when(identityService.identity(anyString(), any(), any())).thenReturn(Optional.empty());

        var result = mvc.perform(get("/auth/discord/callback")
                        .param("code", "un-code")
                        .param("state", STATE)
                        .cookie(new Cookie(AuthCookies.OAUTH_STATE, STATE)))
                .andExpect(status().isBadGateway())
                .andReturn();

        // Le point de ce test : émettre un jeton ici produirait une session qui « marche »
        // jusqu'au premier appel métier, puis refusée partout sans explication lisible.
        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .noneMatch(c -> c.startsWith(AuthCookies.SESSION + "=ey"));
    }

    @Test
    @DisplayName("un callback valide pose le jeton en cookie et renvoie le navigateur sur le site")
    void callbackNominal() throws Exception {
        when(discord.exchangeCodeForProfile(anyString(), any()))
                .thenReturn(new DiscordOAuthService.DiscordProfile(DISCORD_ID, "pisel", "https://cdn/av.png"));
        when(identityService.identity(DISCORD_ID, "pisel", "https://cdn/av.png"))
                .thenReturn(Optional.of(identite()));

        var result = mvc.perform(get("/auth/discord/callback")
                        .param("code", "un-code")
                        .param("state", STATE)
                        .cookie(new Cookie(AuthCookies.OAUTH_STATE, STATE),
                                new Cookie(AuthCookies.OAUTH_VERIFIER, "un-verifieur")))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"))
                .andReturn();

        String session = result.getResponse().getHeaders("Set-Cookie").stream()
                .filter(c -> c.startsWith(AuthCookies.SESSION + "="))
                .filter(c -> !c.contains("Max-Age=0"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("aucun cookie de session posé"));

        assertThat(session).contains("HttpOnly").contains("Secure").contains("SameSite=Lax").contains("Path=/");

        String token = session.substring((AuthCookies.SESSION + "=").length(), session.indexOf(';'));
        assertThat(jwtService.extractActorId(token)).isEqualTo(DISCORD_ID);
        // Les deux identités, encore : sans `userId`, le front ne peut pas se reconnaître dans
        // les `admins` d'un serveur.
        assertThat(jwtService.extractUserId(token)).isEqualTo(USER_ID);
        assertThat(jwtService.extractPermissions(token)).containsExactly("SERVER_VIEW", "SERVER_START");
    }

    @Test
    @DisplayName("un refus devant l'écran d'autorisation ramène au site, sans session")
    void callbackRefuseParLUtilisateur() throws Exception {
        var result = mvc.perform(get("/auth/discord/callback")
                        .param("error", "access_denied")
                        .cookie(new Cookie(AuthCookies.OAUTH_STATE, STATE)))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "/"))
                .andReturn();

        assertThat(result.getResponse().getHeaders("Set-Cookie"))
                .noneMatch(c -> c.startsWith(AuthCookies.SESSION + "=ey"));
        verify(discord, never()).exchangeCodeForProfile(anyString(), any());
    }
}

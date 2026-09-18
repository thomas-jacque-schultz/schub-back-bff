package schultz.thomas.schub.bff.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

/**
 * Les trois cookies de l'authentification, fabriqués au même endroit.
 *
 * <ul>
 *   <li>{@value #SESSION} — le jeton de session, posé par le callback et reposé à chaque
 *       réémission glissante (décisions n°3 et n°4) ;</li>
 *   <li>{@value #OAUTH_STATE} — l'anti-CSRF du flux OAuth, le temps d'un aller-retour ;</li>
 *   <li>{@value #OAUTH_VERIFIER} — le vérifieur PKCE, même durée de vie.</li>
 * </ul>
 *
 * <p><strong>Où vit le {@code state}, et pourquoi là</strong> : le BFF est sans état depuis la
 * phase 4, il n'a donc ni session serveur ni stockage où le déposer. Un cookie {@code httpOnly}
 * de dix minutes est le seul endroit qui survive à l'aller-retour vers Discord sans rien
 * persister. Il est illisible en JavaScript, il est lié au navigateur qui a commencé le flux, et
 * il disparaît de lui-même. {@code SameSite=Lax} est obligatoire ici : le retour de Discord est
 * une navigation de premier plan venue d'un autre site, et {@code Strict} ne joindrait pas le
 * cookie — le flux échouerait à tous les coups.</p>
 *
 * <p>{@code Secure} est configurable parce que le dev passe par {@code http://localhost:18090}.
 * Les navigateurs traitent {@code localhost} comme une origine sûre et acceptent {@code Secure}
 * sur {@code http}, mais la soupape existe pour ne pas découvrir le contraire un lundi matin.
 * <strong>En prod, elle reste à {@code true}.</strong></p>
 */
@Component
public class AuthCookies {

    public static final String SESSION = "schub_session";
    public static final String OAUTH_STATE = "schub_oauth_state";
    public static final String OAUTH_VERIFIER = "schub_oauth_verifier";

    /** Large de quoi couvrir une hésitation devant l'écran d'autorisation, court de quoi ne rien traîner. */
    private static final Duration OAUTH_STEP_TTL = Duration.ofMinutes(10);

    private final boolean secure;

    public AuthCookies(@Value("${auth.cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

    /** Le jeton de session. Sa durée de vie suit celle du jeton lui-même. */
    public String session(String token, long maxAgeSeconds) {
        return build(SESSION, token, maxAgeSeconds, "/");
    }

    public String clearedSession() {
        return build(SESSION, "", 0, "/");
    }

    public String oauthState(String state) {
        return build(OAUTH_STATE, state, OAUTH_STEP_TTL.getSeconds(), "/");
    }

    public String oauthVerifier(String verifier) {
        return build(OAUTH_VERIFIER, verifier, OAUTH_STEP_TTL.getSeconds(), "/");
    }

    /**
     * Efface les deux cookies du flux OAuth.
     *
     * <p>Appelé <strong>quel que soit le dénouement</strong> du callback : un {@code state}
     * abandonné derrière un échec pourrait être rejoué, et un vérifieur PKCE est à usage unique
     * par définition.</p>
     */
    public String[] clearedOauthStep() {
        return new String[]{build(OAUTH_STATE, "", 0, "/"), build(OAUTH_VERIFIER, "", 0, "/")};
    }

    public Optional<String> read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst();
    }

    private String build(String name, String value, long maxAgeSeconds, String path) {
        StringBuilder cookie = new StringBuilder()
                .append(name).append('=').append(value)
                .append("; Max-Age=").append(maxAgeSeconds)
                .append("; Path=").append(path)
                .append("; HttpOnly")
                .append("; SameSite=Lax");
        if (secure) {
            cookie.append("; Secure");
        }
        return cookie.toString();
    }
}

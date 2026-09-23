package schultz.thomas.schub.bff.config.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;

@Component
public class AuthCookies {

    public static final String SESSION = "schub_session";
    public static final String OAUTH_STATE = "schub_oauth_state";
    public static final String OAUTH_VERIFIER = "schub_oauth_verifier";

    private static final Duration OAUTH_STEP_TTL = Duration.ofMinutes(10);

    private final boolean secure;

    public AuthCookies(@Value("${auth.cookie.secure:true}") boolean secure) {
        this.secure = secure;
    }

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

package schultz.thomas.schub.bff.business.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import schultz.thomas.schub.bff.config.security.DiscordOAuthProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Service
public class DiscordOAuthService {

    public static final String SCOPE = "identify";

    private static final Logger log = LoggerFactory.getLogger(DiscordOAuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private final DiscordOAuthProperties properties;
    private final RestTemplate restTemplate;

    public DiscordOAuthService(DiscordOAuthProperties properties, RestTemplate discordRestTemplate) {
        this.properties = properties;
        this.restTemplate = discordRestTemplate;
    }

    public String randomUrlSafeValue() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    public String authorizationUrl(String state, String codeVerifier) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.authorizationUri())
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("state", state);
        if (properties.pkceEnabled()) {
            builder.queryParam("code_challenge", codeChallenge(codeVerifier))
                    .queryParam("code_challenge_method", "S256");
        }
        return builder.encode().toUriString();
    }

    public String codeChallenge(String codeVerifier) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return URL_ENCODER.encodeToString(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 indisponible", ex);
        }
    }

    public DiscordProfile exchangeCodeForProfile(String code, String codeVerifier) {
        String accessToken = exchangeCode(code, codeVerifier);
        return fetchProfile(accessToken);
    }

    private String exchangeCode(String code, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        if (properties.pkceEnabled() && codeVerifier != null) {
            form.add("code_verifier", codeVerifier);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        try {
            Map<?, ?> body = restTemplate.postForObject(
                    properties.tokenUri(), new HttpEntity<>(form, headers), Map.class);
            Object token = body == null ? null : body.get("access_token");
            if (token == null) {
                throw new DiscordOAuthException("Discord n'a pas renvoyé de jeton d'accès");
            }
            return String.valueOf(token);
        } catch (RestClientResponseException ex) {
            // Le statut seul : le corps d'une erreur OAuth peut reprendre le secret client.
            log.warn("Échange du code refusé par Discord — statut {}", ex.getStatusCode().value());
            throw new DiscordOAuthException("Le code d'autorisation a été refusé");
        } catch (RestClientException ex) {
            log.warn("Discord injoignable pendant l'échange du code : {}", ex.getClass().getSimpleName());
            throw new DiscordOAuthException("Discord est injoignable");
        }
    }

    private DiscordProfile fetchProfile(String accessToken) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);
        try {
            Map<?, ?> body = restTemplate.exchange(
                    properties.userInfoUri(), org.springframework.http.HttpMethod.GET,
                    new HttpEntity<>(headers), Map.class).getBody();
            if (body == null || body.get("id") == null) {
                throw new DiscordOAuthException("Profil Discord illisible");
            }
            String id = String.valueOf(body.get("id"));
            return new DiscordProfile(id, displayName(body), avatarUrl(id, body.get("avatar")));
        } catch (RestClientResponseException ex) {
            log.warn("Lecture du profil Discord refusée — statut {}", ex.getStatusCode().value());
            throw new DiscordOAuthException("Profil Discord illisible");
        } catch (RestClientException ex) {
            log.warn("Discord injoignable pendant la lecture du profil : {}", ex.getClass().getSimpleName());
            throw new DiscordOAuthException("Discord est injoignable");
        }
    }

    private String displayName(Map<?, ?> body) {
        Object global = body.get("global_name");
        if (global != null && !String.valueOf(global).isBlank()) {
            return String.valueOf(global);
        }
        Object username = body.get("username");
        return username == null ? null : String.valueOf(username);
    }

    private String avatarUrl(String userId, Object avatarHash) {
        if (avatarHash == null || String.valueOf(avatarHash).isBlank()) {
            return null;
        }
        return "https://cdn.discordapp.com/avatars/" + userId + "/" + avatarHash + ".png";
    }

    public record DiscordProfile(String discordId, String username, String avatarUrl) {
    }

    public static class DiscordOAuthException extends RuntimeException {
        public DiscordOAuthException(String message) {
            super(message);
        }
    }
}

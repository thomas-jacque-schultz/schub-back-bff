package schultz.thomas.schub.bff.config.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;

@Service
public class JwtService {

    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_USER_ID = "userId";
    static final String CLAIM_PERMISSIONS = "permissions";
    static final String CLAIM_USERNAME = "username";

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    public String generateToken(String actorId, String userId, String username,
                                Collection<String> roles, Collection<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(actorId)
                .claim(CLAIM_USER_ID, userId)
                .claim(CLAIM_USERNAME, username)
                .claim(CLAIM_ROLES, List.copyOf(roles))
                .claim(CLAIM_PERMISSIONS, List.copyOf(permissions))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(jwtProperties.expirationSeconds())))
                .signWith(getSigningKey())
                .compact();
    }

    public Claims claims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String extractActorId(String token) {
        return claims(token).getSubject();
    }

    public String extractUserId(String token) {
        Object value = claims(token).get(CLAIM_USER_ID);
        return value == null ? null : String.valueOf(value);
    }

    public boolean isTokenValid(String token) {
        return claims(token).getExpiration().after(new Date());
    }

    public List<String> extractRoles(String token) {
        return stringList(claims(token).get(CLAIM_ROLES));
    }

    public List<String> extractPermissions(String token) {
        return stringList(claims(token).get(CLAIM_PERMISSIONS));
    }

    public boolean shouldRenew(String token) {
        Claims claims = claims(token);
        if (claims.getExpiration().getTime() <= System.currentTimeMillis()) {
            return false;
        }
        long age = System.currentTimeMillis() - claims.getIssuedAt().getTime();
        return age >= jwtProperties.renewAfterSeconds() * 1000L;
    }

    private List<String> stringList(Object claim) {
        if (claim instanceof List<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .toList();
        }
        return Collections.emptyList();
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtProperties.secret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}

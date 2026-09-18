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

/**
 * Émission et lecture du jeton de session.
 *
 * <p>Deux changements du 18-09, qui se tiennent l'un l'autre :</p>
 * <ul>
 *   <li><strong>le sujet est l'identifiant Discord</strong>, pas un nom d'utilisateur. C'est lui
 *       que le BFF repasse au cœur dans {@code X-Actor-Id}, donc c'est lui qui doit être
 *       l'identité portée par le jeton. Le pseudo n'est que de l'affichage et voyage à côté ;</li>
 *   <li><strong>le jeton porte les permissions</strong> et plus seulement des rôles. C'est ce qui
 *       permet au BFF de refuser une route sans interroger le cœur à chaque requête.</li>
 * </ul>
 *
 * <p>Durée : 15 minutes (décision n°3). Retirer un droit prend donc effet en un quart d'heure au
 * pire — la contrepartie assumée d'un jeton qui porte ses permissions.</p>
 */
@Service
public class JwtService {

    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_PERMISSIONS = "permissions";
    static final String CLAIM_USERNAME = "username";

    private final JwtProperties jwtProperties;

    public JwtService(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
    }

    /**
     * @param actorId     identifiant Discord — le sujet du jeton, et l'acteur transmis au cœur
     * @param username    pseudo, pour l'affichage seul
     * @param roles       conservés pour ce qui raisonne encore en rôles ; les décisions
     *                    d'autorisation, elles, se prennent sur les permissions
     * @param permissions ce que ce compte a le droit de faire, au moment de l'émission
     */
    public String generateToken(String actorId, String username,
                                Collection<String> roles, Collection<String> permissions) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(actorId)
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

    public boolean isTokenValid(String token) {
        return claims(token).getExpiration().after(new Date());
    }

    public List<String> extractRoles(String token) {
        return stringList(claims(token).get(CLAIM_ROLES));
    }

    public List<String> extractPermissions(String token) {
        return stringList(claims(token).get(CLAIM_PERMISSIONS));
    }

    /**
     * Le jeton a-t-il passé la moitié de sa vie ?
     *
     * <p>C'est le déclencheur de la réémission glissante : l'utilisateur actif ne se reconnecte
     * jamais, et ses droits se rafraîchissent au plus tard toutes les 7 min 30. Pas de refresh
     * token — il faudrait le stocker, le faire tourner et le révoquer, donc redonner un état au
     * BFF, qui est sans état depuis la phase 4 (décision n°3).</p>
     *
     * <p>Assumé : un utilisateur inactif est déconnecté au bout de 15 minutes. Si c'est pénible,
     * la réponse est d'allonger la durée, pas d'ajouter un refresh token.</p>
     */
    public boolean shouldRenew(String token) {
        Date expiration = claims(token).getExpiration();
        long remaining = expiration.getTime() - System.currentTimeMillis();
        return remaining > 0 && remaining < (jwtProperties.expirationSeconds() * 1000L) / 2;
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

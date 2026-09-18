package schultz.thomas.schub.bff.config.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import schultz.thomas.schub.bff.business.service.IdentityService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Authentifie la requête depuis le jeton, et le renouvelle quand il a passé la moitié de sa vie.
 *
 * <p><strong>La réémission relit les permissions dans le cœur</strong>, elle ne recopie pas
 * celles du jeton en cours. C'est tout l'intérêt : renouveler à l'identique rendrait la session
 * d'un utilisateur actif éternelle <em>et</em> ses droits figés, ce qui viderait de son sens la
 * durée de 15 minutes — un droit retiré ne prendrait jamais effet (décision n°3).</p>
 *
 * <p>Le jeton renouvelé repart dans l'en-tête de réponse {@code X-Auth-Token}. C'est provisoire :
 * au lot A.3 il deviendra un {@code Set-Cookie} {@code httpOnly} et le front n'aura plus rien à
 * orchestrer. Tant que le jeton voyage en {@code Authorization: Bearer}, il faut bien le rendre
 * à quelqu'un.</p>
 *
 * <p>Si le cœur est injoignable au moment du renouvellement, on ne renouvelle pas et la requête
 * suit son cours : déconnecter quelqu'un parce qu'un service amont hoquette serait une punition
 * sans rapport avec la faute.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Provisoire, jusqu'au cookie httpOnly du lot A.3. Exposé au navigateur par la config CORS. */
    public static final String RENEWED_TOKEN_HEADER = "X-Auth-Token";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final IdentityService identityService;

    public JwtAuthenticationFilter(JwtService jwtService, IdentityService identityService) {
        this.jwtService = jwtService;
        this.identityService = identityService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            String actorId = jwtService.extractActorId(token);
            if (actorId != null && jwtService.isTokenValid(token)) {
                authenticate(actorId, token);
                renewIfNeeded(actorId, token, response);
            }
        } catch (JwtException ignored) {
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Le principal est l'identifiant Discord : c'est lui que l'intercepteur Feign repasse au cœur
     * dans {@code X-Actor-Id}. Les autorités sont les permissions <em>et</em> les rôles, pour que
     * {@code hasAuthority('PORT_RULE_EDIT')} et {@code hasRole('ADMIN')} fonctionnent tous deux.
     */
    private void authenticate(String actorId, String token) {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        jwtService.extractPermissions(token).forEach(p -> authorities.add(new SimpleGrantedAuthority(p)));
        jwtService.extractRoles(token).forEach(r -> authorities.add(new SimpleGrantedAuthority(r)));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId, null, authorities));
    }

    private void renewIfNeeded(String actorId, String token, HttpServletResponse response) {
        if (!jwtService.shouldRenew(token)) {
            return;
        }
        identityService.identity(actorId, null).ifPresentOrElse(identity -> {
            String username = identity.user() != null ? identity.user().discordUsername() : null;
            response.setHeader(RENEWED_TOKEN_HEADER, jwtService.generateToken(
                    actorId, username, jwtService.extractRoles(token), identity.permissionsOrEmpty()));
            log.debug("Jeton renouvelé pour l'acteur {}", actorId);
        }, () -> log.warn("Renouvellement impossible pour l'acteur {} — le jeton en cours reste valide", actorId));
    }
}

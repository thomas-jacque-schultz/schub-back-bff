package schultz.thomas.schub.bff.config.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import schultz.thomas.schub.bff.business.service.IdentityService;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Authentifie la requête depuis le jeton, et le renouvelle quand il a passé la moitié de sa vie.
 *
 * <p><strong>Deux transports, le temps d'une transition.</strong> Le jeton vit désormais dans un
 * cookie {@code httpOnly} (décision n°4), mais le front actuel envoie encore
 * {@code Authorization: Bearer}. Les deux sont acceptés, et l'en-tête l'emporte quand les deux
 * sont là — c'est le front non migré qui parle, il doit continuer de fonctionner tel quel. Le
 * retrait du {@code Bearer} est une étape ultérieure, après la migration de {@code httpClient.ts}
 * et des six {@code *Api.ts}.</p>
 *
 * <p><strong>La réémission relit les permissions dans le cœur</strong>, elle ne recopie pas
 * celles du jeton en cours. C'est tout l'intérêt : renouveler à l'identique rendrait la session
 * d'un utilisateur actif éternelle <em>et</em> ses droits figés, ce qui viderait de son sens la
 * durée de 15 minutes — un droit retiré ne prendrait jamais effet (décision n°3).</p>
 *
 * <p><strong>Le jeton renouvelé repart par le canal dont il est venu</strong> : {@code Set-Cookie}
 * pour un jeton arrivé en cookie, en-tête {@code X-Auth-Token} pour un jeton arrivé en
 * {@code Bearer}. Reposer un cookie à un front qui lit un en-tête, ou l'inverse, donnerait une
 * session qui expire malgré une réémission qui a bien eu lieu — une panne sans symptôme jusqu'à
 * la déconnexion.</p>
 *
 * <p>Si le cœur est injoignable au moment du renouvellement, on ne renouvelle pas et la requête
 * suit son cours : déconnecter quelqu'un parce qu'un service amont hoquette serait une punition
 * sans rapport avec la faute.</p>
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** Le canal de réémission du front non migré. Disparaîtra avec le {@code Bearer}. */
    public static final String RENEWED_TOKEN_HEADER = "X-Auth-Token";

    /**
     * Le jeton validé, déposé sur la requête pour {@code /auth/me}.
     *
     * <p>Sans cet attribut, le contrôleur devrait refaire la lecture des deux transports — et
     * c'est exactement le genre de duplication qui finit par diverger : {@code /auth/me}
     * continuerait de n'accepter que l'en-tête, donc répondrait 401 à une session en cookie
     * alors que toutes les autres routes l'acceptent.</p>
     */
    public static final String TOKEN_ATTRIBUTE = "schub.jwt";

    private static final String BEARER_PREFIX = "Bearer ";

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final IdentityService identityService;
    private final AuthCookies cookies;
    private final JwtProperties jwtProperties;

    public JwtAuthenticationFilter(JwtService jwtService,
                                   IdentityService identityService,
                                   AuthCookies cookies,
                                   JwtProperties jwtProperties) {
        this.jwtService = jwtService;
        this.identityService = identityService;
        this.cookies = cookies;
        this.jwtProperties = jwtProperties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Optional<PresentedToken> presented = readToken(request);
        if (presented.isEmpty()) {
            filterChain.doFilter(request, response);
            return;
        }

        PresentedToken token = presented.get();
        try {
            String actorId = jwtService.extractActorId(token.value());
            if (actorId != null && jwtService.isTokenValid(token.value())) {
                authenticate(actorId, token.value());
                request.setAttribute(TOKEN_ATTRIBUTE, token.value());
                renewIfNeeded(actorId, token, response);
            }
        } catch (JwtException ignored) {
            // Jeton illisible, expiré ou mal signé : la requête continue sans identité et se
            // fera refuser par la chaîne de sécurité. Pas de 401 ici — ce filtre authentifie,
            // il n'autorise pas.
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    /** L'en-tête d'abord — c'est le front non migré, et lui seul en envoie un. */
    private Optional<PresentedToken> readToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String value = authHeader.substring(BEARER_PREFIX.length());
            return value.isBlank() ? Optional.empty() : Optional.of(new PresentedToken(value, Transport.HEADER));
        }
        return cookies.read(request, AuthCookies.SESSION)
                .map(value -> new PresentedToken(value, Transport.COOKIE));
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

    private void renewIfNeeded(String actorId, PresentedToken token, HttpServletResponse response) {
        if (!jwtService.shouldRenew(token.value())) {
            return;
        }
        identityService.identity(actorId, null).ifPresentOrElse(identity -> {
            String username = identity.user() != null ? identity.user().discordUsername() : null;
            String userId = identity.user() != null ? identity.user().id() : jwtService.extractUserId(token.value());
            String renewed = jwtService.generateToken(
                    actorId, userId, username, jwtService.extractRoles(token.value()), identity.permissionsOrEmpty());
            if (token.transport() == Transport.COOKIE) {
                response.addHeader(HttpHeaders.SET_COOKIE,
                        cookies.session(renewed, jwtProperties.expirationSeconds()));
            } else {
                response.setHeader(RENEWED_TOKEN_HEADER, renewed);
            }
            log.debug("Jeton renouvelé pour l'acteur {} (transport {})", actorId, token.transport());
        }, () -> log.warn("Renouvellement impossible pour l'acteur {} — le jeton en cours reste valide", actorId));
    }

    private enum Transport { HEADER, COOKIE }

    private record PresentedToken(String value, Transport transport) {
    }
}

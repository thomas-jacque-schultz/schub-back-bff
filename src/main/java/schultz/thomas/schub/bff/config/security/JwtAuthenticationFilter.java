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

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String RENEWED_TOKEN_HEADER = "X-Auth-Token";

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
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }

    private Optional<PresentedToken> readToken(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            String value = authHeader.substring(BEARER_PREFIX.length());
            return value.isBlank() ? Optional.empty() : Optional.of(new PresentedToken(value, Transport.HEADER));
        }
        return cookies.read(request, AuthCookies.SESSION)
                .map(value -> new PresentedToken(value, Transport.COOKIE));
    }

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

package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.IdentityService;
import schultz.thomas.schub.bff.config.security.AuthCookies;
import schultz.thomas.schub.bff.config.security.JwtAuthenticationFilter;
import schultz.thomas.schub.bff.config.security.JwtProperties;
import schultz.thomas.schub.bff.config.security.JwtService;
import schultz.thomas.schub.bff.data.client.UserIdentityDto;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;
    private final IdentityService identityService;
    private final AuthCookies cookies;

    @Value("${discord.admin.id:}")
    private String adminDiscordId;

    public AuthController(JwtService jwtService,
                          JwtProperties jwtProperties,
                          AuthenticationManager authenticationManager,
                          IdentityService identityService,
                          AuthCookies cookies) {
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.authenticationManager = authenticationManager;
        this.identityService = identityService;
        this.cookies = cookies;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.get("username"),
                            request.get("password")
                    )
            );

            if (adminDiscordId == null || adminDiscordId.isBlank()) {
                return ResponseEntity.status(500).body(Map.of(
                        "error", "DISCORD_ADMIN_ID n'est pas configuré : le compte local ne désigne aucun acteur"));
            }

            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            UserIdentityDto identity = identityService.identity(adminDiscordId, authentication.getName())
                    .orElse(null);
            if (identity == null) {
                return ResponseEntity.status(502).body(Map.of(
                        "error", "schub-core unreachable — impossible de déterminer les permissions"));
            }

            String token = jwtService.generateToken(
                    adminDiscordId,
                    identity.user() != null ? identity.user().id() : null,
                    authentication.getName(), roles, identity.permissionsOrEmpty());
            // Jeton dans le corps (front en Bearer) et en cookie httpOnly (front migré), le temps de la transition.
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE, cookies.session(token, jwtProperties.expirationSeconds()))
                    .body(Map.of(
                            "accessToken", token,
                            "tokenType", "Bearer",
                            "expiresInSeconds", jwtProperties.expirationSeconds()
                    ));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request, Authentication authentication) {
        Object token = request.getAttribute(JwtAuthenticationFilter.TOKEN_ATTRIBUTE);
        if (authentication == null || !authentication.isAuthenticated() || token == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String jwt = String.valueOf(token);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("actorId", authentication.getName());
        body.put("userId", jwtService.extractUserId(jwt));
        body.put("username", String.valueOf(jwtService.claims(jwt).get("username")));
        body.put("roles", jwtService.extractRoles(jwt));
        body.put("permissions", jwtService.extractPermissions(jwt));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clearedSession())
                .build();
    }
}

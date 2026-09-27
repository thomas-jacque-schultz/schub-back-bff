package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.IdentityService;
import schultz.thomas.schub.bff.config.security.AuthCookies;
import schultz.thomas.schub.bff.config.security.JwtAuthenticationFilter;
import schultz.thomas.schub.bff.config.security.JwtService;
import schultz.thomas.schub.bff.data.client.UserIdentityDto;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

// La session s'ouvre par Discord (DiscordAuthController) : il n'existe plus de compte local ni de mot de passe.
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;
    private final AuthCookies cookies;
    private final IdentityService identityService;

    public AuthController(JwtService jwtService, AuthCookies cookies, IdentityService identityService) {
        this.jwtService = jwtService;
        this.cookies = cookies;
        this.identityService = identityService;
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpServletRequest request, Authentication authentication) {
        Object token = request.getAttribute(JwtAuthenticationFilter.TOKEN_ATTRIBUTE);
        if (authentication == null || !authentication.isAuthenticated() || token == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String jwt = String.valueOf(token);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("userId", authentication.getName());
        body.put("username", String.valueOf(jwtService.claims(jwt).get("username")));
        body.put("roles", jwtService.extractRoles(jwt));
        body.put("permissions", jwtService.extractPermissions(jwt));
        identityService.byUserId(authentication.getName())
                .map(UserIdentityDto::user)
                .map(UserIdentityDto.Profile::links)
                .ifPresent(links -> body.put("links", links));
        return ResponseEntity.ok(body);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clearedSession())
                .build();
    }
}

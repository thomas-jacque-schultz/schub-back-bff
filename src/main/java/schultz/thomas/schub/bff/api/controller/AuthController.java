package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.config.security.JwtProperties;
import schultz.thomas.schub.bff.config.security.JwtService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;

    public AuthController(JwtService jwtService,
                          JwtProperties jwtProperties,
                          AuthenticationManager authenticationManager) {
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.authenticationManager = authenticationManager;
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

            List<String> roles = authentication.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .toList();

            String token = jwtService.generateToken(authentication.getName(), roles);
            return ResponseEntity.ok(Map.of(
                    "accessToken", token,
                    "tokenType", "Bearer",
                    "expiresInSeconds", jwtProperties.expirationSeconds()
            ));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        return ResponseEntity.ok(Map.of(
                "username", authentication.getName(),
                "roles", authentication.getAuthorities()
        ));
    }
}

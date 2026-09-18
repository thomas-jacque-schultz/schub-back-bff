package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.IdentityService;
import schultz.thomas.schub.bff.config.security.JwtProperties;
import schultz.thomas.schub.bff.config.security.JwtService;
import schultz.thomas.schub.bff.data.client.UserIdentityDto;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Connexion et profil.
 *
 * <p>Le compte par mot de passe est <strong>conservé pour l'instant</strong> : son retrait est le
 * dernier lot du chantier A (A.6), à ne lancer qu'après une connexion Discord réussie en prod.
 * On ne démonte pas la porte de service avant d'avoir vu la porte principale s'ouvrir.</p>
 *
 * <p>Ce qui change ici, et rien d'autre : le jeton émis porte désormais les <strong>permissions
 * de l'OWNER lues dans le cœur</strong>, au lieu d'un simple {@code ROLE_ADMIN} qui ne
 * correspondait à aucune vérification. Sans ça, le compte local se connecterait au BFF et se
 * ferait refuser par le cœur à chaque appel : il ne désignerait aucun acteur connu.</p>
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;
    private final JwtProperties jwtProperties;
    private final AuthenticationManager authenticationManager;
    private final IdentityService identityService;

    /**
     * L'identifiant Discord derrière le compte local.
     *
     * <p>Le cœur ne connaît que des comptes Discord : il faut donc dire <em>qui</em> est le compte
     * local, sans quoi {@code X-Actor-Id} désignerait quelqu'un d'inexistant. C'est la même
     * valeur que celle qui sème l'OWNER dans le cœur — et c'est cohérent : le compte local
     * <em>est</em> le super-administrateur, en attendant de disparaître au lot A.6.</p>
     */
    @Value("${discord.admin.id:}")
    private String adminDiscordId;

    public AuthController(JwtService jwtService,
                          JwtProperties jwtProperties,
                          AuthenticationManager authenticationManager,
                          IdentityService identityService) {
        this.jwtService = jwtService;
        this.jwtProperties = jwtProperties;
        this.authenticationManager = authenticationManager;
        this.identityService = identityService;
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

            // Les permissions viennent du cœur, seule autorité. Si le cœur est injoignable, la
            // connexion échoue franchement : émettre un jeton sans permission produirait une
            // session connectée qui se fait refuser partout, ce qui est plus dur à diagnostiquer
            // qu'un refus net.
            UserIdentityDto identity = identityService.identity(adminDiscordId, authentication.getName())
                    .orElse(null);
            if (identity == null) {
                return ResponseEntity.status(502).body(Map.of(
                        "error", "schub-core unreachable — impossible de déterminer les permissions"));
            }

            String token = jwtService.generateToken(
                    adminDiscordId, authentication.getName(), roles, identity.permissionsOrEmpty());
            return ResponseEntity.ok(Map.of(
                    "accessToken", token,
                    "tokenType", "Bearer",
                    "expiresInSeconds", jwtProperties.expirationSeconds()
            ));
        } catch (BadCredentialsException ex) {
            return ResponseEntity.status(401).body(Map.of("error", "Invalid credentials"));
        }
    }

    /**
     * Le profil et les permissions, pour que le front sache quoi afficher.
     *
     * <p>Les permissions sont lues dans le jeton et non redemandées au cœur : c'est le jeton qui
     * fait foi pendant sa durée de vie, et {@code /auth/me} doit dire la même chose que ce que le
     * BFF applique réellement. Les redemander ici afficherait des boutons que les requêtes
     * suivantes refuseraient encore, jusqu'au prochain renouvellement.</p>
     */
    @GetMapping("/me")
    public ResponseEntity<?> me(Authentication authentication,
                                @RequestHeader(value = "Authorization", required = false) String authorization) {
        if (authentication == null || !authentication.isAuthenticated() || authorization == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized"));
        }

        String token = authorization.substring("Bearer ".length());
        return ResponseEntity.ok(Map.of(
                "actorId", authentication.getName(),
                "username", String.valueOf(jwtService.claims(token).get("username")),
                "roles", jwtService.extractRoles(token),
                "permissions", jwtService.extractPermissions(token)
        ));
    }
}

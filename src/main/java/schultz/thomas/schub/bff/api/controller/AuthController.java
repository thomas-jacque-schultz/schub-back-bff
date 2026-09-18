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
    private final AuthCookies cookies;

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
                    adminDiscordId,
                    identity.user() != null ? identity.user().id() : null,
                    authentication.getName(), roles, identity.permissionsOrEmpty());
            // Le jeton part par les deux canaux : dans le corps pour le front actuel, qui le
            // range en localStorage, et en cookie httpOnly pour le front migré. Poser le cookie
            // ici ne casse rien — l'en-tête l'emporte quand les deux sont présents — et c'est ce
            // qui rend le transport cookie exerçable en dev sans passer par Discord.
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

    /**
     * Le profil et les permissions, pour que le front sache quoi afficher.
     *
     * <p>Les permissions sont lues dans le jeton et non redemandées au cœur : c'est le jeton qui
     * fait foi pendant sa durée de vie, et {@code /auth/me} doit dire la même chose que ce que le
     * BFF applique réellement. Les redemander ici afficherait des boutons que les requêtes
     * suivantes refuseraient encore, jusqu'au prochain renouvellement.</p>
     *
     * <p><strong>{@code actorId} et {@code userId} ne sont pas la même chose, et il faut les
     * deux.</strong> {@code actorId} est l'identifiant Discord — le sujet du jeton, ce que le BFF
     * repasse au cœur. {@code userId} est l'id interne du compte, et c'est <em>lui seul</em> que
     * contiennent les {@code admins} d'un serveur (plan §A.4). Tant que {@code /auth/me} ne
     * rendait que le premier, le front n'avait rien à comparer : il proposait démarrer/arrêter à
     * tout compte connecté, et le cœur répondait 403 après le clic.</p>
     *
     * <p>Ce n'est pas une fuite : c'est l'id de l'appelant lui-même. L'id interne d'un
     * <em>autre</em> compte, lui, ne sort que dans la projection infra d'un serveur, derrière
     * {@code SERVER_INFRA_VIEW}. Pour les autres, le cœur répond par le booléen
     * {@code viewerIsAdmin}, qui ne nomme personne.</p>
     *
     * <p>Le jeton est repris de l'attribut posé par le filtre, donc indifféremment du cookie ou
     * de l'en-tête {@code Authorization} — les deux transports vivent côte à côte le temps de la
     * migration du front.</p>
     */
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

    /**
     * Se déconnecter — c'est-à-dire effacer le cookie.
     *
     * <p>Cette route n'existait pas, et elle devient nécessaire avec la décision n°4 : un cookie
     * {@code httpOnly} est par construction illisible et ineffaçable depuis le front. Sans elle,
     * « se déconnecter » ne ferait qu'oublier un jeton que le navigateur continuerait d'envoyer
     * jusqu'à son expiration.</p>
     *
     * <p>Le jeton n'est pas révoqué — il n'y a rien où le révoquer, le BFF est sans état. Il
     * expire en quinze minutes, et c'est la contrepartie assumée de la décision n°3.</p>
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clearedSession())
                .build();
    }
}

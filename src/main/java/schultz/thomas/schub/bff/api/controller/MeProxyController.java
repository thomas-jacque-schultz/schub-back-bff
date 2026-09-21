package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le compte de l'appelant, vu par lui-même — l'identité et le nom affiché.
 *
 * <p>Le compte Riot, lui, est servi par {@link UserProxyController} sous
 * {@code /users/me/riot-account} : c'est le découpage du cœur, et le BFF route sans réécrire le
 * vocabulaire. Les uniformiser ici ferait diverger les deux moitiés du même profil.</p>
 *
 * <h2>Pourquoi aucune permission nominale</h2>
 *
 * <p>{@link TeamProxyController} s'arrête à {@code isAuthenticated()} parce que le BFF ne
 * <em>peut pas</em> savoir qui est membre de quelle équipe. Ici la raison est différente et plus
 * forte : <strong>la ressource est le lecteur</strong>. Il n'existe aucun chemin vers le profil
 * de quelqu'un d'autre — une route sans second paramètre, donc rien à oublier de contrôler.</p>
 *
 * <p>Écrire {@code hasAuthority('USER_VIEW')} ici serait fait de bonne foi, par symétrie avec
 * l'écran d'administration des comptes, et fermerait le site à tout compte neuf : un
 * {@code VISITEUR} ne pourrait plus voir son propre profil. {@code ProfilProxyAuthorizationTest}
 * fige l'invariant.</p>
 */
@RestController
@RequestMapping("/me")
public class MeProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public MeProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    /**
     * Identité, rôle, permissions et compte Riot d'un seul appel.
     *
     * <p>À ne pas confondre avec {@code GET /auth/me}, qui lit le <strong>jeton</strong> et dit
     * ce que le BFF appliquera. Celle-ci lit le cœur et dit ce qui est vrai : l'avatar, le nom
     * choisi et l'état Riot n'y sont pas, et ne peuvent pas y être.</p>
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> me() {
        return gateway.call(UPSTREAM, core::getMe);
    }

    @PutMapping("/display-name")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> updateDisplayName(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.updateMyDisplayName(gateway.parseBody(body)));
    }

    /**
     * {@code /me/stats}, et jamais {@code /players/{puuid}/stats} : comme le reste de {@code /me},
     * la route n'a pas de second paramètre, donc pas de cible.
     */
    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> stats(@RequestParam(required = false) Integer days,
                                        @RequestParam(required = false) Integer champions) {
        return gateway.call(UPSTREAM, () -> core.getMyStats(days, champions));
    }
}

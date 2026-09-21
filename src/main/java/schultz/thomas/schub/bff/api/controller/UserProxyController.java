package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * L'écran des utilisateurs (lot A.5), servi par le cœur.
 *
 * <p>Le double contrôle n'est pas de la ceinture et des bretelles : le BFF refuse tôt et sans
 * appel réseau, le cœur refuse au point d'action parce qu'il porte les règles anti-élévation —
 * lui seul peut comparer les permissions de l'acteur à celles du rôle attribué.</p>
 */
@RestController
@RequestMapping("/users")
public class UserProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public UserProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<byte[]> all() {
        return gateway.call(UPSTREAM, core::getUsers);
    }

    // --- le compte Riot de l'appelant ---
    //
    // Sous /users/me, comme dans le cœur. Aucune permission : la ressource EST le lecteur, et il
    // n'existe aucun chemin vers le compte Riot de quelqu'un d'autre. C'est exactement l'inverse
    // des deux routes ci-dessus, qui servent l'administration DES comptes — lire les comptes est
    // un droit, lire le sien n'en est pas un.

    @GetMapping("/me/riot-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> myRiotAccount() {
        return gateway.call(UPSTREAM, core::getMyRiotAccount);
    }

    /**
     * Déclarer, relancer ou remplacer son Riot ID.
     *
     * <p>Idempotente : rejouer le même Riot ID relance la résolution du {@code puuid}, ce qui est
     * le « réessayer » dont l'écran a besoin quand le connecteur était éteint.</p>
     *
     * <p><strong>Le 409 de remplacement non confirmé n'est pas une panne</strong> : son corps
     * porte l'objet {@code change} qui dit ce que le changement emporte, et c'est de quoi poser
     * la question à l'écran. {@link UpstreamGateway} relaie statut et corps tels quels, donc il
     * traverse intact — le traduire en 502 priverait le front de la seule information qui lui
     * permet de demander confirmation.</p>
     */
    @PutMapping("/me/riot-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> linkMyRiotAccount(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.linkMyRiotAccount(gateway.parseBody(body)));
    }

    /**
     * Les comptes connus qui ressemblent à une saisie partielle.
     *
     * <p>L'API Riot ne sait pas chercher par pseudo partiel ; les propositions viennent de nos
     * propres parties collectées. <strong>Une liste vide est l'état normal au démarrage</strong>,
     * pas une erreur.</p>
     */
    @GetMapping("/me/riot-account/suggestions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> suggestRiotAccounts(@RequestParam String q,
                                                      @RequestParam(required = false) Integer limit) {
        return gateway.call(UPSTREAM, () -> core.suggestRiotAccounts(q, limit));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
    public ResponseEntity<byte[]> assignRole(@PathVariable String id, @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.assignRole(id, gateway.parseBody(body)));
    }
}

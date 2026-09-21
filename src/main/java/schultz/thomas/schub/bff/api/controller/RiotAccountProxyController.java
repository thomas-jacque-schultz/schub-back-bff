package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les comptes Riot que <strong>nous</strong> connaissons, pour aider quelqu'un à retrouver le
 * sien.
 *
 * <h2>Pourquoi cette route existe, et pourquoi elle ne parle pas à Riot</h2>
 *
 * <p>L'API Riot ne sait pas chercher un joueur par pseudo partiel — cette opération n'existe pas
 * chez eux. La seule matière disponible est celle qu'on a déjà collectée : chaque partie ingérée
 * porte le Riot ID de ses dix participants. Une saisie approchante est donc rapprochée de nos
 * propres données, jamais de Riot.</p>
 *
 * <p>Conséquence à ne pas traiter comme une panne : <strong>au démarrage, cette route rend une
 * liste vide</strong>, parce que rien n'a encore été ingéré. C'est le cas normal, et l'écran doit
 * le dire plutôt que de paraître cassé. La saisie exacte {@code Pseudo#TAG} reste de toute façon
 * ouverte : la recherche est une aide, pas le chemin.</p>
 *
 * <h2>Séparée de {@link MeProxyController}, et à raison</h2>
 *
 * <p>Ce n'est pas une sous-ressource de l'appelant : elle décrit des comptes qui ne sont pas les
 * siens. Elle porte donc son propre chemin et sa propre règle — {@code isAuthenticated()}, parce
 * qu'elle sert à se lier soi-même et qu'un compte tout juste créé n'a encore aucune permission
 * au-delà de celles du rôle {@code VISITEUR}.</p>
 */
@RestController
@RequestMapping("/riot-accounts")
public class RiotAccountProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public RiotAccountProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping("/search")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> search(@RequestParam String q,
                                         @RequestParam(required = false) Integer limit) {
        return gateway.call(UPSTREAM, () -> core.searchKnownRiotAccounts(q, limit));
    }
}

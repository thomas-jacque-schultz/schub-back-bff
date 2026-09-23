package schultz.thomas.schub.bff.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

// Le 204 du cœur (pas encore de référentiel) reviendrait en 200 sans corps : on le rend tel quel.
@RestController
@RequestMapping("/lol/references")
public class ReferenceProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public ReferenceProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping("/champions/{championId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> champion(@PathVariable int championId, @RequestParam String tier) {
        return sansCorps(gateway.call(UPSTREAM, () -> core.getChampionGrid(championId, tier)));
    }

    @GetMapping("/{position}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> grid(@PathVariable String position,
                                       @RequestParam(required = false) String scope,
                                       @RequestParam(required = false) String tier,
                                       @RequestParam(required = false) String patch) {
        return sansCorps(gateway.call(UPSTREAM, () -> core.getReferenceGrid(position, scope, tier, patch)));
    }

    private static ResponseEntity<byte[]> sansCorps(ResponseEntity<byte[]> reponse) {
        if (reponse.getStatusCode().is2xxSuccessful() && (reponse.getBody() == null || reponse.getBody().length == 0)) {
            return ResponseEntity.noContent().build();
        }
        return reponse;
    }
}

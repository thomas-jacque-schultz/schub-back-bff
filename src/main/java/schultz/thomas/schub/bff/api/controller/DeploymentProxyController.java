package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Catalogue des déploiements, pour lier un GameServer à la stack qui le réalise.
 *
 * <p>Ce chemin s'appelait {@code /portainer/stacks}. Une marque d'outil n'a rien à faire dans
 * l'API du BFF : le jour où Portainer est remplacé, le front n'a pas à bouger. Le cœur expose
 * {@code /deployments}, le BFF aussi.</p>
 *
 * <p>Derrière {@code SERVER_INFRA_VIEW} : cette liste est l'inventaire des stacks de la machine,
 * y compris celles qui n'ont rien à voir avec un jeu.</p>
 */
@RestController
@RequestMapping("/deployments")
public class DeploymentProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public DeploymentProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SERVER_INFRA_VIEW')")
    public ResponseEntity<byte[]> all() {
        return gateway.call(UPSTREAM, core::getDeployments);
    }
}

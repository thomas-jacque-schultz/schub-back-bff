package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La charge de la collecte Riot. {@code INGEST_VIEW} n'est portée que par OWNER : c'est une
 * donnée d'exploitation, pas une donnée de jeu.
 */
@RestController
@RequestMapping("/ingest")
public class IngestLoadProxyController {

    private static final String UPSTREAM = "core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public IngestLoadProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping("/load")
    @PreAuthorize("hasAuthority('INGEST_VIEW')")
    public ResponseEntity<byte[]> load() {
        return gateway.call(UPSTREAM, core::getIngestLoad);
    }
}

package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

    @GetMapping("/crawler")
    @PreAuthorize("hasAuthority('INGEST_VIEW')")
    public ResponseEntity<byte[]> crawler() {
        return gateway.call(UPSTREAM, core::getCrawler);
    }

    @PutMapping("/crawler")
    @PreAuthorize("hasAuthority('INGEST_MANAGE')")
    public ResponseEntity<byte[]> toggleCrawler(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.toggleCrawler(gateway.parseBody(body)));
    }
}

package schultz.thomas.schub.bff.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

@RestController
@RequestMapping("/premadelab/settings")
public class PremadeLabSettingsProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public PremadeLabSettingsProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INGEST_VIEW')")
    public ResponseEntity<byte[]> get() {
        return gateway.call(UPSTREAM, core::getPremadeLabSettings);
    }

    @PutMapping
    @PreAuthorize("hasAuthority('INGEST_MANAGE')")
    public ResponseEntity<byte[]> update(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.updatePremadeLabSettings(gateway.parseBody(body)));
    }
}

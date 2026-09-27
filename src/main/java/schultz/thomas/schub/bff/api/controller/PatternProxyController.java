package schultz.thomas.schub.bff.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

// L'éditeur des règles du moteur Augur, dans la configuration de PremadeLab côté Schub.
@RestController
@RequestMapping("/augur/patterns")
@PreAuthorize("hasAuthority('AUGUR_PATTERN_EDIT')")
public class PatternProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public PatternProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    public ResponseEntity<byte[]> list() {
        return gateway.call(UPSTREAM, core::getPatterns);
    }

    @GetMapping("/{key}/versions")
    public ResponseEntity<byte[]> versions(@PathVariable String key) {
        return gateway.call(UPSTREAM, () -> core.getPatternVersions(key));
    }

    @PostMapping
    public ResponseEntity<byte[]> draft(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.draftPattern(gateway.parseBody(body)));
    }

    @GetMapping("/{key}/versions/{version}/impact")
    public ResponseEntity<byte[]> impact(@PathVariable String key, @PathVariable int version) {
        return gateway.call(UPSTREAM, () -> core.getPatternImpact(key, version));
    }

    @PostMapping("/{key}/versions/{version}/activate")
    public ResponseEntity<byte[]> activate(@PathVariable String key, @PathVariable int version,
                                           @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.activatePattern(key, version, gateway.parseBody(body)));
    }

    @PostMapping("/{key}/rollback")
    public ResponseEntity<byte[]> rollback(@PathVariable String key, @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.rollbackPattern(key, gateway.parseBody(body)));
    }
}

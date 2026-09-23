package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/teams/{teamId}/compositions")
public class CompositionProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public CompositionProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> all(@PathVariable String teamId) {
        return gateway.call(UPSTREAM, () -> core.getCompositions(teamId));
    }

    @GetMapping("/{compositionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> byId(@PathVariable String teamId, @PathVariable String compositionId) {
        return gateway.call(UPSTREAM, () -> core.getComposition(teamId, compositionId));
    }

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> create(@PathVariable String teamId,
                                         @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, HttpStatus.CREATED,
                () -> core.createComposition(teamId, gateway.parseBody(body)));
    }

    @PutMapping("/{compositionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> update(@PathVariable String teamId,
                                         @PathVariable String compositionId,
                                         @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.updateComposition(teamId, compositionId, gateway.parseBody(body)));
    }

    @DeleteMapping("/{compositionId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> delete(@PathVariable String teamId, @PathVariable String compositionId) {
        return gateway.callVoid(UPSTREAM, () -> core.deleteComposition(teamId, compositionId));
    }
}

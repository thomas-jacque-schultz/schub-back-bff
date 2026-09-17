package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redirections de ports. La politique vit dans le cœur ; la box est derrière son connecteur,
 * que le BFF ne connaît pas.
 */
@RestController
@RequestMapping("/port-forwarding")
public class PortForwardingProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public PortForwardingProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping("/rules")
    public ResponseEntity<byte[]> rules() {
        return gateway.call(UPSTREAM, core::getPortForwardingRules);
    }

    @GetMapping("/status")
    public ResponseEntity<byte[]> status() {
        return gateway.call(UPSTREAM, core::getPortForwardingStatus);
    }

    @GetMapping("/static-rules")
    public ResponseEntity<byte[]> staticRules() {
        return gateway.call(UPSTREAM, core::getStaticPortRules);
    }

    @PostMapping("/static-rules")
    public ResponseEntity<byte[]> createStaticRule(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, HttpStatus.CREATED, () -> core.createStaticPortRule(gateway.parseBody(body)));
    }

    @DeleteMapping("/static-rules/{id}")
    public ResponseEntity<byte[]> deleteStaticRule(@PathVariable String id) {
        return gateway.callVoid(UPSTREAM, () -> core.deleteStaticPortRule(id));
    }
}

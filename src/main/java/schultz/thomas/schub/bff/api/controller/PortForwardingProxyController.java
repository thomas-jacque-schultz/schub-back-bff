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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Redirections de ports. La politique vit dans le cœur ; la box est derrière son connecteur,
 * que le BFF ne connaît pas.
 *
 * <p>{@code PORT_RULE_EDIT} est une permission de rôle et le restera : ouvrir un port, c'est
 * écrire dans la table de redirections de la box — un pouvoir global, qu'on ne déguise pas en
 * pouvoir local sous prétexte qu'il sert un serveur précis (décision n°11).</p>
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
    @PreAuthorize("hasAuthority('PORT_VIEW')")
    public ResponseEntity<byte[]> rules() {
        return gateway.call(UPSTREAM, core::getPortForwardingRules);
    }

    @GetMapping("/status")
    @PreAuthorize("hasAuthority('PORT_VIEW')")
    public ResponseEntity<byte[]> status() {
        return gateway.call(UPSTREAM, core::getPortForwardingStatus);
    }

    @GetMapping("/static-rules")
    @PreAuthorize("hasAuthority('PORT_VIEW')")
    public ResponseEntity<byte[]> staticRules() {
        return gateway.call(UPSTREAM, core::getStaticPortRules);
    }

    @PostMapping("/static-rules")
    @PreAuthorize("hasAuthority('PORT_RULE_EDIT')")
    public ResponseEntity<byte[]> createStaticRule(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, HttpStatus.CREATED, () -> core.createStaticPortRule(gateway.parseBody(body)));
    }

    @DeleteMapping("/static-rules/{id}")
    @PreAuthorize("hasAuthority('PORT_RULE_EDIT')")
    public ResponseEntity<byte[]> deleteStaticRule(@PathVariable String id) {
        return gateway.callVoid(UPSTREAM, () -> core.deleteStaticPortRule(id));
    }
}

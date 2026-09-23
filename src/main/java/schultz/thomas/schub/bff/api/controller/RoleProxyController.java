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
@RequestMapping("/roles")
public class RoleProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public RoleProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW') or hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<byte[]> all() {
        return gateway.call(UPSTREAM, core::getRoles);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<byte[]> create(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, HttpStatus.CREATED, () -> core.createRole(gateway.parseBody(body)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<byte[]> update(@PathVariable String id, @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.updateRole(id, gateway.parseBody(body)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('ROLE_MANAGE')")
    public ResponseEntity<byte[]> delete(@PathVariable String id) {
        return gateway.callVoid(UPSTREAM, () -> core.deleteRole(id));
    }
}

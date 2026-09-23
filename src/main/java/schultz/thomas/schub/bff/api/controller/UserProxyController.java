package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/users")
public class UserProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public UserProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('USER_VIEW')")
    public ResponseEntity<byte[]> all() {
        return gateway.call(UPSTREAM, core::getUsers);
    }

    @GetMapping("/me/riot-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> myRiotAccount() {
        return gateway.call(UPSTREAM, core::getMyRiotAccount);
    }

    @PutMapping("/me/riot-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> linkMyRiotAccount(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.linkMyRiotAccount(gateway.parseBody(body)));
    }

    @GetMapping("/me/riot-account/suggestions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> suggestRiotAccounts(@RequestParam String q,
                                                      @RequestParam(required = false) Integer limit) {
        return gateway.call(UPSTREAM, () -> core.suggestRiotAccounts(q, limit));
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasAuthority('USER_ROLE_ASSIGN')")
    public ResponseEntity<byte[]> assignRole(@PathVariable String id, @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.assignRole(id, gateway.parseBody(body)));
    }
}

package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/me")
public class MeProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public MeProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> me() {
        return gateway.call(UPSTREAM, core::getMe);
    }

    @PutMapping("/display-name")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> updateDisplayName(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.updateMyDisplayName(gateway.parseBody(body)));
    }

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> stats(@RequestParam(required = false) Integer days,
                                        @RequestParam(required = false) Integer patches,
                                        @RequestParam(required = false) Integer champions) {
        return gateway.call(UPSTREAM, () -> core.getMyStats(days, patches, champions));
    }
}

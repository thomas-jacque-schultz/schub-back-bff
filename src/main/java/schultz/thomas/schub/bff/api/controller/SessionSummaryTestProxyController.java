package schultz.thomas.schub.bff.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

@RestController
@RequestMapping("/premadelab/session-summary-test")
public class SessionSummaryTestProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public SessionSummaryTestProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INGEST_MANAGE')")
    public ResponseEntity<byte[]> recent() {
        return gateway.call(UPSTREAM, core::getSessionSummaryTestSessions);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('INGEST_MANAGE')")
    public ResponseEntity<byte[]> send(@RequestParam String day, @RequestParam(defaultValue = "true") boolean send) {
        return gateway.call(UPSTREAM, () -> core.sendSessionSummaryTest(day, send));
    }
}

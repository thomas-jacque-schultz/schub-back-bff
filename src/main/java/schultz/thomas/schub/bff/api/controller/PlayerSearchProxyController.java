package schultz.thomas.schub.bff.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.business.service.VisitorHasher;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

@RestController
public class PlayerSearchProxyController {

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;
    private final VisitorHasher visitors;

    public PlayerSearchProxyController(CoreFeignClient core, UpstreamGateway gateway, VisitorHasher visitors) {
        this.core = core;
        this.gateway = gateway;
        this.visitors = visitors;
    }

    @GetMapping("/players/search")
    public ResponseEntity<byte[]> search(@RequestParam String q, @RequestParam(required = false) Integer limit,
                                         HttpServletRequest request) {
        String visiteur = visitors.hash(ClientAddress.of(request), request.getHeader(HttpHeaders.USER_AGENT));
        return gateway.call("schub-core", () -> core.searchPlayers(q, limit, visiteur));
    }

    @GetMapping("/players/history-window")
    public ResponseEntity<byte[]> historyWindow() {
        return gateway.call("schub-core", core::getHistoryWindow);
    }
}

package schultz.thomas.schub.bff.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

@RestController
public class PlayerSearchProxyController {

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public PlayerSearchProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping("/players/search")
    public ResponseEntity<byte[]> search(@RequestParam String q, @RequestParam(required = false) Integer limit) {
        return gateway.call("schub-core", () -> core.searchPlayers(q, limit));
    }

    @GetMapping("/players/history-window")
    public ResponseEntity<byte[]> historyWindow() {
        return gateway.call("schub-core", core::getHistoryWindow);
    }
}

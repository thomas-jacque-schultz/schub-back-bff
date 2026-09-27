package schultz.thomas.schub.bff.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

// Publique : la recherche d'un joueur est la vitrine de PremadeLab, ouverte sans compte.
@RestController
@RequestMapping("/players/{riotId}")
public class PlayerProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public PlayerProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    public ResponseEntity<byte[]> page(@PathVariable String riotId,
                                       @RequestParam(required = false) Integer days,
                                       @RequestParam(required = false) Integer patches,
                                       @RequestParam(required = false) Integer champions,
                                       @RequestParam(required = false) Boolean light) {
        return gateway.call(UPSTREAM, () -> core.getPlayer(riotId, days, patches, champions, light));
    }

    @GetMapping("/games")
    public ResponseEntity<byte[]> games(@PathVariable String riotId,
                                        @RequestParam(required = false) Integer days,
                                        @RequestParam(required = false) Integer patches,
                                        @RequestParam(required = false) Integer limit) {
        return gateway.call(UPSTREAM, () -> core.getPlayerGames(riotId, days, patches, limit));
    }

    @GetMapping("/games/{matchId}")
    public ResponseEntity<byte[]> game(@PathVariable String riotId,
                                       @PathVariable String matchId,
                                       @RequestParam(required = false) Integer days,
                                       @RequestParam(required = false) Integer patches) {
        return gateway.call(UPSTREAM, () -> core.getPlayerGame(riotId, matchId, days, patches));
    }

    // Le budget de recherche se compte par compte, ou par adresse IP sans compte.
    @PostMapping("/collect")
    public ResponseEntity<byte[]> collect(@PathVariable String riotId, HttpServletRequest request,
                                          Authentication authentication) {
        String visitor = authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getName())
                ? "user:" + authentication.getName()
                : "ip:" + ClientAddress.of(request);
        return gateway.call(UPSTREAM, () -> core.collectPlayer(riotId, visitor));
    }
}

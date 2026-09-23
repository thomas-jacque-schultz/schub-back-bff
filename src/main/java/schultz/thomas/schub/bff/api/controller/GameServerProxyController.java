package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/game-servers")
public class GameServerProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public GameServerProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('SERVER_VIEW')")
    public ResponseEntity<byte[]> all() {
        return gateway.call(UPSTREAM, core::getGameServers);
    }

    @GetMapping("/public-status")
    public ResponseEntity<byte[]> publicStatus() {
        return gateway.call(UPSTREAM, core::getPublicStatus);
    }

    @GetMapping("/games")
    @PreAuthorize("hasAuthority('SERVER_VIEW')")
    public ResponseEntity<byte[]> games() {
        return gateway.call(UPSTREAM, core::getGames);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('SERVER_VIEW')")
    public ResponseEntity<byte[]> byId(@PathVariable String id) {
        return gateway.call(UPSTREAM, () -> core.getGameServerById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('SERVER_CREATE')")
    public ResponseEntity<byte[]> create(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, HttpStatus.CREATED, () -> core.createGameServer(gateway.parseBody(body)));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('SERVER_EDIT')")
    public ResponseEntity<byte[]> update(@PathVariable String id, @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.updateGameServer(id, gateway.parseBody(body)));
    }

    @PostMapping("/{slug}/start")
    @PreAuthorize("hasAuthority('SERVER_START')")
    public ResponseEntity<byte[]> start(@PathVariable String slug) {
        return gateway.callVoid(UPSTREAM, () -> core.startGameServer(slug));
    }

    @PostMapping("/{slug}/stop")
    @PreAuthorize("hasAuthority('SERVER_STOP')")
    public ResponseEntity<byte[]> stop(@PathVariable String slug) {
        return gateway.callVoid(UPSTREAM, () -> core.stopGameServer(slug));
    }
}

package schultz.thomas.schub.bff.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

// Qui joue où parmi ses coéquipiers (Schub#149) : connecté, sans permission, la ressource est le lecteur.
@RestController
@RequestMapping("/queue-killer")
public class QueueKillerProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public QueueKillerProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @PutMapping("/presence")
    public ResponseEntity<byte[]> signal(@RequestBody(required = false) byte[] body) {
        return gateway.callVoid(UPSTREAM, () -> core.signalQueueKillerPresence(gateway.parseBody(body)));
    }

    @DeleteMapping("/presence/{game}/{room}")
    public ResponseEntity<byte[]> leave(@PathVariable String game, @PathVariable String room) {
        return gateway.callVoid(UPSTREAM, () -> core.leaveQueueKillerRoom(game, room));
    }

    @GetMapping("/premade-rooms")
    public ResponseEntity<byte[]> premadeRooms() {
        return gateway.call(UPSTREAM, core::getPremadeRooms);
    }
}

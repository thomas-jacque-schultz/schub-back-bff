package fr.schub.schubback.api.controllers.controller;

import fr.schub.schubback.api.business.services.BotGatewayService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/bot")
public class BotProxyController {

    private final BotGatewayService botGatewayService;

    public BotProxyController(BotGatewayService botGatewayService) {
        this.botGatewayService = botGatewayService;
    }

    @GetMapping("/gaming-server")
    public ResponseEntity<byte[]> getGamingServers(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return botGatewayService.getGamingServers(authorization);
    }

    @GetMapping("/gaming-server/public-status")
    public ResponseEntity<byte[]> getPublicGamingServersStatus() {
        return botGatewayService.getPublicGamingServersStatus();
    }

    @GetMapping("/gaming-server/{id}")
    public ResponseEntity<byte[]> getGamingServerById(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @PathVariable String id) {
        return botGatewayService.getGamingServerById(authorization, id);
    }

    @PostMapping("/gaming-server")
    public ResponseEntity<byte[]> createGamingServer(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @RequestBody(required = false) byte[] body) {
        return botGatewayService.createGamingServer(authorization, body);
    }

    @PutMapping("/gaming-server/{id}")
    public ResponseEntity<byte[]> updateGamingServer(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                      @PathVariable String id,
                                                      @RequestBody(required = false) byte[] body) {
        return botGatewayService.updateGamingServer(authorization, id, body);
    }

    @PostMapping("/gaming-server/subscribe-channels")
    public ResponseEntity<byte[]> subscribeChannels(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                    @RequestBody(required = false) byte[] body) {
        return botGatewayService.subscribeChannels(authorization, body);
    }

    @PostMapping("/gaming-server/command/{commandName}")
    public ResponseEntity<byte[]> executeGamingServerCommand(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                              @PathVariable String commandName,
                                                              @RequestBody(required = false) byte[] body) {
        return botGatewayService.executeGamingServerCommand(authorization, commandName, body);
    }

    @GetMapping("/discord/guilds/channels")
    public ResponseEntity<byte[]> getGuildsChannels(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return botGatewayService.getGuildsChannels(authorization);
    }

    // --- redirections de ports ---

    @GetMapping("/port-forwarding/rules")
    public ResponseEntity<byte[]> getPortForwardingRules(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return botGatewayService.getPortForwardingRules(authorization);
    }

    @GetMapping("/port-forwarding/static-rules")
    public ResponseEntity<byte[]> getStaticPortRules(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return botGatewayService.getStaticPortRules(authorization);
    }

    @PostMapping("/port-forwarding/static-rules")
    public ResponseEntity<byte[]> createStaticPortRule(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @RequestBody(required = false) byte[] body) {
        return botGatewayService.createStaticPortRule(authorization, body);
    }

    @DeleteMapping("/port-forwarding/static-rules/{id}")
    public ResponseEntity<byte[]> deleteStaticPortRule(@RequestHeader(value = "Authorization", required = false) String authorization,
                                                       @PathVariable String id) {
        return botGatewayService.deleteStaticPortRule(authorization, id);
    }

    // --- catalogue Portainer ---

    @GetMapping("/portainer/stacks")
    public ResponseEntity<byte[]> getPortainerStacks(@RequestHeader(value = "Authorization", required = false) String authorization) {
        return botGatewayService.getPortainerStacks(authorization);
    }
}

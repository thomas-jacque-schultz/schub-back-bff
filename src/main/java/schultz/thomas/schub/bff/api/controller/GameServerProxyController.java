package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le domaine, servi au front. Tout part vers le cœur depuis la phase 4.
 *
 * <p>Le chemin est {@code /game-servers}, pas {@code /gaming-server} : le §2 du plan impose
 * {@code GameServer} partout sans exception, et le cœur expose déjà ce chemin.</p>
 */
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
    public ResponseEntity<byte[]> all() {
        return gateway.call(UPSTREAM, core::getGameServers);
    }

    /** Seule route ouverte sans authentification (cf. SecurityConfig) : la page publique. */
    @GetMapping("/public-status")
    public ResponseEntity<byte[]> publicStatus() {
        return gateway.call(UPSTREAM, core::getPublicStatus);
    }

    @GetMapping("/games")
    public ResponseEntity<byte[]> games() {
        return gateway.call(UPSTREAM, core::getGames);
    }

    @GetMapping("/{id}")
    public ResponseEntity<byte[]> byId(@PathVariable String id) {
        return gateway.call(UPSTREAM, () -> core.getGameServerById(id));
    }

    @PostMapping
    public ResponseEntity<byte[]> create(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, HttpStatus.CREATED, () -> core.createGameServer(gateway.parseBody(body)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<byte[]> update(@PathVariable String id, @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.updateGameServer(id, gateway.parseBody(body)));
    }

    /**
     * Démarrage et arrêt, repérés par le slug.
     *
     * <p>Avant la phase 4, le front passait par {@code /gaming-server/command/{start|pause}} du
     * connecteur Discord, avec l'identifiant en corps de requête. Démarrer un serveur est une
     * action de domaine : elle appartient au cœur. Le chemin des commandes reste au connecteur
     * pour ce qu'il sert réellement — les commandes slash de Discord.</p>
     */
    @PostMapping("/{slug}/start")
    public ResponseEntity<byte[]> start(@PathVariable String slug) {
        return gateway.callVoid(UPSTREAM, () -> core.startGameServer(slug));
    }

    @PostMapping("/{slug}/stop")
    public ResponseEntity<byte[]> stop(@PathVariable String slug) {
        return gateway.callVoid(UPSTREAM, () -> core.stopGameServer(slug));
    }
}

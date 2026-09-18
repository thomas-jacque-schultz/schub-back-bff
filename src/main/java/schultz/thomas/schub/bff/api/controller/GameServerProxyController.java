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

/**
 * Le domaine, servi au front. Tout part vers le cœur depuis la phase 4.
 *
 * <p>Le chemin est {@code /game-servers}, pas {@code /gaming-server} : le §2 du plan impose
 * {@code GameServer} partout sans exception, et le cœur expose déjà ce chemin.</p>
 *
 * <p>Chaque route porte sa permission depuis le 18-09. Jusque-là le BFF appliquait
 * {@code anyRequest().authenticated()} et pas un seul {@code hasRole} : sans danger avec un
 * compte unique, mais ouvrir la connexion Discord à tout le monde dans cet état aurait donné à
 * n'importe quel compte l'accès à {@code POST /game-servers} et à l'arrêt des serveurs. C'est
 * le préalable absolu du chantier (plan §A.0).</p>
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
    @PreAuthorize("hasAuthority('SERVER_VIEW')")
    public ResponseEntity<byte[]> all() {
        return gateway.call(UPSTREAM, core::getGameServers);
    }

    /** Seule route ouverte sans authentification (cf. SecurityConfig) : la page publique. */
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

    /**
     * Démarrage et arrêt, repérés par le slug.
     *
     * <p><strong>Le BFF exige {@code SERVER_VIEW} ici, et pas {@code SERVER_START}, et c'est
     * délibéré.</strong> Être administrateur d'un serveur donne le droit de le démarrer sans que
     * le rôle porte {@code SERVER_START} (décision n°11) — or le BFF ne connaît pas la liste des
     * {@code admins}, qui vit dans le cœur. Exiger {@code SERVER_START} ici refuserait à un
     * administrateur de serveur une action que le cœur lui accorde, et la décision n°11 ne
     * vaudrait plus que pour Discord. Le contrôle grossier s'arrête donc à « c'est un membre
     * connecté » ; le contrôle fin est dans le cœur, seul à pouvoir le faire (plan §A.2).</p>
     */
    @PostMapping("/{slug}/start")
    @PreAuthorize("hasAuthority('SERVER_VIEW')")
    public ResponseEntity<byte[]> start(@PathVariable String slug) {
        return gateway.callVoid(UPSTREAM, () -> core.startGameServer(slug));
    }

    @PostMapping("/{slug}/stop")
    @PreAuthorize("hasAuthority('SERVER_VIEW')")
    public ResponseEntity<byte[]> stop(@PathVariable String slug) {
        return gateway.callVoid(UPSTREAM, () -> core.stopGameServer(slug));
    }
}

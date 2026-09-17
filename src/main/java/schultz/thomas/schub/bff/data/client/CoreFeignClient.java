package schultz.thomas.schub.bff.data.client;

import schultz.thomas.schub.bff.config.InternalSecretFeignConfig;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Le cœur : tout le domaine. GameServer, déploiements, politique de ports.
 *
 * <p>Depuis la phase 4, c'est ici que part tout ce qui n'est pas spécifiquement Discord. Les
 * chemins sont ceux du cœur ({@code /game-servers}, {@code /deployments}) — le BFF ne réécrit
 * pas le vocabulaire, il route.</p>
 */
@FeignClient(name = "coreClient", url = "${core.base-url}", configuration = InternalSecretFeignConfig.class)
public interface CoreFeignClient {

    @GetMapping("/game-servers")
    byte[] getGameServers();

    @GetMapping("/game-servers/public-status")
    byte[] getPublicStatus();

    @GetMapping("/game-servers/{id}")
    byte[] getGameServerById(@PathVariable("id") String id);

    @PostMapping("/game-servers")
    byte[] createGameServer(@RequestBody Object body);

    @PutMapping("/game-servers/{id}")
    byte[] updateGameServer(@PathVariable("id") String id, @RequestBody Object body);

    /** Actions de cycle de vie : repérées par le slug, pas par l'identifiant Mongo. */
    @PostMapping("/game-servers/{slug}/start")
    void startGameServer(@PathVariable("slug") String slug);

    @PostMapping("/game-servers/{slug}/stop")
    void stopGameServer(@PathVariable("slug") String slug);

    @GetMapping("/game-servers/games")
    byte[] getGames();

    // --- déploiements (anciennement « stacks Portainer » : la marque ne remonte plus ici) ---

    @GetMapping("/deployments")
    byte[] getDeployments();

    // --- redirections de ports ---

    @GetMapping("/port-forwarding/rules")
    byte[] getPortForwardingRules();

    @GetMapping("/port-forwarding/static-rules")
    byte[] getStaticPortRules();

    @PostMapping("/port-forwarding/static-rules")
    byte[] createStaticPortRule(@RequestBody Object body);

    @DeleteMapping("/port-forwarding/static-rules/{id}")
    void deleteStaticPortRule(@PathVariable("id") String id);

    @GetMapping("/port-forwarding/status")
    byte[] getPortForwardingStatus();
}

package schultz.thomas.schub.bff.data.client;

import schultz.thomas.schub.bff.config.InternalSecretFeignConfig;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Le cœur : tout le domaine. GameServer, déploiements, politique de ports, et depuis le 18-09
 * l'identité — comptes, rôles, permissions.
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

    // --- identité (plan §1 : elle vit dans le cœur depuis le 18-09) ---

    /**
     * Le profil et les permissions d'un compte Discord, créé au rôle {@code VISITEUR} s'il est
     * inconnu. Seule route du cœur dont le BFF lit le contenu : il lui faut les permissions pour
     * composer le jeton.
     */
    @GetMapping("/users/by-discord/{discordId}")
    UserIdentityDto getIdentity(@PathVariable("discordId") String discordId,
                                @RequestParam(value = "discordUsername", required = false) String discordUsername,
                                @RequestParam(value = "avatarUrl", required = false) String avatarUrl);

    @GetMapping("/users")
    byte[] getUsers();

    @PutMapping("/users/{id}/role")
    byte[] assignRole(@PathVariable("id") String id, @RequestBody Object body);

    @GetMapping("/roles")
    byte[] getRoles();

    @PostMapping("/roles")
    byte[] createRole(@RequestBody Object body);

    @PutMapping("/roles/{id}")
    byte[] updateRole(@PathVariable("id") String id, @RequestBody Object body);

    @DeleteMapping("/roles/{id}")
    void deleteRole(@PathVariable("id") String id);

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

    // --- équipes (chantier D) ---
    //
    // L'API fusionnée du cœur, relayée telle quelle. Le BFF ne lit aucun de ces corps : les
    // `viewerCanEdit`, `viewerCanEditCompositions` et `viewerMemberId` sont calculés par le cœur
    // pour l'acteur transmis en `X-Actor-Id`, et le traversent sans être interprétés.

    @GetMapping("/teams")
    byte[] getTeams();

    @PostMapping("/teams")
    byte[] createTeam(@RequestBody Object body);

    /** Revendication des places laissées à un Riot ID, après liaison du compte. */
    @PostMapping("/teams/claim")
    byte[] claimTeams();

    @GetMapping("/teams/{teamId}")
    byte[] getTeam(@PathVariable("teamId") String teamId);

    @PutMapping("/teams/{teamId}")
    byte[] renameTeam(@PathVariable("teamId") String teamId, @RequestBody Object body);

    @DeleteMapping("/teams/{teamId}")
    void deleteTeam(@PathVariable("teamId") String teamId);

    @PostMapping("/teams/{teamId}/members")
    byte[] addTeamMember(@PathVariable("teamId") String teamId, @RequestBody Object body);

    @PutMapping("/teams/{teamId}/members/{memberId}")
    byte[] updateTeamMember(@PathVariable("teamId") String teamId,
                            @PathVariable("memberId") String memberId,
                            @RequestBody Object body);

    /**
     * Retirer un membre rend l'équipe entière, et non 204 : c'est le contrat du cœur, et il évite
     * au front un second appel pour redessiner l'effectif.
     */
    @DeleteMapping("/teams/{teamId}/members/{memberId}")
    byte[] removeTeamMember(@PathVariable("teamId") String teamId,
                            @PathVariable("memberId") String memberId);

    @GetMapping("/teams/{teamId}/compositions")
    byte[] getCompositions(@PathVariable("teamId") String teamId);

    @GetMapping("/teams/{teamId}/compositions/{compositionId}")
    byte[] getComposition(@PathVariable("teamId") String teamId,
                          @PathVariable("compositionId") String compositionId);

    @PostMapping("/teams/{teamId}/compositions")
    byte[] createComposition(@PathVariable("teamId") String teamId, @RequestBody Object body);

    @PutMapping("/teams/{teamId}/compositions/{compositionId}")
    byte[] updateComposition(@PathVariable("teamId") String teamId,
                             @PathVariable("compositionId") String compositionId,
                             @RequestBody Object body);

    @DeleteMapping("/teams/{teamId}/compositions/{compositionId}")
    void deleteComposition(@PathVariable("teamId") String teamId,
                           @PathVariable("compositionId") String compositionId);
}

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

    @PostMapping("/game-servers/{slug}/start")
    void startGameServer(@PathVariable("slug") String slug);

    @PostMapping("/game-servers/{slug}/stop")
    void stopGameServer(@PathVariable("slug") String slug);

    @GetMapping("/game-servers/games")
    byte[] getGames();

    @GetMapping("/users/by-discord/{discordId}")
    UserIdentityDto getIdentity(@PathVariable("discordId") String discordId,
                                @RequestParam(value = "discordUsername", required = false) String discordUsername,
                                @RequestParam(value = "avatarUrl", required = false) String avatarUrl);

    @GetMapping("/users")
    byte[] getUsers();

    @GetMapping("/ingest/load")
    byte[] getIngestLoad();

    @GetMapping("/me")
    byte[] getMe();

    @PutMapping("/me/display-name")
    byte[] updateMyDisplayName(@RequestBody Object body);

    @GetMapping("/users/me/riot-account")
    byte[] getMyRiotAccount();

    @PutMapping("/users/me/riot-account")
    byte[] linkMyRiotAccount(@RequestBody Object body);

    @GetMapping("/users/me/riot-account/suggestions")
    byte[] suggestRiotAccounts(@RequestParam("q") String query,
                               @RequestParam(value = "limit", required = false) Integer limit);

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

    @GetMapping("/deployments")
    byte[] getDeployments();

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

    @GetMapping("/teams")
    byte[] getTeams();

    @PostMapping("/teams")
    byte[] createTeam(@RequestBody Object body);

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

    @GetMapping("/teams/{teamId}/games/{matchId}/reviews")
    byte[] getGameReviews(@PathVariable("teamId") String teamId,
                          @PathVariable("matchId") String matchId);

    @PostMapping("/teams/{teamId}/games/{matchId}/reviews")
    byte[] createGameReview(@PathVariable("teamId") String teamId,
                            @PathVariable("matchId") String matchId,
                            @RequestBody Object body);

    @PutMapping("/teams/{teamId}/games/{matchId}/reviews/{reviewId}")
    byte[] updateGameReview(@PathVariable("teamId") String teamId,
                            @PathVariable("matchId") String matchId,
                            @PathVariable("reviewId") String reviewId,
                            @RequestBody Object body);

    @DeleteMapping("/teams/{teamId}/games/{matchId}/reviews/{reviewId}")
    void deleteGameReview(@PathVariable("teamId") String teamId,
                          @PathVariable("matchId") String matchId,
                          @PathVariable("reviewId") String reviewId);

    @GetMapping("/teams/{teamId}/champion-pool")
    byte[] getChampionPool(@PathVariable("teamId") String teamId,
                           @RequestParam(value = "masteryFloor", required = false) Integer masteryFloor);

    @PutMapping("/teams/{teamId}/champion-pool/roles/{role}")
    byte[] setPoolChampions(@PathVariable("teamId") String teamId,
                            @PathVariable("role") String role,
                            @RequestBody Object body);

    @PutMapping("/teams/{teamId}/champion-pool/mastery-floor")
    byte[] setPoolMasteryFloor(@PathVariable("teamId") String teamId,
                               @RequestBody Object body);

    @GetMapping("/teams/{teamId}/stats/players")
    byte[] getTeamPlayersStats(@PathVariable("teamId") String teamId,
                               @RequestParam(value = "days", required = false) Integer days,
                               @RequestParam(value = "champions", required = false) Integer champions);

    @GetMapping("/teams/{teamId}/stats/team")
    byte[] getTeamGamesStats(@PathVariable("teamId") String teamId,
                             @RequestParam(value = "days", required = false) Integer days,
                             @RequestParam(value = "limit", required = false) Integer limit);

    @GetMapping("/teams/{teamId}/stats/games/{matchId}")
    byte[] getTeamGameDetail(@PathVariable("teamId") String teamId,
                             @PathVariable("matchId") String matchId);

    @GetMapping("/teams/{teamId}/stats/refresh")
    byte[] getTeamStatsRefresh(@PathVariable("teamId") String teamId);

    @PostMapping("/teams/{teamId}/stats/refresh")
    byte[] refreshTeamStats(@PathVariable("teamId") String teamId);

    @GetMapping("/teams/{teamId}/stats/opposition")
    byte[] getTeamOpposition(@PathVariable("teamId") String teamId,
                             @RequestParam(value = "days", required = false) Integer days);

    @GetMapping("/me/stats")
    byte[] getMyStats(@RequestParam(value = "days", required = false) Integer days,
                      @RequestParam(value = "champions", required = false) Integer champions);
}

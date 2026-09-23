package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Contrôle grossier limité à isAuthenticated() : TEAM_VIEW, TEAM_EDIT et COMPOSITION_EDIT sont à portée
// d'équipe, le JWT ne les porte pas, le cœur tranche. Seule TEAM_CREATE (globale) se vérifie ici.
@RestController
@RequestMapping("/teams")
public class TeamProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public TeamProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> mine() {
        return gateway.call(UPSTREAM, core::getTeams);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('TEAM_CREATE')")
    public ResponseEntity<byte[]> create(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, HttpStatus.CREATED, () -> core.createTeam(gateway.parseBody(body)));
    }

    @PostMapping("/claim")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> claim() {
        return gateway.call(UPSTREAM, core::claimTeams);
    }

    @GetMapping("/{teamId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> byId(@PathVariable String teamId) {
        return gateway.call(UPSTREAM, () -> core.getTeam(teamId));
    }

    @PutMapping("/{teamId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> rename(@PathVariable String teamId,
                                         @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.renameTeam(teamId, gateway.parseBody(body)));
    }

    @DeleteMapping("/{teamId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> delete(@PathVariable String teamId) {
        return gateway.callVoid(UPSTREAM, () -> core.deleteTeam(teamId));
    }

    @PostMapping("/{teamId}/members")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> addMember(@PathVariable String teamId,
                                            @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, HttpStatus.CREATED,
                () -> core.addTeamMember(teamId, gateway.parseBody(body)));
    }

    @PutMapping("/{teamId}/members/{memberId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> updateMember(@PathVariable String teamId,
                                               @PathVariable String memberId,
                                               @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.updateTeamMember(teamId, memberId, gateway.parseBody(body)));
    }

    @DeleteMapping("/{teamId}/members/{memberId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> removeMember(@PathVariable String teamId, @PathVariable String memberId) {
        return gateway.call(UPSTREAM, () -> core.removeTeamMember(teamId, memberId));
    }

    @GetMapping("/{teamId}/champion-pool")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> championPool(@PathVariable String teamId,
                                               @RequestParam(required = false) Integer masteryFloor) {
        return gateway.call(UPSTREAM, () -> core.getChampionPool(teamId, masteryFloor));
    }

    @PutMapping("/{teamId}/champion-pool/roles/{role}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> setPoolChampions(@PathVariable String teamId,
                                                   @PathVariable String role,
                                                   @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.setPoolChampions(teamId, role, gateway.parseBody(body)));
    }

    @PutMapping("/{teamId}/champion-pool/mastery-floor")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> setPoolMasteryFloor(@PathVariable String teamId,
                                                      @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.setPoolMasteryFloor(teamId, gateway.parseBody(body)));
    }

    @GetMapping("/{teamId}/stats/players")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> playersStats(@PathVariable String teamId,
                                               @RequestParam(required = false) Integer days,
                                               @RequestParam(required = false) Integer champions) {
        return gateway.call(UPSTREAM, () -> core.getTeamPlayersStats(teamId, days, champions));
    }

    @GetMapping("/{teamId}/stats/team")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> teamStats(@PathVariable String teamId,
                                            @RequestParam(required = false) Integer days,
                                            @RequestParam(required = false) Integer limit) {
        return gateway.call(UPSTREAM, () -> core.getTeamGamesStats(teamId, days, limit));
    }

    @GetMapping("/{teamId}/games/{matchId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> gameReviews(@PathVariable String teamId,
                                              @PathVariable String matchId) {
        return gateway.call(UPSTREAM, () -> core.getGameReviews(teamId, matchId));
    }

    @PostMapping("/{teamId}/games/{matchId}/reviews")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> createGameReview(@PathVariable String teamId,
                                                   @PathVariable String matchId,
                                                   @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, HttpStatus.CREATED,
                () -> core.createGameReview(teamId, matchId, gateway.parseBody(body)));
    }

    @PutMapping("/{teamId}/games/{matchId}/reviews/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> updateGameReview(@PathVariable String teamId,
                                                   @PathVariable String matchId,
                                                   @PathVariable String reviewId,
                                                   @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM,
                () -> core.updateGameReview(teamId, matchId, reviewId, gateway.parseBody(body)));
    }

    @DeleteMapping("/{teamId}/games/{matchId}/reviews/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> deleteGameReview(@PathVariable String teamId,
                                                   @PathVariable String matchId,
                                                   @PathVariable String reviewId) {
        return gateway.callVoid(UPSTREAM,
                () -> core.deleteGameReview(teamId, matchId, reviewId));
    }
}

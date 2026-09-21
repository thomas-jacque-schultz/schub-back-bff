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

/**
 * Les équipes, servies au front. Tout part vers le cœur, qui porte le domaine depuis D.4.
 *
 * <h2>Où s'arrête le contrôle grossier, et pourquoi il s'arrête là</h2>
 *
 * <p>Le partage du plan §A.2 est net : contrôle grossier au BFF, fin dans le cœur. Encore
 * faut-il que le contrôle grossier soit <em>possible</em>. {@code TEAM_VIEW}, {@code TEAM_EDIT}
 * et {@code COMPOSITION_EDIT} sont des permissions <strong>à portée d'équipe</strong> : elles
 * sont accordées par l'appartenance à une équipe précise
 * ({@code TeamScopedAuthority} dans le cœur), pas par le rôle. Le JWT, lui, ne porte que les
 * permissions du rôle — il ne peut pas porter « membre de l'équipe 64f… ». Le BFF ne sait donc
 * pas, et ne peut pas savoir, qui est membre de quoi.</p>
 *
 * <p>Conséquence directe : exiger {@code TEAM_VIEW} à la porte refuserait à un capitaine l'accès
 * à sa propre équipe, puisque le rôle {@code VISITEUR} ne porte que {@code SERVER_VIEW} et
 * {@code TEAM_CREATE}. C'est exactement le piège que
 * {@link GameServerProxyController#start(String)} a désamorcé pour le démarrage d'un serveur :
 * le contrôle grossier s'y arrête à « c'est un membre connecté », le contrôle fin est dans le
 * cœur, seul à connaître les {@code admins}. Ici, la même règle donne
 * {@code isAuthenticated()} — à la différence des serveurs, il n'existe aucune permission
 * globale de lecture d'équipe dont on pourrait se servir comme filet.</p>
 *
 * <p><strong>La seule route qui porte une permission est la création</strong>, et c'est le cas
 * symétrique : {@code TEAM_CREATE} est globale, sans ressource — le cœur l'évalue lui aussi sans
 * équipe ({@code permissionEvaluator.require(actor, TEAM_CREATE, null)}). Elle se vérifie donc
 * ici sans rien deviner, et elle évite un aller-retour au cœur pour un refus certain.</p>
 *
 * <p>Ce que le BFF apporte quand même sur les autres routes : l'acteur. {@code X-Actor-Id} est
 * posé par {@code InternalSecretFeignConfig} depuis le contexte de sécurité, et c'est le secret
 * interne qui rend cette affirmation croyable. Sans le BFF, le cœur n'aurait personne à qui
 * appliquer son contrôle fin.</p>
 */
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

    /**
     * Les équipes de l'appelant.
     *
     * <p>Le cœur ne sert que les siennes — celles où il figure et celles qu'il a créées. Il n'y a
     * donc rien à filtrer ici, et rien non plus à exiger : un compte sans équipe reçoit une liste
     * vide, ce qui est une réponse, pas un refus.</p>
     */
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

    /**
     * Revendiquer les places qui attendaient son Riot ID.
     *
     * <p>Aucune permission : l'opération ne porte que sur l'appelant, et le cœur ne relie que ce
     * qui désigne déjà son compte. Idempotente, elle rend les équipes qui viennent de basculer —
     * donc une liste vide si rien n'attendait.</p>
     */
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

    /** Rend l'équipe mise à jour, pas 204 : c'est le contrat du cœur, on ne le réécrit pas. */
    @DeleteMapping("/{teamId}/members/{memberId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> removeMember(@PathVariable String teamId, @PathVariable String memberId) {
        return gateway.call(UPSTREAM, () -> core.removeTeamMember(teamId, memberId));
    }

    /**
     * Les trois lectures de panneau. {@code isAuthenticated()} et rien de plus : {@code TEAM_VIEW}
     * est à portée d'équipe, le jeton ne la porte pas, et c'est le cœur qui tranche.
     */
    @GetMapping("/{teamId}/champion-pool")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> championPool(@PathVariable String teamId,
                                               @RequestParam(required = false) Integer champions) {
        return gateway.call(UPSTREAM, () -> core.getChampionPool(teamId, champions));
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
}

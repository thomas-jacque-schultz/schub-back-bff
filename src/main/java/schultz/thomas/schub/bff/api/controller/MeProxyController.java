package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Le profil de l'appelant : le menu « Mon profil ».
 *
 * <h2>Pourquoi aucune permission nominale</h2>
 *
 * <p>{@link TeamProxyController} s'arrête à {@code isAuthenticated()} parce que le BFF ne
 * <em>peut pas</em> savoir qui est membre de quelle équipe. Ici la raison est différente et plus
 * forte : <strong>la ressource est le lecteur</strong>. Toutes ces routes sont sous
 * {@code /users/me}, il n'existe aucun chemin vers le profil de quelqu'un d'autre, et exiger une
 * permission reviendrait à pouvoir refuser à quelqu'un l'accès à son propre nom. C'est la même
 * forme que {@code POST /teams/claim} : une route sans second paramètre, donc rien à oublier de
 * contrôler.</p>
 *
 * <p>C'est aussi ce qui distingue ces routes de {@link UserProxyController}, qui sert l'écran
 * d'administration des comptes et exige {@code USER_VIEW} : lire <em>les</em> comptes est un
 * droit, lire <em>son</em> compte n'en est pas un.</p>
 *
 * <h2>Ce qui n'est pas proxifié : la déliaison</h2>
 *
 * <p>Le cœur porte un {@code DELETE /users/me/riot-account}. Il ne traverse pas le BFF, et c'est
 * une décision de produit, pas un oubli : délier laisserait les équipes qui référencent ce compte
 * dans un état incohérent. Le geste offert est le <em>changement</em>, qui remplace le lien sans
 * jamais le laisser vide. Ouvrir la route « au cas où » suffirait à ce que quelqu'un l'appelle.</p>
 */
@RestController
@RequestMapping("/users/me")
public class MeProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public MeProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    /**
     * Le profil complet : Discord, nom sur le site, rôle, compte Riot et avancement de l'ingest.
     *
     * <p>À ne pas confondre avec {@code GET /auth/me}, qui lit le <strong>jeton</strong> et dit
     * ce que le BFF appliquera. Celle-ci lit le cœur et dit ce qui est vrai : l'avatar, le nom
     * choisi et l'état Riot n'y sont pas, et ne peuvent pas y être.</p>
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> me() {
        return gateway.call(UPSTREAM, core::getMe);
    }

    @PutMapping("/display-name")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> updateDisplayName(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.updateMyDisplayName(gateway.parseBody(body)));
    }

    @GetMapping("/riot-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> riotAccount() {
        return gateway.call(UPSTREAM, core::getMyRiotAccount);
    }

    /**
     * Déclarer ou remplacer son Riot ID.
     *
     * <p>Idempotente : rejouer le même Riot ID relance la résolution du {@code puuid}, ce qui est
     * le « réessayer » dont l'écran a besoin quand le connecteur était éteint. Les refus du cœur
     * — 409 si le compte est déjà revendiqué, 400 si le Riot ID est malformé — traversent tels
     * quels, sinon le front ne peut plus distinguer les deux.</p>
     */
    @PutMapping("/riot-account")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> linkRiotAccount(@RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.linkMyRiotAccount(gateway.parseBody(body)));
    }

    /** Ce que coûte un changement de compte, avant de le décider. Lecture seule, sans effet. */
    @GetMapping("/riot-account/change-preview")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> previewRiotAccountChange(@RequestParam String riotId) {
        return gateway.call(UPSTREAM, () -> core.previewRiotAccountChange(riotId));
    }
}

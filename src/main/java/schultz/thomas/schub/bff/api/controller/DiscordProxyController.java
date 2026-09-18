package schultz.thomas.schub.bff.api.controller;

import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.ConnectorDiscordFeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Ce qui est réellement Discord — et c'est tout ce qui reste au connecteur côté front. */
@RestController
@RequestMapping("/discord")
public class DiscordProxyController {

    private static final String UPSTREAM = "schub-connector-discord";

    private final ConnectorDiscordFeignClient discord;
    private final UpstreamGateway gateway;

    public DiscordProxyController(ConnectorDiscordFeignClient discord, UpstreamGateway gateway) {
        this.discord = discord;
        this.gateway = gateway;
    }

    /**
     * La liste des salons du bot, derrière la même permission que l'abonnement : c'est la liste
     * dans laquelle on choisit, elle n'a d'usage que pour qui a le droit de choisir — et elle
     * révèle la composition des serveurs Discord où le bot est présent.
     */
    @GetMapping("/guilds/channels")
    @PreAuthorize("hasAuthority('DISCORD_CHANNEL_MANAGE')")
    public ResponseEntity<byte[]> guildsChannels() {
        return gateway.call(UPSTREAM, discord::getGuildsChannels);
    }

    /** Choix des salons notifiés. Rétabli en phase 4 : le front l'appelait dans le vide. */
    @PostMapping("/channels/subscribe")
    @PreAuthorize("hasAuthority('DISCORD_CHANNEL_MANAGE')")
    public ResponseEntity<byte[]> subscribeChannels(@RequestBody(required = false) byte[] body) {
        return gateway.callVoid(UPSTREAM, () -> discord.subscribeChannels(gateway.parseBody(body)));
    }
}

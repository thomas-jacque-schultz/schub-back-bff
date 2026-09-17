package schultz.thomas.schub.bff.data.client;

import schultz.thomas.schub.bff.config.InternalSecretFeignConfig;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Le connecteur Discord, et rien d'autre que Discord.
 *
 * <p>Depuis la phase 3 il ne détient plus aucun domaine : il ne sait parler que de guildes, de
 * salons et de messages. Tout ce qui concerne un serveur de jeu passe par
 * {@link CoreFeignClient}.</p>
 */
@FeignClient(name = "connectorDiscordClient", url = "${connector.discord.base-url}",
        configuration = InternalSecretFeignConfig.class)
public interface ConnectorDiscordFeignClient {

    @GetMapping("/discord/guilds/channels")
    byte[] getGuildsChannels();

    @PostMapping("/discord/channels/subscribe")
    void subscribeChannels(@RequestBody Object body);
}

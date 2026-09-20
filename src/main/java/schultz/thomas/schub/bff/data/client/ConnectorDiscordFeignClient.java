package schultz.thomas.schub.bff.data.client;

import schultz.thomas.schub.bff.config.InternalSecretFeignConfig;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import schultz.thomas.schub.bff.api.dto.DirectMessageAck;
import schultz.thomas.schub.bff.api.dto.DirectMessageRequest;

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

    /**
     * Écrit à une personne, en message privé — et à défaut dans un salon abonné.
     *
     * <p>Le repli est décidé par le connecteur, pas ici : lui seul sait si Discord a refusé.
     * La réponse dit par quel chemin c'est passé, et un repli mérite qu'on aille rouvrir les
     * MP du compte destinataire.</p>
     */
    @PostMapping("/discord/direct-messages")
    DirectMessageAck sendDirectMessage(@RequestBody DirectMessageRequest request);
}

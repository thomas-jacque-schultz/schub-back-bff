package schultz.thomas.schub.bff.data.client;

import schultz.thomas.schub.bff.config.InternalSecretFeignConfig;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import schultz.thomas.schub.bff.api.dto.DirectMessageAck;
import schultz.thomas.schub.bff.api.dto.DirectMessageRequest;

@FeignClient(name = "connectorDiscordClient", url = "${connector.discord.base-url}",
        configuration = InternalSecretFeignConfig.class)
public interface ConnectorDiscordFeignClient {

    @GetMapping("/discord/guilds/channels")
    byte[] getGuildsChannels();

    @PostMapping("/discord/channels/subscribe")
    void subscribeChannels(@RequestBody Object body);

    @PostMapping("/discord/direct-messages")
    DirectMessageAck sendDirectMessage(@RequestBody DirectMessageRequest request);
}

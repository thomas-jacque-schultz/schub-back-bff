package schultz.thomas.schub.bff.business.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import schultz.thomas.schub.bff.data.client.UserIdentityDto;

import java.util.Optional;

@Service
public class IdentityService {

    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);

    private final CoreFeignClient core;

    public IdentityService(CoreFeignClient core) {
        this.core = core;
    }

    public Optional<UserIdentityDto> identity(String discordId, String discordUsername) {
        return identity(discordId, discordUsername, null);
    }

    public Optional<UserIdentityDto> identity(String discordId, String discordUsername, String avatarUrl) {
        try {
            return Optional.ofNullable(core.getIdentity(discordId, discordUsername, avatarUrl));
        } catch (Exception ex) {
            log.warn("Identité indisponible pour l'acteur {} : {}", discordId, ex.getMessage());
            return Optional.empty();
        }
    }
}

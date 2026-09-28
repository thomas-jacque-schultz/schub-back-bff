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

    // Retrouve le compte par son identifiant Discord, ou le crée.
    public Optional<UserIdentityDto> discordLogin(String discordId, String discordUsername, String avatarUrl) {
        try {
            return Optional.ofNullable(core.getIdentity(discordId, discordUsername, avatarUrl));
        } catch (Exception ex) {
            log.warn("Identité indisponible pour le compte Discord {} : {}", discordId, ex.getMessage());
            return Optional.empty();
        }
    }

    // Au plus une fois par heure et par session : à la connexion et au renouvellement du jeton.
    public void recordActivity(String userId, String app) {
        try {
            core.recordActivity(userId, app == null ? "schub" : app);
        } catch (Exception ex) {
            log.debug("Activité non enregistrée pour {} : {}", userId, ex.getMessage());
        }
    }

    public Optional<UserIdentityDto> byUserId(String userId) {
        try {
            return Optional.ofNullable(core.getIdentityById(userId));
        } catch (Exception ex) {
            log.warn("Identité indisponible pour l'utilisateur {} : {}", userId, ex.getMessage());
            return Optional.empty();
        }
    }
}

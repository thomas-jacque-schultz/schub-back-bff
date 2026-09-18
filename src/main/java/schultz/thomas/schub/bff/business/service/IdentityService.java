package schultz.thomas.schub.bff.business.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;
import schultz.thomas.schub.bff.data.client.UserIdentityDto;

import java.util.Optional;

/**
 * Qui est l'utilisateur, et que peut-il faire — la question posée au cœur.
 *
 * <p>Le BFF ne détient aucune identité : il la demande. C'est la contrepartie de la décision du
 * 18-09 (l'identité vit dans le cœur) et c'est ce qui garantit qu'il n'existe qu'une seule
 * réponse à « quels sont ses droits ? ».</p>
 */
@Service
public class IdentityService {

    private static final Logger log = LoggerFactory.getLogger(IdentityService.class);

    private final CoreFeignClient core;

    public IdentityService(CoreFeignClient core) {
        this.core = core;
    }

    /**
     * L'identité du compte Discord, créée au rôle {@code VISITEUR} si elle n'existe pas encore.
     *
     * <p>{@link Optional#empty()} quand le cœur est injoignable — et c'est un cas que l'appelant
     * doit traiter, pas ignorer : à la connexion il faut échouer, à la réémission glissante il
     * faut au contraire laisser vivre le jeton en cours plutôt que de déconnecter quelqu'un
     * pour une indisponibilité passagère.</p>
     */
    public Optional<UserIdentityDto> identity(String discordId, String discordUsername) {
        return identity(discordId, discordUsername, null);
    }

    /**
     * Variante de la connexion Discord : le pseudo et l'avatar viennent d'être lus dans
     * {@code /users/@me}, et le cœur les rafraîchit au passage.
     *
     * <p>C'est le BFF qui les fournit parce qu'il est le seul à avoir parlé à Discord — le cœur
     * ne connaît pas cette API et ne doit pas l'apprendre (plan §1).</p>
     */
    public Optional<UserIdentityDto> identity(String discordId, String discordUsername, String avatarUrl) {
        try {
            return Optional.ofNullable(core.getIdentity(discordId, discordUsername, avatarUrl));
        } catch (Exception ex) {
            log.warn("Identité indisponible pour l'acteur {} : {}", discordId, ex.getMessage());
            return Optional.empty();
        }
    }
}

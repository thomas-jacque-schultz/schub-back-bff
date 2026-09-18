package schultz.thomas.schub.bff.data.client;

import java.util.List;

/**
 * L'identité telle que le cœur la rend.
 *
 * <p>C'est la <strong>seule</strong> forme du domaine que le BFF comprend. Tout le reste
 * traverse en octets bruts, exprès : une évolution du contrat du cœur ne doit pas obliger à
 * toucher le BFF. Ici il n'y a pas le choix — il faut lire les permissions pour composer le
 * jeton et pour refuser une route.</p>
 *
 * <p>Les champs inconnus sont ignorés (Spring Boot désactive
 * {@code FAIL_ON_UNKNOWN_PROPERTIES}) : le cœur peut enrichir le profil — le lien Riot du
 * chantier D, par exemple — sans rien casser ici.</p>
 */
public record UserIdentityDto(Profile user, List<String> permissions) {

    public record Profile(
            String id,
            String discordId,
            String discordUsername,
            String avatarUrl,
            String roleId,
            String roleName
    ) {
    }

    public List<String> permissionsOrEmpty() {
        return permissions == null ? List.of() : permissions;
    }
}

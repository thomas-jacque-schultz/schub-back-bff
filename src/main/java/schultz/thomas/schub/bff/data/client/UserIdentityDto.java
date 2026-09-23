package schultz.thomas.schub.bff.data.client;

import java.util.List;

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

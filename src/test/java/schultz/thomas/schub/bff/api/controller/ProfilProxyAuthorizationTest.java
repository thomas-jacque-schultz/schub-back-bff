package schultz.thomas.schub.bff.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProfilProxyAuthorizationTest {

    private static final List<String> PROFIL_DANS_USERS =
            List.of("myRiotAccount", "linkMyRiotAccount", "suggestRiotAccounts");

    private static final List<String> ADMINISTRATION = List.of("all", "assignRole");

    @Test
    @DisplayName("aucune route de profil n'exige de permission nominale : la ressource est le lecteur")
    void leProfilNExigeAucunePermissionNominale() {
        routes(MeProxyController.class).forEach(this::verifierAuthentificationSeule);

        routes(UserProxyController.class).stream()
                .filter(route -> PROFIL_DANS_USERS.contains(route.getName()))
                .forEach(this::verifierAuthentificationSeule);
    }

    @Test
    @DisplayName("les routes d'administration des comptes gardent leur permission")
    void administrationGardeSaPermission() {
        List<Method> administration = routes(UserProxyController.class).stream()
                .filter(route -> ADMINISTRATION.contains(route.getName()))
                .toList();

        assertThat(administration).hasSameSizeAs(ADMINISTRATION);
        administration.forEach(route -> assertThat(regle(route))
                .as("%s sert les comptes des autres : elle exige une permission", route.getName())
                .startsWith("hasAuthority("));
    }

    @Test
    @DisplayName("toute route de profil porte une règle explicite")
    void touteRoutePorteUneRegle() {
        routes(MeProxyController.class).forEach(this::verifierPresence);
        routes(UserProxyController.class).forEach(this::verifierPresence);
    }

    @Test
    @DisplayName("le BFF n'expose aucune route de déliaison du compte Riot")
    void aucuneRouteDeDeliaison() {
        assertThat(routes(UserProxyController.class))
                .filteredOn(route -> route.getName().toLowerCase().contains("riot"))
                .as("seul le changement est offert, jamais la déliaison")
                .noneMatch(route -> route.isAnnotationPresent(DeleteMapping.class));
    }

    private void verifierAuthentificationSeule(Method route) {
        assertThat(regle(route))
                .as("%s doit s'arrêter à l'authentification", route.getName())
                .isEqualTo("isAuthenticated()");
    }

    private void verifierPresence(Method route) {
        assertThat(regle(route))
                .as("la route %s n'annonce aucune règle d'autorisation", route.getName())
                .isNotBlank();
    }

    private String regle(Method route) {
        PreAuthorize annotation = route.getAnnotation(PreAuthorize.class);
        return annotation == null ? "" : annotation.value();
    }

    private List<Method> routes(Class<?> controleur) {
        List<Method> routes = Arrays.stream(controleur.getDeclaredMethods())
                .filter(methode -> Arrays.stream(methode.getAnnotations())
                        .anyMatch(annotation -> annotation.annotationType().isAnnotationPresent(RequestMapping.class)))
                .toList();

        assertThat(routes).as("aucune route trouvée sur %s", controleur.getSimpleName()).isNotEmpty();
        return routes;
    }
}

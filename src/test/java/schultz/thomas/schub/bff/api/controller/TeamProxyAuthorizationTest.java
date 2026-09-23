package schultz.thomas.schub.bff.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TeamProxyAuthorizationTest {

    private static final List<String> A_PORTEE_D_EQUIPE = List.of("TEAM_VIEW", "TEAM_EDIT", "COMPOSITION_EDIT");

    @Test
    @DisplayName("aucune route d'équipe n'exige une permission à portée d'équipe")
    void aucunePermissionAPorteeDEquipeALaPorte() {
        routes(TeamProxyController.class).forEach(this::verifierPortee);
        routes(CompositionProxyController.class).forEach(this::verifierPortee);
    }

    @Test
    @DisplayName("toute route d'équipe porte une règle explicite")
    void touteRoutePorteUneRegle() {
        routes(TeamProxyController.class).forEach(this::verifierPresence);
        routes(CompositionProxyController.class).forEach(this::verifierPresence);
    }

    @Test
    @DisplayName("la création d'équipe exige TEAM_CREATE, elle seule le peut")
    void laCreationExigeTeamCreate() throws NoSuchMethodException {
        Method creation = TeamProxyController.class.getMethod("create", byte[].class);

        assertThat(regle(creation)).isEqualTo("hasAuthority('TEAM_CREATE')");
    }

    private void verifierPortee(Method route) {
        String regle = regle(route);
        assertThat(A_PORTEE_D_EQUIPE)
                .as("%s ne doit exiger aucune permission à portée d'équipe, or elle exige %s",
                        route.getName(), regle)
                .noneMatch(regle::contains);
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

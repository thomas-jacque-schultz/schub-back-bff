package schultz.thomas.schub.bff.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La politique d'autorisation des routes d'équipe, verrouillée.
 *
 * <p>Ce test ne vérifie pas que Spring applique {@code @PreAuthorize} — c'est le travail du
 * framework — mais <strong>ce que les annotations disent</strong>. C'est une décision de
 * conception, pas un détail d'implémentation : la prochaine main qui écrira
 * {@code hasAuthority('TEAM_EDIT')} sur une route d'équipe le fera de bonne foi, et l'écran d'un
 * capitaine cessera de fonctionner sans qu'aucune erreur ne soit levée nulle part — le BFF
 * refusera avant que le cœur, seul à savoir, ait pu accorder.</p>
 *
 * <p>Un test d'intégration serait plus fidèle mais moins précis : il dirait « 403 », là où c'est
 * l'intention qu'on veut figer. Les deux invariants tenus ici :</p>
 *
 * <ol>
 *   <li>aucune route d'équipe n'exige une permission <em>à portée d'équipe</em>
 *       ({@code TEAM_VIEW}, {@code TEAM_EDIT}, {@code COMPOSITION_EDIT}) — le JWT ne peut pas les
 *       porter, elles s'évaluent avec l'équipe en ressource, donc dans le cœur ;</li>
 *   <li>aucune route n'est laissée <em>sans</em> annotation : le filet
 *       {@code anyRequest().authenticated()} existe, mais une route muette ne dit pas si son
 *       silence est réfléchi (plan §A.0).</li>
 * </ol>
 */
class TeamProxyAuthorizationTest {

    /** Ce qu'un JWT ne peut pas porter : ces permissions viennent de l'appartenance à UNE équipe. */
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

    /**
     * La création est le cas symétrique : {@code TEAM_CREATE} est globale et sans ressource, le
     * cœur l'évalue de la même façon. Elle se vérifie donc à la porte, et c'est la seule.
     */
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

    /** Les méthodes publiques portant un mapping — donc les routes, et rien d'autre. */
    private List<Method> routes(Class<?> controleur) {
        List<Method> routes = Arrays.stream(controleur.getDeclaredMethods())
                .filter(methode -> Arrays.stream(methode.getAnnotations())
                        .anyMatch(annotation -> annotation.annotationType().isAnnotationPresent(RequestMapping.class)))
                .toList();

        assertThat(routes).as("aucune route trouvée sur %s", controleur.getSimpleName()).isNotEmpty();
        return routes;
    }
}

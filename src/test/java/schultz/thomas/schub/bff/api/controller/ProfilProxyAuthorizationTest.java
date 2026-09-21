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
 * La politique d'autorisation du profil, verrouillée — même forme que
 * {@link TeamProxyAuthorizationTest}, autre raison.
 *
 * <p>Pour les équipes, l'invariant est que le BFF <em>ne peut pas</em> juger. Ici, il pourrait :
 * rien n'empêcherait d'écrire {@code hasAuthority('USER_VIEW')} sur {@code GET /users/me}, et ce
 * serait fait de bonne foi, par symétrie avec l'écran d'administration des comptes. L'effet
 * serait qu'un compte tout juste créé au rôle {@code VISITEUR} ne pourrait plus voir son propre
 * profil, ni lier son compte Riot — c'est-à-dire ne pourrait rien faire du site.</p>
 *
 * <p>Le second invariant tient la décision produit : <strong>le BFF n'ouvre pas de route de
 * déliaison</strong>. Le cœur en porte une ; l'exposer laisserait les équipes qui référencent le
 * compte sans rien pour les rattacher.</p>
 */
class ProfilProxyAuthorizationTest {

    @Test
    @DisplayName("aucune route de profil n'exige de permission nominale : la ressource est le lecteur")
    void leProfilNExigeAucunePermissionNominale() {
        routes(MeProxyController.class).forEach(this::verifierAuthentificationSeule);
        routes(RiotAccountProxyController.class).forEach(this::verifierAuthentificationSeule);
    }

    @Test
    @DisplayName("toute route de profil porte une règle explicite")
    void touteRoutePorteUneRegle() {
        routes(MeProxyController.class).forEach(this::verifierPresence);
        routes(RiotAccountProxyController.class).forEach(this::verifierPresence);
    }

    @Test
    @DisplayName("le BFF n'expose aucune route de déliaison du compte Riot")
    void aucuneRouteDeDeliaison() {
        assertThat(routes(MeProxyController.class))
                .as("délier laisserait les équipes incohérentes : seul le changement est offert")
                .noneMatch(route -> route.isAnnotationPresent(
                        org.springframework.web.bind.annotation.DeleteMapping.class));
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

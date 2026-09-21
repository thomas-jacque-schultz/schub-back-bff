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
 * Ce que les routes de serveur exigent, et pourquoi elles peuvent l'exiger.
 *
 * <p>Démarrer et arrêter demandaient {@code SERVER_VIEW} tant qu'un compte pouvait tenir ce droit
 * d'une liste d'administrateurs par serveur, que le BFF ne connaît pas. Cette liste n'existe
 * plus : les deux permissions viennent du rôle, le jeton les porte, et le refus se fait ici. Les
 * remettre à {@code SERVER_VIEW} rouvrirait le démarrage à tout compte connecté.</p>
 */
class GameServerProxyAuthorizationTest {

    @Test
    @DisplayName("démarrer et arrêter exigent leur propre permission, pas SERVER_VIEW")
    void piloterExigeSaPermission() throws NoSuchMethodException {
        assertThat(regle(GameServerProxyController.class.getMethod("start", String.class)))
                .isEqualTo("hasAuthority('SERVER_START')");
        assertThat(regle(GameServerProxyController.class.getMethod("stop", String.class)))
                .isEqualTo("hasAuthority('SERVER_STOP')");
    }

    /** {@code publicStatus} est la vue sans compte : son absence de règle est la règle. */
    @Test
    @DisplayName("toute route de serveur porte une règle explicite, sauf la vue publique")
    void touteRoutePorteUneRegle() {
        routes(GameServerProxyController.class).stream()
                .filter(route -> !"publicStatus".equals(route.getName()))
                .forEach(route -> assertThat(regle(route))
                        .as("la route %s n'annonce aucune règle d'autorisation", route.getName())
                        .isNotBlank());
    }

    private String regle(Method route) {
        PreAuthorize annotation = route.getAnnotation(PreAuthorize.class);
        return annotation == null ? "" : annotation.value();
    }

    private List<Method> routes(Class<?> controleur) {
        return Arrays.stream(controleur.getDeclaredMethods())
                .filter(methode -> Arrays.stream(methode.getAnnotations())
                        .anyMatch(annotation -> annotation.annotationType().isAnnotationPresent(RequestMapping.class)))
                .toList();
    }
}

package schultz.thomas.schub.bff.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import schultz.thomas.schub.bff.data.client.CoreFeignClient;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class MyStatsRouteTest {

    @Test
    @DisplayName("Aucune route du BFF ne mène aux statistiques de quelqu'un d'autre")
    void aucuneRouteCiblee() {
        assertThat(cheminsDeLecture(CoreFeignClient.class))
                .filteredOn(chemin -> chemin.contains("stats"))
                // {matchId} : le cœur ne le cherche que parmi les parties d'équipe de {teamId}.
                .allSatisfy(chemin -> assertThat(chemin.replace("{teamId}", "").replace("{matchId}", ""))
                        .as("une route de statistiques ne désigne que l'équipe ou l'une de ses parties, jamais un joueur")
                        .doesNotContain("{"));
        assertThat(cheminsDeLecture(MeProxyController.class))
                .noneMatch(chemin -> chemin.contains("{"));
    }

    @Test
    @DisplayName("La route de mes stats ne prend aucun paramètre de chemin")
    void aucuneCiblePossible() throws NoSuchMethodException {
        Method route = MeProxyController.class.getMethod("stats", Integer.class, Integer.class, Integer.class);

        assertThat(Arrays.stream(route.getParameters())
                .anyMatch(parametre -> parametre.isAnnotationPresent(PathVariable.class)))
                .isFalse();
    }

    private static java.util.List<String> cheminsDeLecture(Class<?> type) {
        java.util.List<String> chemins = Arrays.stream(type.getDeclaredMethods())
                .map(methode -> methode.getAnnotation(GetMapping.class))
                .filter(java.util.Objects::nonNull)
                .flatMap(mapping -> Arrays.stream(mapping.value()))
                .toList();
        assertThat(chemins).as("aucune route trouvée sur %s", type.getSimpleName()).isNotEmpty();
        return chemins;
    }
}

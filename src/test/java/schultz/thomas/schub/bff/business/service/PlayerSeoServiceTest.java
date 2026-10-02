package schultz.thomas.schub.bff.business.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlayerSeoServiceTest {

    @Test
    @DisplayName("le head et le body d'une page ne lisent le cœur qu'une fois, sans maîtrises")
    void uneLecturePourDeuxFragments() {
        CoreFeignClient core = mock(CoreFeignClient.class);
        when(core.getPlayer("Le Nom-EUW", null, null, 30, false, false)).thenReturn("""
                {"gameName":"Le Nom","tagLine":"EUW","slug":"Le Nom-EUW","rankings":[],
                 "stats":{"overall":{"games":12,"winRate":0.5,"kda":2.0},"champions":[{"label":"Ahri"}]}}
                """.getBytes(StandardCharsets.UTF_8));
        PlayerSeoService seo = new PlayerSeoService(core, new ObjectMapper());

        assertThat(seo.lookup("Le Nom-EUW").summary()).isPresent();
        assertThat(seo.summary("Le Nom-EUW").orElseThrow().champions()).containsExactly("Ahri");

        verify(core, times(1)).getPlayer("Le Nom-EUW", null, null, 30, false, false);
    }
}

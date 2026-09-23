package schultz.thomas.schub.bff.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class IngestProxyAuthorizationTest {

    @Test
    @DisplayName("voir la collecte exige INGEST_VIEW, la basculer exige INGEST_MANAGE")
    void droitsDeLaCollecte() throws NoSuchMethodException {
        assertThat(regle("load")).isEqualTo("hasAuthority('INGEST_VIEW')");
        assertThat(regle("crawler")).isEqualTo("hasAuthority('INGEST_VIEW')");
        assertThat(IngestLoadProxyController.class.getMethod("toggleCrawler", byte[].class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('INGEST_MANAGE')");
    }

    private static String regle(String route) throws NoSuchMethodException {
        return IngestLoadProxyController.class.getMethod(route).getAnnotation(PreAuthorize.class).value();
    }
}

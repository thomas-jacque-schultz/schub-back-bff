package schultz.thomas.schub.bff.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class QueueKillerProxyAuthorizationTest {

    @Test
    @DisplayName("la présence et les salons de la premade demandent une session, aucune permission : un VISITEUR a une équipe")
    void sessionSansPermission() {
        assertThat(Arrays.stream(QueueKillerProxyController.class.getDeclaredMethods())
                .filter(methode -> methode.isAnnotationPresent(PreAuthorize.class)))
                .isEmpty();
    }
}

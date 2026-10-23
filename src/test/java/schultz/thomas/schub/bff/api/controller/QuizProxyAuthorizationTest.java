package schultz.thomas.schub.bff.api.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;

class QuizProxyAuthorizationTest {

    @Test
    @DisplayName("la banque active se lit sans compte ; la banque complète, l'écriture et la suppression exigent QUIZ_QUESTION_EDIT")
    void droitsDeLaBanque() throws NoSuchMethodException {
        assertThat(QuizProxyController.class.getMethod("active").getAnnotation(PreAuthorize.class)).isNull();
        assertThat(QuizProxyController.class.getMethod("all").getAnnotation(PreAuthorize.class).value())
                .isEqualTo("hasAuthority('QUIZ_QUESTION_EDIT')");
        assertThat(QuizProxyController.class.getMethod("save", String.class, byte[].class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('QUIZ_QUESTION_EDIT')");
        assertThat(QuizProxyController.class.getMethod("delete", String.class)
                .getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('QUIZ_QUESTION_EDIT')");
    }
}

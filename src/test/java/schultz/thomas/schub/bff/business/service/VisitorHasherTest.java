package schultz.thomas.schub.bff.business.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VisitorHasherTest {

    @Test
    @DisplayName("Même personne le même jour : même hachage ; il ne contient ni l'adresse ni le navigateur")
    void stableDansLaJournee() {
        VisitorHasher hasher = new VisitorHasher();

        String premier = hasher.hash("203.0.113.7", "Firefox");

        assertThat(hasher.hash("203.0.113.7", "Firefox")).isEqualTo(premier);
        assertThat(hasher.hash("203.0.113.8", "Firefox")).isNotEqualTo(premier);
        assertThat(premier).hasSize(32).doesNotContain("203");
    }

    @Test
    @DisplayName("Deux instances n'ont pas le même sel : un hachage ne se recalcule pas ailleurs")
    void selPropreAChaqueInstance() {
        assertThat(new VisitorHasher().hash("203.0.113.7", "Firefox"))
                .isNotEqualTo(new VisitorHasher().hash("203.0.113.7", "Firefox"));
    }
}

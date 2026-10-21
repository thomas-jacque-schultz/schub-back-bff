package schultz.thomas.schub.bff.business.service;

import java.util.Arrays;

// Un sujet absent ou inconnu devient « Autre » : une page restée en cache n'envoie pas de sujet.
public enum ContactSubject {
    ACCOUNT("Problème de compte"),
    WRONG_STATS("Stats fausses"),
    FEATURE("Nouvelle fonctionnalité"),
    MINI_GAMES("Mini-jeux"),
    OTHER("Autre");

    private final String label;

    ContactSubject(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static ContactSubject of(String code) {
        return Arrays.stream(values())
                .filter(subject -> subject.name().equals(code))
                .findFirst()
                .orElse(OTHER);
    }
}

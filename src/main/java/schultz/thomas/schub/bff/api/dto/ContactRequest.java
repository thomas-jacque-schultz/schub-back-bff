package schultz.thomas.schub.bff.api.dto;

/**
 * Ce qu'un visiteur envoie depuis le formulaire public.
 *
 * @param name           le nom qu'il donne — jamais vérifié, et ce n'est pas le sujet
 * @param email          son adresse, pour lui répondre
 * @param message        le message
 * @param website        le champ leurre : **doit être vide**. Rempli, la requête vient d'un robot
 * @param turnstileToken le jeton Turnstile, quand le rempart est actif
 */
public record ContactRequest(
        String name,
        String email,
        String message,
        String website,
        String turnstileToken
) {
}

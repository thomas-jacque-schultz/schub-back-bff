package schultz.thomas.schub.bff.api.dto;

/**
 * La réponse au visiteur.
 *
 * <p>Volontairement pauvre. Elle ne dit pas <em>par quel chemin</em> le message est passé, ni
 * si le message privé a échoué au profit du salon de repli : ce sont des détails d'exploitation,
 * qui vivent dans les journaux. Les exposer renseignerait un robot sur l'effet de ses essais.</p>
 */
public record ContactResponse(boolean delivered) {
}

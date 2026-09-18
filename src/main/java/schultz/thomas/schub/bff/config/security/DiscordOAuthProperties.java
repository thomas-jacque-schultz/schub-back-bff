package schultz.thomas.schub.bff.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Le paramétrage de la connexion Discord.
 *
 * <p><strong>Le scope n'est pas ici, et c'est délibéré</strong> : il est figé dans
 * {@link schultz.thomas.schub.bff.business.service.DiscordOAuthService}, à {@code identify} seul.
 * Une variable d'environnement mal réglée pourrait sinon demander {@code email} ou {@code guilds}
 * — on ne demande pas ce dont on n'a pas besoin, et ce n'est pas une décision d'exploitation.</p>
 *
 * <p>{@code clientSecret} ne doit apparaître nulle part ailleurs : ni dans un fichier versionné,
 * ni dans un journal, même tronqué. Il arrive par
 * {@code spring.config.import=optional:configtree:/run/secrets/} ou par l'environnement, et il ne
 * ressort pas. Aucun {@code toString()} n'est fourni pour ce record par prudence — les records en
 * génèrent un qui imprime tous les composants, d'où la redéfinition ci-dessous.</p>
 */
@ConfigurationProperties(prefix = "discord.oauth")
public record DiscordOAuthProperties(
        String clientId,
        String clientSecret,
        /** Doit être déclarée à l'identique dans le portail développeur Discord, au caractère près. */
        String redirectUri,
        String authorizationUri,
        String tokenUri,
        String userInfoUri,
        /** Où renvoyer le navigateur une fois le cookie posé. Chemin relatif : on ne redirige pas hors du site. */
        String postLoginRedirect,
        /** PKCE S256. Coupable par configuration au cas où le fournisseur refuserait le paramètre. */
        boolean pkceEnabled
) {

    /** Vrai quand la connexion Discord est réellement configurée — sinon la route répond 503. */
    public boolean configured() {
        return clientId != null && !clientId.isBlank()
                && clientSecret != null && !clientSecret.isBlank()
                && redirectUri != null && !redirectUri.isBlank();
    }

    /**
     * Sans cette redéfinition, un {@code log.debug("{}", properties)} imprimerait le secret :
     * le {@code toString()} par défaut d'un record liste tous ses composants.
     */
    @Override
    public String toString() {
        return "DiscordOAuthProperties[clientId=" + clientId + ", redirectUri=" + redirectUri
                + ", pkceEnabled=" + pkceEnabled + ", clientSecret=(masqué)]";
    }
}

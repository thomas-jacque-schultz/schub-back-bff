package schultz.thomas.schub.bff.config.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * La durée de session et la fraîcheur des droits sont deux réglages distincts.
 * Les confondre obligeait à choisir entre déconnecter un utilisateur actif et figer ses droits.
 */
@ConfigurationProperties(prefix = "auth.jwt")
public record JwtProperties(String secret, long expirationSeconds, long renewAfterSeconds) {
}

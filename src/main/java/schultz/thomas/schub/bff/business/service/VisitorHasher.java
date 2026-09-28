package schultz.thomas.schub.bff.business.service;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;

// Compte les personnes distinctes qui cherchent un joueur, sans cookie : hachage de l'adresse et du navigateur avec un
// sel tiré chaque jour et gardé en mémoire seulement. Le sel oublié, le hachage ne remonte plus à personne.
@Component
public class VisitorHasher {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    private final SecureRandom aleatoire = new SecureRandom();
    private volatile Sel sel;

    private record Sel(LocalDate jour, byte[] octets) {
    }

    public String hash(String address, String userAgent) {
        byte[] octets = selDu(LocalDate.now(PARIS));
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            sha.update(octets);
            sha.update(String.valueOf(address).getBytes(StandardCharsets.UTF_8));
            sha.update((byte) 0);
            sha.update(String.valueOf(userAgent).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(sha.digest(), 0, 16);
        } catch (NoSuchAlgorithmException absent) {
            throw new IllegalStateException(absent);
        }
    }

    private synchronized byte[] selDu(LocalDate jour) {
        Sel courant = sel;
        if (courant == null || !courant.jour().equals(jour)) {
            byte[] octets = new byte[32];
            aleatoire.nextBytes(octets);
            courant = new Sel(jour, octets);
            sel = courant;
        }
        return courant.octets();
    }
}

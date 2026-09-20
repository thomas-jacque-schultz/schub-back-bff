package schultz.thomas.schub.bff.business.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import schultz.thomas.schub.bff.config.ContactProperties;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La limitation de débit du formulaire de contact — **la couche qui ne se contourne pas**.
 *
 * <p>Le champ leurre s'évite en lisant le HTML ; Turnstile s'évite en payant un service de
 * résolution. Celle-ci, non : elle compte ce qui arrive vraiment, et elle est du côté serveur.
 * C'est pour ça qu'elle est la seule des trois à n'avoir aucun interrupteur.</p>
 *
 * <h2>Deux compteurs, pas un</h2>
 *
 * <p>Par IP, parce que c'est ce qui arrête un robot isolé. Et globalement, parce qu'un botnet
 * change d'adresse à chaque requête et rendrait le premier compteur décoratif. Le plafond global
 * est haut : il n'existe pas pour filtrer, il existe pour qu'une attaque distribuée coûte une
 * journée de formulaire indisponible plutôt qu'une messagerie noyée.</p>
 *
 * <h2>Ce que cette implémentation ne fait pas, et pourquoi c'est acceptable</h2>
 *
 * <p>Le compte est <strong>en mémoire, donc par instance</strong>. Deux répliques du BFF
 * laisseraient passer le double. Le BFF tourne aujourd'hui en exemplaire unique, et le jour où ce
 * ne sera plus vrai, la bonne réponse sera un compteur partagé — pas de faire semblant ici. Le
 * plus important est ailleurs : <strong>Cloudflare est devant</strong>, et c'est lui qui absorbe
 * le volume. Ce limiteur est la dernière porte, pas la première.</p>
 */
@Component
public class ContactRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ContactRateLimiter.class);
    private static final Duration WINDOW = Duration.ofHours(1);

    /**
     * Au-delà, on vide la table des adresses inactives.
     *
     * <p>Sans ce plafond, une attaque distribuée ferait grossir la table jusqu'à épuiser la
     * mémoire du service — le limiteur deviendrait lui-même le vecteur de la panne qu'il est
     * censé prévenir.</p>
     */
    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final ContactProperties properties;
    private final Map<String, Deque<Instant>> perAddress = new ConcurrentHashMap<>();
    private final Deque<Instant> global = new ArrayDeque<>();

    public ContactRateLimiter(ContactProperties properties) {
        this.properties = properties;
    }

    /**
     * Enregistre une tentative et dit si elle est acceptée.
     *
     * <p>L'appel <strong>compte la tentative même quand il la refuse</strong> : sinon un robot
     * qui insiste verrait sa fenêtre glisser et finirait par passer. Insister doit prolonger le
     * refus, pas le raccourcir.</p>
     */
    public synchronized boolean tryAcquire(String address) {
        Instant now = Instant.now();
        Instant floor = now.minus(WINDOW);

        purge(global, floor);
        if (global.size() >= properties.globalPerHour()) {
            log.warn("Plafond global du formulaire de contact atteint ({} par heure)", properties.globalPerHour());
            return false;
        }

        if (perAddress.size() > MAX_TRACKED_ADDRESSES) {
            perAddress.entrySet().removeIf(entry -> {
                purge(entry.getValue(), floor);
                return entry.getValue().isEmpty();
            });
        }

        Deque<Instant> attempts = perAddress.computeIfAbsent(address, key -> new ArrayDeque<>());
        purge(attempts, floor);

        attempts.addLast(now);
        if (attempts.size() > properties.perIpPerHour()) {
            log.info("Formulaire de contact refusé pour {} : {} tentatives dans l'heure",
                    address, attempts.size());
            return false;
        }

        global.addLast(now);
        return true;
    }

    private static void purge(Deque<Instant> attempts, Instant floor) {
        while (!attempts.isEmpty() && attempts.peekFirst().isBefore(floor)) {
            attempts.removeFirst();
        }
    }
}

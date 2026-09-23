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

@Component
public class ContactRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(ContactRateLimiter.class);
    private static final Duration WINDOW = Duration.ofHours(1);

    private static final int MAX_TRACKED_ADDRESSES = 10_000;

    private final ContactProperties properties;
    private final Map<String, Deque<Instant>> perAddress = new ConcurrentHashMap<>();
    private final Deque<Instant> global = new ArrayDeque<>();

    public ContactRateLimiter(ContactProperties properties) {
        this.properties = properties;
    }

    // Une tentative refusée est comptée aussi : insister prolonge le refus.
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

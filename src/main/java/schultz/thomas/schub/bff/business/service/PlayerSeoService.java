package schultz.thomas.schub.bff.business.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

// Ce qu'un robot doit lire sur la page d'un joueur, sans exécuter de JavaScript.
@Service
public class PlayerSeoService {

    private static final Logger log = LoggerFactory.getLogger(PlayerSeoService.class);
    private static final int CHAMPIONS = 3;
    // nginx demande le head et le body d'une page en même temps : une seule lecture du cœur pour les deux.
    private static final Duration FRAICHEUR = Duration.ofMinutes(1);

    private final CoreFeignClient core;
    private final ObjectMapper objectMapper;
    private final Map<String, Lecture> lectures = new ConcurrentHashMap<>();

    private record Lecture(CompletableFuture<Lookup> lookup, Instant depuis) {
    }

    public PlayerSeoService(CoreFeignClient core, ObjectMapper objectMapper) {
        this.core = core;
        this.objectMapper = objectMapper;
    }

    public record Rank(String queue, String tier, String division, int leaguePoints, int wins, int losses) {
    }

    public record Summary(String gameName, String tagLine, String slug, Rank solo, long games,
                          Double winRate, Double kda, List<String> champions) {

        public String riotId() {
            return gameName + "#" + tagLine;
        }
    }

    // Joueur introuvable : FOUND=false. Cœur ou Riot indisponibles : vide, et surtout pas « introuvable ».
    public record Lookup(boolean found, Optional<Summary> summary) {
    }

    public Lookup lookup(String slug) {
        Instant maintenant = Instant.now();
        lectures.values().removeIf(lecture -> lecture.depuis().plus(FRAICHEUR).isBefore(maintenant));
        CompletableFuture<Lookup> nouvelle = new CompletableFuture<>();
        Lecture enCours = lectures.putIfAbsent(slug, new Lecture(nouvelle, maintenant));
        if (enCours != null) {
            return enCours.lookup().join();
        }
        Lookup lu = lire(slug);
        nouvelle.complete(lu);
        return lu;
    }

    private Lookup lire(String slug) {
        try {
            return new Lookup(true, Optional.of(read(slug)));
        } catch (FeignException.NotFound introuvable) {
            return new Lookup(false, Optional.empty());
        } catch (Exception ex) {
            log.debug("Résumé indisponible pour la page de {} : {}", slug, ex.getMessage());
            return new Lookup(true, Optional.empty());
        }
    }

    public Optional<Summary> summary(String slug) {
        return lookup(slug).summary();
    }

    private Summary read(String slug) throws IOException {
        JsonNode page = objectMapper.readTree(core.getPlayer(slug, null, null, 30, false, false));
        JsonNode overall = page.path("stats").path("overall");
        List<String> champions = new ArrayList<>();
        for (JsonNode ligne : page.path("stats").path("champions")) {
            if (champions.size() < CHAMPIONS && ligne.hasNonNull("label")) {
                champions.add(ligne.get("label").asText());
            }
        }
        return new Summary(
                page.path("gameName").asText(),
                page.path("tagLine").asText(),
                page.path("slug").asText(slug),
                solo(page.path("rankings")),
                overall.path("games").asLong(0),
                overall.hasNonNull("winRate") ? overall.get("winRate").asDouble() : null,
                overall.hasNonNull("kda") ? overall.get("kda").asDouble() : null,
                champions);
    }

    public List<String> trackedRiotIds(int limit) {
        try {
            List<String> ids = new ArrayList<>();
            objectMapper.readTree(core.getTrackedPlayers(limit)).forEach(id -> ids.add(id.asText()));
            return ids;
        } catch (Exception ex) {
            log.warn("Plan du site sans profils : {}", ex.getMessage());
            return List.of();
        }
    }

    private static Rank solo(JsonNode rankings) {
        Rank meilleur = null;
        for (JsonNode r : rankings) {
            if (!r.hasNonNull("tier")) {
                continue;
            }
            Rank rang = new Rank(r.path("riotQueueType").asText(""), r.get("tier").asText(),
                    r.path("division").asText(""), r.path("leaguePoints").asInt(), r.path("wins").asInt(),
                    r.path("losses").asInt());
            if (meilleur == null || rang.queue().toUpperCase(Locale.ROOT).contains("SOLO")) {
                meilleur = rang;
            }
        }
        return meilleur;
    }
}

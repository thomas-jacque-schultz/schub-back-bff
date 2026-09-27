package schultz.thomas.schub.bff.business.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

// Ce qu'un robot doit lire sur la page d'un joueur, sans exécuter de JavaScript.
@Service
public class PlayerSeoService {

    private static final Logger log = LoggerFactory.getLogger(PlayerSeoService.class);
    private static final int CHAMPIONS = 3;

    private final CoreFeignClient core;
    private final ObjectMapper objectMapper;

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

    // Vide si le joueur est introuvable ou si le cœur ne répond pas : la page garde alors ses balises génériques.
    public Optional<Summary> summary(String slug) {
        try {
            JsonNode page = objectMapper.readTree(core.getPlayer(slug, null, null, 30, false));
            JsonNode overall = page.path("stats").path("overall");
            List<String> champions = new ArrayList<>();
            for (JsonNode ligne : page.path("stats").path("champions")) {
                if (champions.size() < CHAMPIONS && ligne.hasNonNull("label")) {
                    champions.add(ligne.get("label").asText());
                }
            }
            return Optional.of(new Summary(
                    page.path("gameName").asText(),
                    page.path("tagLine").asText(),
                    page.path("slug").asText(slug),
                    solo(page.path("rankings")),
                    overall.path("games").asLong(0),
                    overall.hasNonNull("winRate") ? overall.get("winRate").asDouble() : null,
                    overall.hasNonNull("kda") ? overall.get("kda").asDouble() : null,
                    champions));
        } catch (Exception ex) {
            log.debug("Résumé indisponible pour la page de {} : {}", slug, ex.getMessage());
            return Optional.empty();
        }
    }

    public List<String> trackedRiotIds() {
        try {
            List<String> ids = new ArrayList<>();
            objectMapper.readTree(core.getTrackedPlayers(50_000)).forEach(id -> ids.add(id.asText()));
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

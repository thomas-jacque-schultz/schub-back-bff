package schultz.thomas.schub.bff.business.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Les pages publiques d'équipe (Schub#98), lues pour le head, le corps servi aux robots et la carte de partage.
@Service
public class TeamSeoService {

    private static final Logger log = LoggerFactory.getLogger(TeamSeoService.class);

    private final CoreFeignClient core;
    private final ObjectMapper objectMapper;

    public TeamSeoService(CoreFeignClient core, ObjectMapper objectMapper) {
        this.core = core;
        this.objectMapper = objectMapper;
    }

    public record PublicTeam(String id, String name, List<String> members, long games, long wins, long monthGames,
                             long monthWins, String duoA, String duoB, List<String> champions) {
    }

    public record Listed(String id, String name) {
    }

    // Vide si l'équipe est privée, inexistante, ou si le cœur ne répond pas.
    public Optional<PublicTeam> team(String teamId) {
        try {
            JsonNode t = objectMapper.readTree(core.getPublicTeam(teamId));
            List<String> membres = new ArrayList<>();
            t.path("members").forEach(m -> {
                if (m.hasNonNull("riotId")) {
                    membres.add(m.get("riotId").asText());
                }
            });
            List<String> champions = new ArrayList<>();
            t.path("champions").forEach(c -> champions.add(c.path("name").asText()));
            JsonNode duo = t.path("bestDuo");
            return Optional.of(new PublicTeam(t.path("id").asText(), t.path("name").asText(), membres,
                    t.path("games").asLong(), t.path("wins").asLong(), t.path("monthGames").asLong(),
                    t.path("monthWins").asLong(), duo.isNull() || duo.isMissingNode() ? null : duo.path("a").asText(),
                    duo.isNull() || duo.isMissingNode() ? null : duo.path("b").asText(), champions));
        } catch (FeignException.NotFound absente) {
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Équipe publique {} illisible : {}", teamId, ex.getMessage());
            return Optional.empty();
        }
    }

    public List<Listed> listed() {
        try {
            List<Listed> equipes = new ArrayList<>();
            objectMapper.readTree(core.getPublicTeams()).forEach(t ->
                    equipes.add(new Listed(t.path("id").asText(), t.path("name").asText())));
            return equipes;
        } catch (Exception ex) {
            log.warn("Plan du site sans équipes : {}", ex.getMessage());
            return List.of();
        }
    }
}

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
import java.util.Locale;
import java.util.Optional;

// Les pages publiques de combos par palier (Schub#112), lues pour les robots. Chemin : /combos/silver-gold/adc-sup.
@Service
public class TierCombosSeoService {

    private static final Logger log = LoggerFactory.getLogger(TierCombosSeoService.class);

    public static final List<String> PALIERS = List.of("IRON_BRONZE", "SILVER_GOLD", "PLATINUM_EMERALD", "DIAMOND",
            "MASTER_PLUS");
    public static final List<String> TYPES = List.of("ADC_SUP", "JGL_MID", "JGL_MID_SUP");

    private final CoreFeignClient core;
    private final ObjectMapper objectMapper;

    public TierCombosSeoService(CoreFeignClient core, ObjectMapper objectMapper) {
        this.core = core;
        this.objectMapper = objectMapper;
    }

    public record Line(List<String> champions, long games, long wins, boolean lowConfidence) {
    }

    public record Page(String tier, String kind, long games, List<Line> lines) {
    }

    public static String code(String slug) {
        return slug.toUpperCase(Locale.ROOT).replace('-', '_');
    }

    public static String slug(String code) {
        return code.toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static boolean known(String tier, String kind) {
        return PALIERS.contains(tier) && TYPES.contains(kind);
    }

    // Vide si la page n'existe pas, ou si le cœur ou le connecteur ne répondent pas.
    public Optional<Page> page(String tier, String kind) {
        if (!known(tier, kind)) {
            return Optional.empty();
        }
        try {
            JsonNode page = objectMapper.readTree(core.getTierCombos(tier, kind));
            List<Line> lignes = new ArrayList<>();
            page.path("combos").forEach(combo -> {
                List<String> champions = new ArrayList<>();
                combo.path("champions").forEach(c -> champions.add(c.path("name").asText("?")));
                lignes.add(new Line(champions, combo.path("games").asLong(), combo.path("wins").asLong(),
                        combo.path("lowConfidence").asBoolean()));
            });
            return Optional.of(new Page(tier, kind, page.path("games").asLong(), lignes));
        } catch (FeignException.NotFound absente) {
            return Optional.empty();
        } catch (Exception ex) {
            log.warn("Combos {} / {} illisibles : {}", tier, kind, ex.getMessage());
            return Optional.empty();
        }
    }
}

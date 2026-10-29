package schultz.thomas.schub.bff.api.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.bff.business.service.RelayPassSigner;
import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

// Le Macro Planner d'une équipe (Schub#171, #172) : ses plans, gardés par le cœur, et le laissez-passer de son salon
// temps réel. Le cœur tranche l'appartenance (TEAM_VIEW) : le laissez-passer n'est signé qu'après sa réponse.
@RestController
@RequestMapping("/teams/{teamId}")
public class MacroPlanProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;
    private final RelayPassSigner signer;
    private final ObjectMapper json;

    public MacroPlanProxyController(CoreFeignClient core, UpstreamGateway gateway, RelayPassSigner signer,
                                    ObjectMapper json) {
        this.core = core;
        this.gateway = gateway;
        this.signer = signer;
        this.json = json;
    }

    @PostMapping("/macro-pass")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> pass(@PathVariable String teamId) {
        return gateway.call(UPSTREAM, () -> {
            core.getTeam(teamId);
            try {
                return json.writeValueAsBytes(signer.sign(teamId));
            } catch (com.fasterxml.jackson.core.JsonProcessingException impossible) {
                throw new IllegalStateException(impossible);
            }
        });
    }

    @GetMapping("/macro-plans")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> all(@PathVariable String teamId) {
        return gateway.call(UPSTREAM, () -> core.getMacroPlans(teamId));
    }

    @GetMapping("/macro-plans/{planId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> byId(@PathVariable String teamId, @PathVariable String planId) {
        return gateway.call(UPSTREAM, () -> core.getMacroPlan(teamId, planId));
    }

    @PostMapping("/macro-plans")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> create(@PathVariable String teamId, @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, HttpStatus.CREATED, () -> core.createMacroPlan(teamId, gateway.parseBody(body)));
    }

    @PutMapping("/macro-plans/{planId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> update(@PathVariable String teamId, @PathVariable String planId,
                                         @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.updateMacroPlan(teamId, planId, gateway.parseBody(body)));
    }

    @DeleteMapping("/macro-plans/{planId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> delete(@PathVariable String teamId, @PathVariable String planId) {
        return gateway.callVoid(UPSTREAM, () -> core.deleteMacroPlan(teamId, planId));
    }
}

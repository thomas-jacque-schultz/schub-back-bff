package schultz.thomas.schub.bff.api.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.bff.business.service.UpstreamGateway;
import schultz.thomas.schub.bff.data.client.CoreFeignClient;

// La banque du quiz (Schub#137) : lue sans compte par le jeu, modifiée par l'OWNER dans Schub.
@RestController
@RequestMapping("/quiz/questions")
public class QuizProxyController {

    private static final String UPSTREAM = "schub-core";

    private final CoreFeignClient core;
    private final UpstreamGateway gateway;

    public QuizProxyController(CoreFeignClient core, UpstreamGateway gateway) {
        this.core = core;
        this.gateway = gateway;
    }

    @GetMapping
    public ResponseEntity<byte[]> active() {
        return gateway.call(UPSTREAM, core::getQuizQuestions);
    }

    @GetMapping("/all")
    @PreAuthorize("hasAuthority('QUIZ_QUESTION_EDIT')")
    public ResponseEntity<byte[]> all() {
        return gateway.call(UPSTREAM, core::getAllQuizQuestions);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('QUIZ_QUESTION_EDIT')")
    public ResponseEntity<byte[]> save(@PathVariable String id, @RequestBody(required = false) byte[] body) {
        return gateway.call(UPSTREAM, () -> core.saveQuizQuestion(id, gateway.parseBody(body)));
    }
}

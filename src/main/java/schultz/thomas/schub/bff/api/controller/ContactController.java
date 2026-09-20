package schultz.thomas.schub.bff.api.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import schultz.thomas.schub.bff.api.dto.ContactRequest;
import schultz.thomas.schub.bff.api.dto.ContactResponse;
import schultz.thomas.schub.bff.business.service.ContactService;

/**
 * Le formulaire de contact du portfolio.
 *
 * <p><strong>La seule route publique en écriture du BFF.</strong> Elle est ouverte explicitement
 * dans {@code SecurityConfig}, qui applique par ailleurs {@code anyRequest().authenticated()} :
 * sans cette ligne, le formulaire répondrait 401 à tout le monde.</p>
 *
 * <p>Elle ne porte aucune permission et n'en portera jamais — c'est tout l'objet d'un formulaire
 * de contact. Ce qui la protège est ailleurs : trois couches anti-spam dans
 * {@link ContactService}.</p>
 */
@RestController
@RequestMapping("/contact")
public class ContactController {

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @PostMapping
    public ResponseEntity<ContactResponse> submit(@RequestBody ContactRequest request,
                                                  HttpServletRequest httpRequest) {
        ContactService.Outcome outcome = contactService.submit(request, clientAddress(httpRequest));

        return switch (outcome) {
            case DELIVERED -> ResponseEntity.ok(new ContactResponse(true));
            // Un 400 sans détail, quelle que soit la couche qui a refusé : dire à un robot
            // laquelle l'a arrêté, c'est lui dire quoi corriger.
            case REJECTED -> ResponseEntity.badRequest().body(new ContactResponse(false));
            case RATE_LIMITED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ContactResponse(false));
            case UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ContactResponse(false));
        };
    }

    /**
     * L'adresse réelle du visiteur.
     *
     * <p>{@code getRemoteAddr()} seul renverrait l'adresse de nginx — c'est-à-dire la même pour
     * tout le monde. La limitation de débit par IP compterait alors tous les visiteurs comme un
     * seul, et fermerait le formulaire au premier robot venu.</p>
     *
     * <p>L'ordre compte : {@code CF-Connecting-IP} d'abord, parce que Cloudflare est le seul
     * point d'entrée en production et que cet en-tête, lui, ne peut pas être forgé par le
     * client — Cloudflare le réécrit. {@code X-Forwarded-For} ensuite, pour le développement
     * derrière nginx seul, et en ne lisant que sa <em>première</em> valeur.</p>
     */
    private static String clientAddress(HttpServletRequest request) {
        String cloudflare = request.getHeader("CF-Connecting-IP");
        if (cloudflare != null && !cloudflare.isBlank()) {
            return cloudflare.trim();
        }

        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}

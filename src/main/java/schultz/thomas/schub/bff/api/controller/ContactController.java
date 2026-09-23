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
            case REJECTED -> ResponseEntity.badRequest().body(new ContactResponse(false));
            case RATE_LIMITED -> ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(new ContactResponse(false));
            case UNAVAILABLE -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(new ContactResponse(false));
        };
    }

    // CF-Connecting-IP d'abord : Cloudflare le réécrit, le client ne peut pas le forger. X-Forwarded-For
    // (première valeur) ne sert qu'en dev derrière nginx seul. getRemoteAddr() = nginx pour tout le monde.
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

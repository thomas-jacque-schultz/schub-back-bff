package schultz.thomas.schub.bff.api.controller;

import jakarta.servlet.http.HttpServletRequest;

final class ClientAddress {

    private ClientAddress() {
    }

    // CF-Connecting-IP d'abord : Cloudflare le réécrit, le client ne peut pas le forger. X-Forwarded-For
    // (première valeur) ne sert qu'en dev derrière nginx seul. getRemoteAddr() = nginx pour tout le monde.
    static String of(HttpServletRequest request) {
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

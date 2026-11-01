package org.suffleport.zwloader.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.suffleport.zwloader.domain.GuestVisit;
import org.suffleport.zwloader.service.GuestVisitService;

import java.time.format.DateTimeFormatter;

/**
 * Публичный эндпоинт для check-in гостя по QR-коду.
 * Не требует авторизации — открывается напрямую с телефона при сканировании QR.
 * Возвращает HTML-страницу с результатом (успех или ошибка).
 */
@Controller
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicCheckinController {

    private final GuestVisitService guestVisitService;

    private static final DateTimeFormatter DISPLAY_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    @GetMapping(value = "/guest-checkin", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> guestCheckin(@RequestParam(name = "code", required = false) String code) {
        if (code == null || code.isBlank()) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildHtml("Ошибка", "❌ Код визита не указан.", "#dc2626"));
        }
        try {
            // checkInByCode уже загружает guest+host через JOIN FETCH
            GuestVisit visitWithGuest = guestVisitService.checkInByCode(code);
            String guestName = visitWithGuest.getGuest() != null
                    ? visitWithGuest.getGuest().getLastName() + " " + visitWithGuest.getGuest().getFirstName()
                    : "Гость";
            String period = "—";
            if (visitWithGuest.getPlannedFrom() != null && visitWithGuest.getPlannedTo() != null) {
                period = visitWithGuest.getPlannedFrom().format(DISPLAY_FMT)
                        + " – " + visitWithGuest.getPlannedTo().format(DISPLAY_FMT);
            } else if (visitWithGuest.getPlannedFrom() != null) {
                period = visitWithGuest.getPlannedFrom().format(DISPLAY_FMT);
            }

            boolean isEntering = "ACTIVE".equalsIgnoreCase(visitWithGuest.getStatus());
            String actionTitle = isEntering ? "Гость вошёл" : "Гость покинул здание";
            String actionEmoji = isEntering ? "✅" : "🔚";
            String actionColor = isEntering ? "#16a34a" : "#2563eb";

            String body = actionEmoji + " " + actionTitle + "!"
                    + "<br><br>"
                    + "<b>Гость:</b> " + escapeHtml(guestName)
                    + "<br>"
                    + "<b>Визит:</b> #" + visitWithGuest.getId()
                    + "<br>"
                    + "<b>Период:</b> " + period;
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildHtml(actionTitle, body, actionColor));
        } catch (IllegalStateException ex) {
            // Визит недоступен или завершён
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildHtml("Визит недоступен", "⚠️ " + escapeHtml(ex.getMessage()), "#d97706"));
        } catch (Exception ex) {
            return ResponseEntity.badRequest()
                    .contentType(MediaType.TEXT_HTML)
                    .body(buildHtml("Ошибка", "❌ " + escapeHtml(ex.getMessage()), "#dc2626"));
        }
    }

    private static String buildHtml(String title, String message, String color) {
        return "<!DOCTYPE html><html lang=\"ru\"><head>"
                + "<meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<title>" + escapeHtml(title) + "</title>"
                + "<style>"
                + "body{font-family:sans-serif;display:flex;align-items:center;justify-content:center;"
                + "min-height:100vh;margin:0;background:#f1f5f9;}"
                + ".card{background:#fff;border-radius:16px;padding:2rem 2.5rem;max-width:400px;width:90%;"
                + "box-shadow:0 4px 24px rgba(0,0,0,0.10);text-align:center;}"
                + "h1{font-size:1.4rem;margin-bottom:1rem;color:" + color + ";}"
                + "p{font-size:1rem;color:#374151;line-height:1.6;}"
                + "</style>"
                + "</head><body>"
                + "<div class=\"card\">"
                + "<h1>" + escapeHtml(title) + "</h1>"
                + "<p>" + message + "</p>"
                + "</div>"
                + "</body></html>";
    }

    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#x27;");
    }
}

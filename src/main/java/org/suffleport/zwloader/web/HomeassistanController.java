package org.suffleport.zwloader.web;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.domain.Event;
import org.suffleport.zwloader.domain.Source;
import org.suffleport.zwloader.service.CardService;
import org.suffleport.zwloader.service.EventService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ha")
@RequiredArgsConstructor
public class HomeassistanController {

    private final CardService cardService;
    private final EventService eventService;

    /** has_access: вернуть plain true/false */
    @GetMapping(value = "/has-access", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> hasAccess(@RequestParam("uid") String uid) {
        boolean allow = false;
        if (uid != null && !uid.isBlank()) {
            var card = cardService.findByUid(uid);
            allow = card != null && card.isActive() && card.getPerson() != null;
        }
        return ResponseEntity.ok(allow ? "true" : "false");
    }

    /** log_event_toggle (NFC): вернуть направление как plain text */
    @PostMapping(value = "/nfc/toggle", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> nfcToggle(@RequestParam("uid") String uid,
                                            @RequestParam(value = "device", required = false) String device) {
        Event e = eventService.createNfcToggleEvent(uid, device);
        return ResponseEntity.ok(e.getDirection().name());
    }

    /** reporter_log_face_toggle: вернуть JSON { direction: "IN"|"OUT"|"NONE" } */
    @PostMapping(value = "/face/toggle", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> faceToggle(@RequestParam("face") String faceName,
                                          @RequestParam(value = "device", required = false) String device) {
        Event e = eventService.createFaceToggleEvent(faceName, device);
        return Map.of("direction", e.getDirection() != null ? e.getDirection().name() : "NONE");
    }

    /** log_face_event_toggle (fallback): вернуть plain направление или NONE */
    @GetMapping(value = "/face/last-direction", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> faceLastDirection(@RequestParam("face") String faceName) {
        Event last = eventService.lastByFace(faceName);
        return ResponseEntity.ok(last != null && last.getDirection() != null ? last.getDirection().name() : "NONE");
    }

    /** create_security_alert: фиксируем событие DENY */
    @PostMapping(value = "/security-alert", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> securityAlert(@RequestParam("uid") String uidOrFace,
                                             @RequestParam(value = "device", required = false) String device,
                                             @RequestParam(value = "source", defaultValue = "NFC") Source source,
                                             @RequestParam(value = "reason", required = false) String reason) {
        Event e = eventService.createSecurityAlert(uidOrFace, device, source, reason);
        return Map.of("status", "ok", "eventId", e.getId());
    }

    /** register_card_by_name: uid + name -> статус */
    @PostMapping(value = "/card/register-by-name", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> registerCard(@RequestParam("uid") String uid,
                                            @RequestParam(value = "name", required = false) String fullName) {
        var res = cardService.registerCardByName(uid, fullName);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("status", res.status());
        out.put("person_name", res.personName() != null ? res.personName() : "");
        out.put("uid", uid);
        return out;
    }

    /** send_scanned_card: скан без имени в режиме регистрации */
    @PostMapping(value = "/card/scan", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> scanCard(@RequestParam("uid") String uid,
                                        @RequestParam(value = "name", required = false) String fullName) {
        var res = (fullName == null || fullName.isBlank())
                ? cardService.analyzeScanStatus(uid)
                : cardService.registerCardByName(uid, fullName);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("status", res.status());
        out.put("person_name", res.personName() != null ? res.personName() : "");
        out.put("uid", uid);
        return out;
    }

    /** scan_guest_qr: простой парс JSON payload (здесь stub) */
    @PostMapping(value = "/guest/scan-qr", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> scanGuestQr(@RequestParam("qr_payload") String payload) {
        // В реальности: распарсить JSON, проверить гостя/визит, создать событие. Здесь упрощено.
        return Map.of("status", "ok", "payload_echo", payload);
    }

    // DTOs при необходимости
    @Data
    public static class SimpleResponse { private String status; }
}

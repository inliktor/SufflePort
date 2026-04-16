package org.suffleport.zwloader.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.domain.Event;
import org.suffleport.zwloader.domain.Source;
import org.suffleport.zwloader.service.CardService;
import org.suffleport.zwloader.service.EventService;
import org.suffleport.zwloader.service.HaCardScanInboxService;
import org.suffleport.zwloader.service.HaGuestQrService;
import org.suffleport.zwloader.service.HaIntegrationAuthService;
import org.suffleport.zwloader.service.HomeAssistantIntegrationService;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/ha")
@RequiredArgsConstructor
@Slf4j
public class HomeassistanController {

    private final CardService cardService;
    private final EventService eventService;
    private final HaCardScanInboxService haCardScanInboxService;
    private final HaGuestQrService haGuestQrService;
    private final HaIntegrationAuthService haIntegrationAuthService;
    private final HomeAssistantIntegrationService homeAssistantIntegrationService;

    @GetMapping(value = "/integration/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> integrationStatus(@RequestParam("token") String token) {
        haIntegrationAuthService.requireValidToken(token);
        return homeAssistantIntegrationService.ping();
    }

    /** has_access: вернуть plain true/false */
    @GetMapping(value = "/has-access", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> hasAccess(@RequestParam("uid") String uid) {
        boolean allow = false;
        if (uid != null && !uid.isBlank()) {
            var card = cardService.findByUid(uid);
            allow = card != null && card.isActive() && card.getPerson() != null;
            captureScanFromAccessIfRegistrationActive(uid);
        }
        return ResponseEntity.ok(allow ? "true" : "false");
    }

    @GetMapping(value = "/has_access", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> hasAccessLegacy(@RequestParam("uid") String uid) {
        return hasAccess(uid);
    }

    @PostMapping(value = "/has-access", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> hasAccessPost(@RequestBody(required = false) Map<String, Object> body,
                                                @RequestParam(value = "uid", required = false) String uid,
                                                @RequestParam(value = "p_uid", required = false) String legacyUid) {
        String resolvedUid = firstNonBlank(uid, legacyUid, valueOf(body, "uid"), valueOf(body, "p_uid"));
        return hasAccess(resolvedUid);
    }

    @PostMapping(value = "/has_access", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> hasAccessPostLegacy(@RequestBody(required = false) Map<String, Object> body,
                                                      @RequestParam(value = "uid", required = false) String uid,
                                                      @RequestParam(value = "p_uid", required = false) String legacyUid) {
        return hasAccessPost(body, uid, legacyUid);
    }

    /** log_event_toggle (NFC): вернуть направление как plain text */
    @PostMapping(value = "/nfc/toggle", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> nfcToggle(@RequestParam(value = "uid", required = false) String uid,
                                            @RequestParam(value = "p_uid", required = false) String legacyUid,
                                            @RequestParam(value = "device", required = false) String device) {
        String resolvedUid = firstNonBlank(uid, legacyUid);
        Event e = eventService.createNfcToggleEvent(resolvedUid, device);
        return ResponseEntity.ok(e.getDirection().name());
    }

    @GetMapping(value = "/nfc/toggle", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> nfcToggleGet(@RequestParam(value = "uid", required = false) String uid,
                                               @RequestParam(value = "p_uid", required = false) String legacyUid,
                                               @RequestParam(value = "device", required = false) String device) {
        String resolvedUid = firstNonBlank(uid, legacyUid);
        Event e = eventService.createNfcToggleEvent(resolvedUid, device);
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
        return buildCardRegistrationResponse(uid, fullName);
    }

    @PostMapping(value = "/register-card", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> registerCardCompat(@RequestParam("token") String token,
                                                  @Valid @RequestBody HaCardRegistrationRequest request) {
        haIntegrationAuthService.requireValidToken(token);
        return buildCardRegistrationResponse(request.getUid(), request.getName());
    }

    /** send_scanned_card: скан без имени в режиме регистрации */
    @PostMapping(value = "/card/scan", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> scanCard(@RequestParam("uid") String uid,
                                        @RequestParam(value = "name", required = false) String fullName) {
        return buildCardScanResponse(uid, fullName);
    }

    @PostMapping(value = "/card-scanned", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> scanCardCompat(@RequestParam("token") String token,
                                              @Valid @RequestBody HaCardRegistrationRequest request) {
        haIntegrationAuthService.requireValidToken(token);
        return buildCardScanResponse(request.getUid(), request.getName());
    }

    @GetMapping(value = "/card-scan/latest", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> latestCardScan() {
        var payload = haCardScanInboxService.poll();
        if (payload == null) {
            return Map.of("status", "idle");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", payload.status());
        out.put("uid", payload.uid());
        out.put("person_name", payload.personName() != null ? payload.personName() : "");
        out.put("received_at", payload.receivedAt().toString());
        return out;
    }

    private Map<String, Object> buildCardScanResponse(String uid, String fullName) {
        if (!haCardScanInboxService.isRegistrationWindowActive()) {
            log.info("HA card scan ignored because registration window is closed, uid={}", uid);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("status", "ignored_registration_closed");
            out.put("person_name", "");
            out.put("uid", uid);
            return out;
        }

        var res = (fullName == null || fullName.isBlank())
                ? cardService.analyzeScanStatus(uid)
                : cardService.registerCardByName(uid, fullName);
        log.info("HA card scan received uid={}, status={}, personNamePresent={}", uid, res.status(), res.personName() != null && !res.personName().isBlank());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", res.status());
        out.put("person_name", res.personName() != null ? res.personName() : "");
        out.put("uid", uid);
        boolean accepted = haCardScanInboxService.pushIfRegistrationWindowActive(uid, res.status(), res.personName());
        out.put("accepted", accepted);
        return out;
    }

    /** scan_guest_qr: обработка гостевого QR-кода из HA scanner payload */
    @PostMapping(value = "/guest/scan-qr", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> scanGuestQr(@RequestParam("qr_payload") String payload) {
        return ResponseEntity.ok(haGuestQrService.processPayload(payload).message());
    }

    @PostMapping(value = "/qr_webhook", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> scanGuestQrWebhook(@RequestParam("token") String token,
                                                     @RequestBody String payload) {
        haIntegrationAuthService.requireValidToken(token);
        return ResponseEntity.ok(haGuestQrService.processPayload(payload).message());
    }

    private Map<String, Object> buildCardRegistrationResponse(String uid, String fullName) {
        var res = cardService.registerCardByName(uid, fullName);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", res.status());
        out.put("person_name", res.personName() != null ? res.personName() : "");
        out.put("uid", uid);
        return out;
    }

    private void captureScanFromAccessIfRegistrationActive(String uid) {
        if (uid == null || uid.isBlank() || !haCardScanInboxService.isRegistrationWindowActive()) {
            return;
        }
        var res = cardService.analyzeScanStatus(uid);
        boolean accepted = haCardScanInboxService.pushIfRegistrationWindowActive(uid, res.status(), res.personName());
        if (accepted) {
            log.info("HA card scan captured via has_access during active registration window, uid={}", uid);
        }
    }

    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return null;
    }

    private String valueOf(Map<String, Object> body, String key) {
        if (body == null || !body.containsKey(key) || body.get(key) == null) {
            return null;
        }
        return String.valueOf(body.get(key));
    }

    // DTOs при необходимости
    @Data
    public static class SimpleResponse { private String status; }

    @Data
    public static class HaCardRegistrationRequest {
        @Size(max = 64, message = "UID карты не может быть длиннее 64 символов")
        private String uid;

        @Size(max = 200, message = "Имя владельца не может быть длиннее 200 символов")
        private String name;
    }
}

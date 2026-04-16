package org.suffleport.zwloader.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.service.CardService;

import java.util.Map;

@RestController
@RequestMapping("/rpc")
@RequiredArgsConstructor
public class PostgrestCompatController {

    private final CardService cardService;

    @GetMapping(value = "/has_access", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> hasAccessGet(@RequestParam(value = "uid", required = false) String uid,
                                               @RequestParam(value = "p_uid", required = false) String legacyUid) {
        return ResponseEntity.ok(isAllowed(firstNonBlank(uid, legacyUid)) ? "true" : "false");
    }

    @PostMapping(value = "/has_access", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> hasAccessPost(@RequestBody(required = false) Map<String, Object> body,
                                                @RequestParam(value = "uid", required = false) String uid,
                                                @RequestParam(value = "p_uid", required = false) String legacyUid) {
        String resolvedUid = firstNonBlank(uid, legacyUid, valueOf(body, "uid"), valueOf(body, "p_uid"));
        return ResponseEntity.ok(isAllowed(resolvedUid) ? "true" : "false");
    }

    private boolean isAllowed(String uid) {
        if (uid == null || uid.isBlank()) {
            return false;
        }
        var card = cardService.findByUid(uid);
        return card != null && card.isActive() && card.getPerson() != null;
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
}

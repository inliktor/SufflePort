package org.suffleport.zwloader.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.suffleport.zwloader.domain.GuestVisit;
import org.suffleport.zwloader.validation.ValidationRules;

import java.util.Iterator;

@Service
@RequiredArgsConstructor
public class HaGuestQrService {

    private static final int MAX_QR_PAYLOAD_LENGTH = 4_000;
    private static final int MAX_PARSE_DEPTH = 6;

    private final ObjectMapper objectMapper;
    private final GuestVisitService guestVisitService;

    public QrScanResult processPayload(String rawPayload) {
        String payload = ValidationRules.normalizeRequiredText(rawPayload, "QR payload", MAX_QR_PAYLOAD_LENGTH);
        String visitCode = extractVisitCode(payload);
        GuestVisit visit = guestVisitService.checkInByCode(visitCode);
        String guestName = visit.getGuest() != null && visit.getGuest().getFullName() != null
                ? visit.getGuest().getFullName()
                : "гость";
        String message = "Визит " + visit.getId() + " активирован для " + guestName;
        return new QrScanResult("ok", message, visit.getId(), visit.getStatus(), visitCode);
    }

    private String extractVisitCode(String payload) {
        JsonNode root = tryParseJson(payload);
        String extracted = root != null ? extractVisitCode(root, 0) : extractVisitCodeFromText(payload);
        if (extracted == null || extracted.isBlank()) {
            throw new IllegalArgumentException("Не удалось извлечь код визита из QR payload");
        }
        return extracted;
    }

    private String extractVisitCode(JsonNode node, int depth) {
        if (node == null || node.isNull() || depth > MAX_PARSE_DEPTH) {
            return null;
        }
        if (node.isTextual()) {
            String value = node.asText();
            JsonNode nested = tryParseJson(value);
            if (nested != null && nested != node) {
                String nestedCode = extractVisitCode(nested, depth + 1);
                if (nestedCode != null) {
                    return nestedCode;
                }
            }
            return extractVisitCodeFromText(value);
        }
        if (node.isNumber()) {
            return "VISIT:" + node.asText();
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String extracted = extractVisitCode(item, depth + 1);
                if (extracted != null) {
                    return extracted;
                }
            }
            return null;
        }
        if (node.isObject()) {
            String directCode = extractDirectCode(node);
            if (directCode != null) {
                return directCode;
            }
            for (String fieldName : new String[]{"payload", "data", "event_data", "result", "qr", "qr_payload"}) {
                if (node.has(fieldName)) {
                    String extracted = extractVisitCode(node.get(fieldName), depth + 1);
                    if (extracted != null) {
                        return extracted;
                    }
                }
            }
            Iterator<JsonNode> iterator = node.elements();
            while (iterator.hasNext()) {
                String extracted = extractVisitCode(iterator.next(), depth + 1);
                if (extracted != null) {
                    return extracted;
                }
            }
        }
        return null;
    }

    private String extractDirectCode(JsonNode node) {
        for (String fieldName : new String[]{"code", "visit_code", "visitCode", "text", "value", "raw", "content"}) {
            if (!node.has(fieldName)) {
                continue;
            }
            String extracted = extractVisitCode(node.get(fieldName), 1);
            if (extracted != null) {
                return extracted;
            }
        }
        for (String fieldName : new String[]{"visit_id", "visitId", "id"}) {
            if (node.has(fieldName) && node.get(fieldName).canConvertToLong()) {
                return "VISIT:" + node.get(fieldName).asText();
            }
        }
        return null;
    }

    private String extractVisitCodeFromText(String value) {
        String normalized = ValidationRules.normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.regionMatches(true, 0, "VISIT:", 0, "VISIT:".length())) {
            return "VISIT:" + normalized.substring("VISIT:".length()).trim();
        }
        return null;
    }

    private JsonNode tryParseJson(String payload) {
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    public record QrScanResult(String status, String message, Long visitId, String visitStatus, String code) {}
}
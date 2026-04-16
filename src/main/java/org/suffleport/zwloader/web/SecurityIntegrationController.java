package org.suffleport.zwloader.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.suffleport.zwloader.domain.Event;
import org.suffleport.zwloader.domain.Source;
import org.suffleport.zwloader.service.EventService;
import org.suffleport.zwloader.service.HaIntegrationAuthService;

import java.util.Map;

@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityIntegrationController {

    private final EventService eventService;
    private final HaIntegrationAuthService haIntegrationAuthService;

    @PostMapping(value = "/alert", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> securityAlert(@RequestParam("token") String token,
                                             @Valid @RequestBody SecurityAlertRequest request) {
        haIntegrationAuthService.requireValidToken(token);
        Event event = eventService.createSecurityAlert(
                request.getUid(),
                request.getDeviceId(),
                Source.NFC,
                request.getAlertType()
        );
        return Map.of(
                "status", "ok",
                "eventId", event.getId(),
                "alert_type", request.getAlertType()
        );
    }

    @PostMapping(value = "/unknown-face", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> unknownFace(@RequestParam(value = "token", required = false) String token,
                                           @Valid @RequestBody UnknownFaceAlertRequest request) {
        if (token != null && !token.isBlank()) {
            haIntegrationAuthService.requireValidToken(token);
        }
        Event event = eventService.createSecurityAlert(
                request.getFaceName(),
                request.getDeviceId(),
                Source.FACE,
                "UNKNOWN_FACE"
        );
        return Map.of(
                "status", "ok",
                "eventId", event.getId(),
                "face_name", request.getFaceName()
        );
    }

    @Data
    public static class SecurityAlertRequest {
        @NotBlank(message = "Тип алерта обязателен")
        @Size(max = 100, message = "Тип алерта не может быть длиннее 100 символов")
        @JsonProperty("alert_type")
        private String alertType;

        @NotBlank(message = "UID обязателен")
        @Size(max = 200, message = "UID не может быть длиннее 200 символов")
        private String uid;

        @Size(max = 100, message = "ID устройства не может быть длиннее 100 символов")
        @JsonProperty("device_id")
        private String deviceId;
    }

    @Data
    public static class UnknownFaceAlertRequest {
        @NotBlank(message = "Имя лица обязательно")
        @Size(max = 200, message = "Имя лица не может быть длиннее 200 символов")
        @JsonProperty("face_name")
        private String faceName;

        @Size(max = 100, message = "ID устройства не может быть длиннее 100 символов")
        @JsonProperty("device_id")
        private String deviceId;
    }
}
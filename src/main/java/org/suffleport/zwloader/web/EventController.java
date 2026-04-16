package org.suffleport.zwloader.web;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.domain.*;
import org.suffleport.zwloader.service.EventService;
import org.suffleport.zwloader.service.HaIntegrationAuthService;

import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;
    private final HaIntegrationAuthService haIntegrationAuthService;

    @GetMapping
    public Object listAll(@RequestParam(name = "page", required = false) Integer page,
                          @RequestParam(name = "size", required = false) Integer size,
                          @RequestParam(name = "sort", required = false) String sortBy,
                  @RequestParam(name = "dir", required = false) String sortDir,
                  @RequestParam(name = "source", required = false) Source source,
                  @RequestParam(name = "direction", required = false) Direction direction,
                  @RequestParam(name = "date", required = false) LocalDate date) {
        if (PaginationSupport.isPaged(page, size)) {
            return eventService.listPage(source, direction, date,
                PaginationSupport.buildPageable(page, size, sortBy, sortDir,
                    "createdAt", "createdAt", "id", "source", "direction"));
        }
        return eventService.listAll(source, direction, date);
    }

    @GetMapping("/{id}")
    public Event get(@PathVariable Long id) { return eventService.getOrThrow(id); }

    @GetMapping("/by-person/{personId}")
    public List<Event> byPerson(@PathVariable java.util.UUID personId) {
        return eventService.listByPerson(personId);
    }

    @GetMapping("/by-card/{uid}")
    public List<Event> byCard(@PathVariable String uid) { return eventService.listByCard(uid); }

    @GetMapping("/by-device/{deviceId}")
    public List<Event> byDevicePeriod(@PathVariable String deviceId,
                                      @RequestParam("start") OffsetDateTime start,
                                      @RequestParam("end") OffsetDateTime end) {
        return eventService.listByDeviceAndPeriod(deviceId, start, end);
    }

    @GetMapping("/by-source")
    public List<Event> bySource(@RequestParam("source") Source source) { return eventService.findBySource(source); }

    @GetMapping("/created-after")
    public Object createdAfter(@RequestParam("date") OffsetDateTime date,
                               @RequestParam(name = "page", required = false) Integer page,
                               @RequestParam(name = "size", required = false) Integer size,
                               @RequestParam(name = "sort", required = false) String sortBy,
                               @RequestParam(name = "dir", required = false) String sortDir) {
        if (PaginationSupport.isPaged(page, size)) {
            return eventService.listCreatedAfter(date,
                    PaginationSupport.buildPageable(page, size, sortBy, sortDir,
                            "createdAt", "createdAt", "id", "source", "direction"));
        }
        return eventService.listCreatedAfter(date);
    }

    @GetMapping("/between")
    public List<Event> between(@RequestParam("start") OffsetDateTime start,
                               @RequestParam("end") OffsetDateTime end) {
        return eventService.listBetween(start, end);
    }

    @PostMapping
    public Event create(@Valid @RequestBody CreateEventRequest req) {
        return eventService.create(
                req.getCardUid(),
                req.getPersonId(),
                req.getDeviceId(),
                req.getFaceName(),
                req.getDirection(),
                req.getSource(),
                req.getMeta()
        );
    }

    @PostMapping(value = "/log", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> logFromHaForm(@RequestParam("token") String token,
                                             @RequestParam("uid") String uid,
                                             @RequestParam(value = "source", required = false) String source,
                                             @RequestParam(value = "device_id", required = false) String deviceId,
                                             @RequestParam(value = "grace_seconds", required = false) Integer graceSeconds) {
        haIntegrationAuthService.requireValidToken(token);
        HaLogEventRequest request = new HaLogEventRequest();
        request.setUid(uid);
        request.setSource(source);
        request.setDeviceId(deviceId);
        request.setGraceSeconds(graceSeconds);
        return handleHaToggleEvent(request);
    }

    @PostMapping(value = "/log", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> logFromHaJson(@RequestParam("token") String token,
                                             @Valid @RequestBody HaLogEventRequest request) {
        haIntegrationAuthService.requireValidToken(token);
        return handleHaToggleEvent(request);
    }

    @PostMapping("/manual")
    public Event createManual(@Valid @RequestBody ManualEventRequest req) {
        EventMeta meta = req.getMeta() != null ? req.getMeta() : new EventMeta();
        if (req.getReason() != null && !req.getReason().isBlank()) {
            meta.setDecision(req.getReason().trim());
        }

        return eventService.create(
                req.getCardUid(),
                req.getPersonId(),
                req.getDeviceId(),
                null,
                req.getDirection(),
                Source.MANUAL,
                meta
        );
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { eventService.delete(id); }

    private Map<String, Object> handleHaToggleEvent(HaLogEventRequest request) {
        Source source = parseSource(request.getSource());
        Event event = source == Source.FACE
                ? eventService.createFaceToggleEvent(request.getUid(), request.getDeviceId())
                : eventService.createNfcToggleEvent(request.getUid(), request.getDeviceId());

        return Map.of(
                "status", "ok",
                "direction", event.getDirection().name(),
                "eventId", event.getId(),
                "source", source.name()
        );
    }

    private Source parseSource(String rawSource) {
        if (rawSource == null || rawSource.isBlank()) {
            return Source.NFC;
        }
        try {
            return Source.valueOf(rawSource.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Недопустимый источник события: " + rawSource);
        }
    }

    @Data
    public static class CreateEventRequest {
        @Size(max = 64, message = "UID карты не может быть длиннее 64 символов")
        private String cardUid;
        private java.util.UUID personId;

        @Size(max = 100, message = "ID устройства не может быть длиннее 100 символов")
        private String deviceId;

        @Size(max = 200, message = "Имя лица не может быть длиннее 200 символов")
        private String faceName;
        private Direction direction;
        private Source source;
        private EventMeta meta;
    }

    @Data
    public static class HaLogEventRequest {
        @Size(max = 200, message = "Идентификатор карты или лица не может быть длиннее 200 символов")
        private String uid;

        private String source;

        @Size(max = 100, message = "ID устройства не может быть длиннее 100 символов")
        @JsonProperty("device_id")
        private String deviceId;

        @JsonProperty("grace_seconds")
        private Integer graceSeconds;
    }


    @Data
    public static class ManualEventRequest {
        @Size(max = 64, message = "UID карты не может быть длиннее 64 символов")
        private String cardUid;
        private java.util.UUID personId;
        @Size(max = 100, message = "ID устройства не может быть длиннее 100 символов")
        private String deviceId;

        private Direction direction;

        @Size(max = 200, message = "Причина не может быть длиннее 200 символов")
        private String reason;

        private EventMeta meta;
    }
}


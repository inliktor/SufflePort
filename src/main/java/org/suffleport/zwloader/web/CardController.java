package org.suffleport.zwloader.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.domain.Card;
import org.suffleport.zwloader.service.CardService;
import org.suffleport.zwloader.service.HaCardScanInboxService;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/cards")
@RequiredArgsConstructor
public class CardController {

    private final CardService cardService;
    private final HaCardScanInboxService haCardScanInboxService;

    @GetMapping("/{uid}")
    public Card get(@PathVariable String uid) { return cardService.getByUidOrThrow(uid); }

    @PostMapping
    public Card create(@Valid @RequestBody CreateCardRequest req) {
        return cardService.createCard(req.getUid(), req.getPersonId());
    }

    @PostMapping("/{uid}/activate")
    public Card activate(@PathVariable String uid) { return cardService.activate(uid); }

    @PostMapping("/{uid}/deactivate")
    public Card deactivate(@PathVariable String uid) { return cardService.deactivate(uid); }

    @PutMapping("/{uid}/reassign/{personId}")
    public Card reassign(@PathVariable String uid, @PathVariable UUID personId) {
        return cardService.reassignOwner(uid, personId);
    }

    @DeleteMapping("/{uid}")
    public void delete(@PathVariable String uid) { cardService.deleteByUid(uid); }

    @DeleteMapping("/by-person/{personId}")
    public void deleteByPerson(@PathVariable UUID personId) { cardService.deleteAllByPerson(personId); }

    @GetMapping("/by-person/{personId}")
    public List<Card> byPerson(@PathVariable UUID personId) { return cardService.findByPerson(personId); }

    @GetMapping("/by-person/{personId}/active")
    public List<Card> activeByPerson(@PathVariable UUID personId) { return cardService.findActiveByPerson(personId); }

    @GetMapping("/by-person/{personId}/inactive")
    public List<Card> inactiveByPerson(@PathVariable UUID personId) { return cardService.findInactiveByPerson(personId); }

    @GetMapping("/search")
    public List<Card> search(@RequestParam("q") String fragment) { return cardService.searchByUidFragment(fragment); }

    @GetMapping("/created-after")
    public List<Card> createdAfter(@RequestParam("date") OffsetDateTime date) { return cardService.listCreatedAfter(date); }

    @GetMapping("/by-person/{personId}/count-active")
    public long countActive(@PathVariable UUID personId) { return cardService.countActiveByPerson(personId); }

    @GetMapping("/count-active")
    public long countActive() { return cardService.countActive(); }

    @PostMapping("/registration/start")
    public Map<String, Object> startRegistration(@RequestParam(name = "ttlSec", defaultValue = "45") int ttlSec) {
        haCardScanInboxService.startRegistrationWindow(ttlSec);
        return Map.of("status", "ok", "active", true);
    }

    @PostMapping("/registration/stop")
    public Map<String, Object> stopRegistration() {
        haCardScanInboxService.stopRegistrationWindow();
        return Map.of("status", "ok", "active", false);
    }

    @GetMapping("/registration/status")
    public Map<String, Object> registrationStatus() {
        return Map.of("status", "ok", "active", haCardScanInboxService.isRegistrationWindowActive());
    }

    @Data
    public static class CreateCardRequest {
        @NotBlank(message = "UID карты обязателен")
        @Size(max = 64, message = "UID карты не может быть длиннее 64 символов")
        @Pattern(regexp = "^[A-Za-z0-9:_-]{4,64}$", message = "UID карты содержит недопустимые символы")
        private String uid;

        @NotNull(message = "personId обязателен")
        private UUID personId;
    }
}


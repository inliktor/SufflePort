package org.suffleport.zwloader.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import org.suffleport.zwloader.domain.Card;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.service.CardService;
import org.suffleport.zwloader.service.PersonnelService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/personnel")
public class PersonnelController {

    private final PersonnelService personnelService;
    private final CardService cardService;

    public PersonnelController(PersonnelService personnelService, CardService cardService) {
        this.personnelService = personnelService;
        this.cardService = cardService;
    }

    @GetMapping
    public Object getAll(@RequestParam(name = "page", required = false) Integer page,
                         @RequestParam(name = "size", required = false) Integer size,
                         @RequestParam(name = "sort", required = false) String sortBy,
                         @RequestParam(name = "dir", required = false) String sortDir,
                         @RequestParam(name = "query", required = false) String query) {
        if (PaginationSupport.isPaged(page, size)) {
            return personnelService.listPage(query, PaginationSupport.buildPageable(page, size, sortBy, sortDir,
                    "lastName", "lastName", "firstName", "createdAt"));
        }
        return personnelService.listAll(query);
    }

    @GetMapping("/{id}")
    public Personnel getById(@PathVariable UUID id) {
        return personnelService.getOrThrow(id);
    }

    @PostMapping
    public Personnel create(@Valid @RequestBody PersonnelRequest body) {
        return personnelService.create(
                body.getLastName(),
                body.getFirstName(),
                body.getMiddleName(),
                body.getDateOfBirth(),
                body.getPositionId(),
                body.getPhone(),
                body.getPhotoBase64()
        );
    }

    @PutMapping("/{id}")
    public Personnel update(@PathVariable UUID id, @Valid @RequestBody PersonnelRequest body) {
        return personnelService.update(
                id,
                body.getLastName(),
                body.getFirstName(),
                body.getMiddleName(),
                body.getDateOfBirth(),
                body.getPositionId(),
                body.getPhone(),
                body.getPhotoBase64()
        );
    }

    @GetMapping("/{id}/cards")
    public List<Card> cards(@PathVariable UUID id) {
        personnelService.getOrThrow(id);
        return cardService.findByPerson(id);
    }

    @PostMapping("/{id}/cards")
    public Card assignCard(@PathVariable UUID id, @Valid @RequestBody PersonnelCardRequest body) {
        personnelService.getOrThrow(id);
        return cardService.createCard(body.getUid(), id);
    }

    @PutMapping("/{id}/cards/{uid}/reassign")
    public Card reassignCard(@PathVariable UUID id, @PathVariable String uid) {
        personnelService.getOrThrow(id);
        return cardService.reassignOwner(uid, id);
    }

    @DeleteMapping("/{id}/cards/{uid}")
    public void deleteCard(@PathVariable UUID id, @PathVariable String uid) {
        personnelService.getOrThrow(id);
        Card card = cardService.getByUidOrThrow(uid);
        if (card.getPerson() == null || !id.equals(card.getPerson().getId())) {
            throw new IllegalArgumentException("Карта не привязана к выбранному сотруднику");
        }
        cardService.deleteByUid(uid);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        personnelService.delete(id);
    }

    @Data
    public static class PersonnelRequest {
        @Size(max = 100, message = "Фамилия не может быть длиннее 100 символов")
        @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Фамилия содержит недопустимые символы")
        private String lastName;

        @Size(max = 100, message = "Имя не может быть длиннее 100 символов")
        @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Имя содержит недопустимые символы")
        private String firstName;

        @Size(max = 100, message = "Отчество не может быть длиннее 100 символов")
        @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Отчество содержит недопустимые символы")
        private String middleName;

        @PastOrPresent(message = "Дата рождения не может быть в будущем")
        private LocalDate dateOfBirth;

        private UUID positionId;

        @Size(max = 30, message = "Телефон не может быть длиннее 30 символов")
        @Pattern(regexp = "^(?:\\+7|7|8)[0-9()\\-\\s]{10,20}$", message = "Телефон должен быть российским номером")
        private String phone;

        @Size(max = 10000000, message = "Фото слишком большое")
        private String photoBase64;
    }

    @Data
    public static class PersonnelCardRequest {
        @NotBlank(message = "UID карты обязателен")
        @Size(max = 64, message = "UID карты не может быть длиннее 64 символов")
        @Pattern(regexp = "^[A-Za-z0-9:_-]{4,64}$", message = "UID карты содержит недопустимые символы")
        private String uid;
    }
}

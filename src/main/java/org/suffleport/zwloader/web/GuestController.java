package org.suffleport.zwloader.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.domain.Guest;
import org.suffleport.zwloader.service.GuestService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/guests")
@RequiredArgsConstructor
public class GuestController {

    private final GuestService guestService;

    @GetMapping
    public Object listAll(@RequestParam(name = "page", required = false) Integer page,
                          @RequestParam(name = "size", required = false) Integer size,
                          @RequestParam(name = "sort", required = false) String sortBy,
                          @RequestParam(name = "dir", required = false) String sortDir,
                          @RequestParam(name = "query", required = false) String query) {
        if (PaginationSupport.isPaged(page, size)) {
            return guestService.listPage(query, PaginationSupport.buildPageable(page, size, sortBy, sortDir,
                    "lastName", "lastName", "firstName", "createdAt"));
        }
        return guestService.listAll(query);
    }

    @GetMapping("/{id}")
    public Guest get(@PathVariable UUID id) { return guestService.getOrThrow(id); }

    @PostMapping
    public Guest create(@Valid @RequestBody CreateGuestRequest req) {
        return guestService.create(req.getLastName(), req.getFirstName(), req.getMiddleName(), req.getPhone(), req.getCompany(), req.getPhotoBase64());
    }

    @PutMapping("/{id}")
    public Guest update(@PathVariable UUID id, @Valid @RequestBody UpdateGuestRequest req) {
        return guestService.update(id, req.getLastName(), req.getFirstName(), req.getMiddleName(), req.getPhone(), req.getCompany(), req.getDocument(), req.getNotes(), req.getPhotoBase64());
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) { guestService.delete(id); }

    @GetMapping("/search/by-last-name")
    public List<Guest> byLastName(@RequestParam("lastName") String lastName) { return guestService.findByLastName(lastName); }

    @GetMapping("/search/by-document")
    public List<Guest> byDocument(@RequestParam("q") String fragment) { return guestService.searchByDocument(fragment); }

    @Data
    public static class CreateGuestRequest {
        @Size(max = 100, message = "Фамилия не может быть длиннее 100 символов")
        @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Фамилия содержит недопустимые символы")
        private String lastName;

        @Size(max = 100, message = "Имя не может быть длиннее 100 символов")
        @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Имя содержит недопустимые символы")
        private String firstName;

        @Size(max = 100, message = "Отчество не может быть длиннее 100 символов")
        @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Отчество содержит недопустимые символы")
        private String middleName;

        @Size(max = 30, message = "Телефон не может быть длиннее 30 символов")
        @Pattern(regexp = "^(?:\\+7|7|8)[0-9()\\-\\s]{10,20}$", message = "Телефон должен быть российским номером")
        private String phone;

        @Size(max = 200, message = "Компания не может быть длиннее 200 символов")
        private String company;

        @Size(max = 10000000, message = "Фото слишком большое")
        private String photoBase64;
    }

    @Data
    public static class UpdateGuestRequest {
        @Size(max = 100, message = "Фамилия не может быть длиннее 100 символов")
        @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Фамилия содержит недопустимые символы")
        private String lastName;

        @Size(max = 100, message = "Имя не может быть длиннее 100 символов")
        @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Имя содержит недопустимые символы")
        private String firstName;

        @Size(max = 100, message = "Отчество не может быть длиннее 100 символов")
        @Pattern(regexp = "^[\\p{L}][\\p{L} .'-]*$", message = "Отчество содержит недопустимые символы")
        private String middleName;

        @Size(max = 30, message = "Телефон не может быть длиннее 30 символов")
        @Pattern(regexp = "^(?:\\+7|7|8)[0-9()\\-\\s]{10,20}$", message = "Телефон должен быть российским номером")
        private String phone;

        @Size(max = 200, message = "Компания не может быть длиннее 200 символов")
        private String company;

        @Size(max = 200, message = "Документ не может быть длиннее 200 символов")
        private String document;

        @Size(max = 500, message = "Примечания не могут быть длиннее 500 символов")
        private String notes;

        @Size(max = 10000000, message = "Фото слишком большое")
        private String photoBase64;
    }
}


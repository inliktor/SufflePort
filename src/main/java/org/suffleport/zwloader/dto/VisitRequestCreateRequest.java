package org.suffleport.zwloader.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Данные для оформления заявки на гостевой визит.
 *
 * Поля посетителя хранятся в заявке как снимок: согласующий должен видеть
 * ровно то, что указал автор, даже если карточку гостя потом изменят.
 */
public record VisitRequestCreateRequest(

        @NotNull(message = "Сотрудник-хост обязателен")
        UUID hostPersonId,

        @NotBlank(message = "Фамилия посетителя обязательна")
        @Size(max = 100, message = "Фамилия не длиннее 100 символов")
        String visitorLastName,

        @NotBlank(message = "Имя посетителя обязательно")
        @Size(max = 100, message = "Имя не длиннее 100 символов")
        String visitorFirstName,

        @Size(max = 100, message = "Отчество не длиннее 100 символов")
        String visitorMiddleName,

        @Size(max = 200, message = "Организация не длиннее 200 символов")
        String organization,

        @Size(max = 32, message = "Телефон не длиннее 32 символов")
        String phone,

        @Size(max = 300, message = "Цель посещения не длиннее 300 символов")
        String purpose,

        @NotNull(message = "Начало посещения обязательно")
        OffsetDateTime plannedFrom,

        @NotNull(message = "Окончание посещения обязательно")
        OffsetDateTime plannedTo
) {
}

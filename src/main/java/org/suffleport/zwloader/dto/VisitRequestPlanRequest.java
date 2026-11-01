package org.suffleport.zwloader.dto;

import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * Изменение плана черновика: время посещения и цель.
 * Поля необязательные — меняется только то, что передали.
 */
public record VisitRequestPlanRequest(

        OffsetDateTime plannedFrom,

        OffsetDateTime plannedTo,

        @Size(max = 300, message = "Цель посещения не длиннее 300 символов")
        String purpose
) {
}

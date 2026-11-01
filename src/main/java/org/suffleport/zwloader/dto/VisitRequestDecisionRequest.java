package org.suffleport.zwloader.dto;

import jakarta.validation.constraints.Size;

/**
 * Решение по заявке. Комментарий необязателен при одобрении,
 * но обязателен при отказе — это проверяет сервис, а не аннотация,
 * потому что правило зависит от действия.
 */
public record VisitRequestDecisionRequest(

        @Size(max = 1000, message = "Комментарий не длиннее 1000 символов")
        String comment
) {
}

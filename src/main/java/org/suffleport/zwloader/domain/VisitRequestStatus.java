package org.suffleport.zwloader.domain;

import java.util.Locale;

/**
 * Статус заявки на гостевой визит.
 *
 * Намеренно сделан отдельным перечислением, а не строкой: в проекте уже есть
 * {@link GuestVisit}, у которого статус описывает само посещение. Заявка и визит
 * живут по разным правилам, поэтому смешивать их статусы нельзя.
 */
public enum VisitRequestStatus {

    /** Черновик. Автор ещё ничего не отправил, заявка не видна согласующему. */
    DRAFT,

    /** Отправлена на согласование. Появляется в очереди. */
    SUBMITTED,

    /** Одобрена. На этом этапе создаётся ожидаемый визит. */
    APPROVED,

    /** Отклонена с обязательным комментарием. */
    REJECTED,

    /** Отменена автором или администратором. */
    CANCELLED;

    /**
     * Разбор статуса из строки. Используется в местах, где значение приходит
     * снаружи (фильтры, старые записи в БД).
     */
    public static VisitRequestStatus parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Статус заявки обязателен");
        }
        try {
            return VisitRequestStatus.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Неизвестный статус заявки: " + raw);
        }
    }

    public boolean isFinal() {
        return this == REJECTED || this == CANCELLED;
    }
}

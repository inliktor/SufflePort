package org.suffleport.zwloader.dto;

import java.util.List;

/**
 * Сводка по файлу импорта: сколько строк принято, сколько пропущено
 * как дубли и сколько содержит ошибки.
 */
public record ImportReport(
        int total,
        int ok,
        int skipped,
        int errors,
        boolean canApply,
        List<ImportRowResult> rows
) {
}

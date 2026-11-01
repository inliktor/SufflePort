package org.suffleport.zwloader.dto;

import java.util.List;

/**
 * Результат проверки одной строки файла импорта.
 */
public record ImportRowResult(int rowNumber, String fullName, String status, List<String> messages) {
}

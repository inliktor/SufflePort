package org.suffleport.zwloader.validation;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public final class ValidationRules {

    private static final Pattern HUMAN_NAME_PATTERN = Pattern.compile("^[\\p{L}][\\p{L} .'-]*$");
    private static final Pattern CARD_UID_PATTERN = Pattern.compile("^[A-Za-z0-9:_-]{4,64}$");
    private static final Pattern SYSTEM_ID_PATTERN = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{1,99}$");
    private static final Pattern BASE64_PATTERN = Pattern.compile("^(?:data:[^;]+;base64,)?[A-Za-z0-9+/=\\r\\n]+$");
    private static final Set<String> VISIT_STATUSES = Set.of("PLANNED", "ACTIVE", "FINISHED", "CANCELLED", "DENIED");

    private ValidationRules() {
    }

    public static String requireHumanName(String value, String fieldName, int maxLength) {
        String normalized = normalizeRequiredText(value, fieldName, maxLength);
        if (!HUMAN_NAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " содержит недопустимые символы");
        }
        return normalized;
    }

    public static String optionalHumanName(String value, String fieldName, int maxLength) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " не может быть длиннее " + maxLength + " символов");
        }
        if (!HUMAN_NAME_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " содержит недопустимые символы");
        }
        return normalized;
    }

    public static String normalizeRequiredText(String value, String fieldName, int maxLength) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            throw new IllegalArgumentException(fieldName + " обязательно");
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " не может быть длиннее " + maxLength + " символов");
        }
        return normalized;
    }

    public static String normalizeOptionalText(String value, String fieldName, int maxLength) {
        String normalized = normalizeNullableText(value);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + " не может быть длиннее " + maxLength + " символов");
        }
        return normalized;
    }

    public static String normalizePhone(String phone) {
        String normalized = normalizeNullableText(phone);
        if (normalized == null) {
            return null;
        }
        String digits = normalized.replaceAll("[\\s()-]", "");
        if (!digits.matches("^(\\+7|7|8)\\d{10}$")) {
            throw new IllegalArgumentException("Телефон должен быть российским номером в формате +7XXXXXXXXXX");
        }
        if (digits.startsWith("8")) {
            return "+7" + digits.substring(1);
        }
        if (digits.startsWith("7")) {
            return "+7" + digits.substring(1);
        }
        return digits;
    }

    public static String normalizeCardUid(String uid) {
        String normalized = normalizeRequiredText(uid, "UID карты", 64);
        if (!CARD_UID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("UID карты содержит недопустимые символы");
        }
        return normalized;
    }

    public static String normalizeOptionalCardUid(String uid) {
        String normalized = normalizeNullableText(uid);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 64 || !CARD_UID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("UID карты содержит недопустимые символы");
        }
        return normalized;
    }

    public static String normalizeSystemId(String id, String fieldName) {
        String normalized = normalizeRequiredText(id, fieldName, 100);
        if (!SYSTEM_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " содержит недопустимые символы");
        }
        return normalized;
    }

    public static String normalizeOptionalSystemId(String id, String fieldName) {
        String normalized = normalizeNullableText(id);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 100 || !SYSTEM_ID_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException(fieldName + " содержит недопустимые символы");
        }
        return normalized;
    }

    public static String normalizePhotoBase64(String photoBase64) {
        String normalized = normalizeNullableText(photoBase64);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 10_000_000) {
            throw new IllegalArgumentException("Фото слишком большое");
        }
        if (!BASE64_PATTERN.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Фото должно быть корректной base64-строкой");
        }
        return normalized;
    }

    public static String normalizeRtspUrl(String rtspUrl) {
        String normalized = normalizeNullableText(rtspUrl);
        if (normalized == null) {
            return null;
        }
        if (normalized.length() > 500) {
            throw new IllegalArgumentException("RTSP URL не может быть длиннее 500 символов");
        }
        try {
            URI uri = URI.create(normalized);
            String scheme = uri.getScheme();
            if (scheme == null || !("rtsp".equalsIgnoreCase(scheme) || "rtsps".equalsIgnoreCase(scheme))) {
                throw new IllegalArgumentException("RTSP URL должен начинаться с rtsp:// или rtsps://");
            }
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("RTSP URL имеет некорректный формат");
        }
        return normalized;
    }

    public static LocalDate validateDateOfBirth(LocalDate dateOfBirth) {
        if (dateOfBirth == null) {
            return null;
        }
        LocalDate today = LocalDate.now();
        if (dateOfBirth.isAfter(today)) {
            throw new IllegalArgumentException("Дата рождения не может быть в будущем");
        }
        if (dateOfBirth.isBefore(today.minusYears(120))) {
            throw new IllegalArgumentException("Дата рождения выглядит недостоверной");
        }
        return dateOfBirth;
    }

    public static void validateDateRange(OffsetDateTime start, OffsetDateTime end, String startField, String endField, boolean required) {
        if (required && (start == null || end == null)) {
            throw new IllegalArgumentException(startField + " и " + endField + " обязательны");
        }
        if (start == null || end == null) {
            return;
        }
        if (!end.isAfter(start)) {
            throw new IllegalArgumentException(endField + " должно быть позже, чем " + startField);
        }
    }

    public static String normalizeVisitStatus(String status) {
        String normalized = normalizeRequiredText(status, "Статус визита", 30).toUpperCase(Locale.ROOT);
        if (!VISIT_STATUSES.contains(normalized)) {
            throw new IllegalArgumentException("Недопустимый статус визита: " + normalized);
        }
        return normalized;
    }

    public static Integer validateAccessLevel(Integer accessLevel) {
        if (accessLevel == null) {
            return null;
        }
        if (accessLevel < 0 || accessLevel > 100) {
            throw new IllegalArgumentException("Уровень доступа должен быть в диапазоне от 0 до 100");
        }
        return accessLevel;
    }

    public static String normalizeEmail(String email) {
        String normalized = normalizeRequiredText(email, "Email", 254).toLowerCase(Locale.ROOT);
        return normalized;
    }

    public static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128) {
            throw new IllegalArgumentException("Пароль должен содержать от 8 до 128 символов");
        }
        if (!password.matches(".*\\d.*") || !password.matches(".*[\\p{L}A-Za-z].*")) {
            throw new IllegalArgumentException("Пароль должен содержать хотя бы одну букву и одну цифру");
        }
    }

    public static String normalizeNullableText(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }
}
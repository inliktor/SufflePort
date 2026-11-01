package org.suffleport.zwloader.validation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты для аннотации @RussianEmail.
 * Проверяем: российские домены (.ru, .su, .рф) — разрешены.
 * Иностранные домены (gmail.com, yahoo.com и др.) — запрещены для регистрации.
 * null и blank — пропускаются (поле необязательное на уровне аннотации).
 */
class RussianEmailValidatorTest {

    private RussianEmailValidator validator;

    @BeforeEach
    void setUp() {
        validator = new RussianEmailValidator();
    }

    // ==================== Российские домены — должны проходить ====================

    @ParameterizedTest(name = "российский email: \"{0}\"")
    @ValueSource(strings = {
            "user@mail.ru",
            "admin@yandex.ru",
            "support@company.ru",
            "test@example.su",
            "name@corp.su",
            "info@firm.рф",
            "USER@MAIL.RU",         // регистронезависимый
            "User.Name@Company.Ru"  // смешанный регистр
    })
    void russianDomain_isValid(String email) {
        assertTrue(validator.isValid(email, null),
                "Email с российским доменом должен быть допустим: " + email);
    }

    // ==================== Иностранные домены — должны отклоняться ====================

    @ParameterizedTest(name = "иностранный email: \"{0}\"")
    @ValueSource(strings = {
            "user@gmail.com",
            "user@yahoo.com",
            "user@hotmail.com",
            "user@outlook.com",
            "user@icloud.com",
            "user@mail.de",
            "user@company.io",
            "user@example.org",
            "user@domain.net",
            "user@corp.co.uk"
    })
    void foreignDomain_isInvalid(String email) {
        assertFalse(validator.isValid(email, null),
                "Email с иностранным доменом должен быть запрещён для регистрации: " + email);
    }

    // ==================== Граничные случаи ====================

    @Test
    void nullValue_isValid() {
        // null разрешён — поле может быть необязательным
        assertTrue(validator.isValid(null, null));
    }

    @Test
    void blankValue_isValid() {
        // Пустая строка разрешена на уровне аннотации (обязательность задаётся @NotBlank)
        assertTrue(validator.isValid("  ", null));
    }

    @Test
    void emptyString_isValid() {
        assertTrue(validator.isValid("", null));
    }

    @Test
    void emailWithoutAt_isInvalid() {
        assertFalse(validator.isValid("notanemail.ru", null));
    }

    @Test
    void emailWithOnlyAt_isInvalid() {
        assertFalse(validator.isValid("@mail.ru", null));
    }
}

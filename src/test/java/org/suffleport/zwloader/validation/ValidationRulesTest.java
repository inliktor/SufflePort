package org.suffleport.zwloader.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Юнит-тесты для ValidationRules.
 * Проверяем граничные значения: некорректные даты (1742, 2300), неверные телефоны,
 * недопустимые символы в именах, слабые пароли — всё должно бросать исключение.
 */
class ValidationRulesTest {

    // ==================== Дата рождения ====================

    @Test
    void dateOfBirth_valid_accepted() {
        LocalDate valid = LocalDate.of(1990, 6, 15);
        assertEquals(valid, ValidationRules.validateDateOfBirth(valid));
    }

    @Test
    void dateOfBirth_null_accepted() {
        // Поле необязательное — null должен проходить
        assertNull(ValidationRules.validateDateOfBirth(null));
    }

    @Test
    void dateOfBirth_year1742_rejected() {
        // Год 1742 — за пределами 120 лет
        LocalDate ancient = LocalDate.of(1742, 1, 1);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ValidationRules.validateDateOfBirth(ancient)
        );
        assertTrue(ex.getMessage().contains("недостоверн"), "Должно говорить о недостоверной дате");
    }

    @Test
    void dateOfBirth_year2300_rejected() {
        // Год 2300 — дата в будущем
        LocalDate future = LocalDate.of(2300, 1, 1);
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ValidationRules.validateDateOfBirth(future)
        );
        assertTrue(ex.getMessage().contains("будущ"), "Должно говорить о дате в будущем");
    }

    @Test
    void dateOfBirth_tomorrow_rejected() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> ValidationRules.validateDateOfBirth(LocalDate.now().plusDays(1))
        );
        assertTrue(ex.getMessage().contains("будущ"));
    }

    @Test
    void dateOfBirth_today_accepted() {
        // Сегодняшняя дата — не в будущем, может быть новорождённый
        assertDoesNotThrow(() -> ValidationRules.validateDateOfBirth(LocalDate.now()));
    }

    @Test
    void dateOfBirth_exactly120YearsAgo_rejected() {
        // Ровно 120+ лет назад — за границей допустимого диапазона
        LocalDate tooOld = LocalDate.now().minusYears(121);
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.validateDateOfBirth(tooOld));
    }

    // ==================== Пароль ====================

    @ParameterizedTest(name = "слабый пароль: \"{0}\"")
    @ValueSource(strings = {
            "abc",           // слишком короткий
            "12345678",      // нет букв
            "abcdefgh",      // нет цифр
            "short1",        // меньше 8 символов
            ""               // пустой
    })
    void password_weak_rejected(String weak) {
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.validatePassword(weak),
                "Слабый пароль должен быть отклонён: " + weak);
    }

    @ParameterizedTest(name = "сильный пароль: \"{0}\"")
    @ValueSource(strings = {
            "Password1",
            "Пароль123",
            "secure_pass_99",
            "Ab1cde!@#fgh"
    })
    void password_strong_accepted(String strong) {
        assertDoesNotThrow(() -> ValidationRules.validatePassword(strong),
                "Сильный пароль должен быть принят: " + strong);
    }

    @Test
    void password_null_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.validatePassword(null));
    }

    @Test
    void password_tooLong_rejected() {
        String tooLong = "Aa1" + "x".repeat(130); // > 128 символов
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.validatePassword(tooLong));
    }

    // ==================== Телефон ====================

    @ParameterizedTest(name = "корректный телефон: \"{0}\"")
    @ValueSource(strings = {
            "+79991234567",
            "89991234567",
            "+7 (999) 123-45-67",
            "8 (999) 123-45-67",
            "79991234567"
    })
    void phone_valid_accepted(String phone) {
        assertDoesNotThrow(() -> ValidationRules.normalizePhone(phone),
                "Корректный телефон должен быть принят: " + phone);
    }

    @ParameterizedTest(name = "некорректный телефон: \"{0}\"")
    @ValueSource(strings = {
            "+1234567890",       // не российский код
            "1234567890",        // нет кода страны
            "+7999123456",       // 9 цифр вместо 10
            "abc",               // не цифры
            "123"                // слишком короткий
    })
    void phone_invalid_rejected(String phone) {
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.normalizePhone(phone),
                "Некорректный телефон должен быть отклонён: " + phone);
    }

    @Test
    void phone_null_returnsNull() {
        // Телефон необязательный
        assertNull(ValidationRules.normalizePhone(null));
    }

    // ==================== ФИО (имена) ====================

    @ParameterizedTest(name = "корректное имя: \"{0}\"")
    @ValueSource(strings = {
            "Иванов",
            "Иван-Пётр",
            "O'Brien",
            "Мария Иванова"
    })
    void humanName_valid_accepted(String name) {
        assertDoesNotThrow(() -> ValidationRules.requireHumanName(name, "Имя", 100));
    }

    @ParameterizedTest(name = "некорректное имя: \"{0}\"")
    @ValueSource(strings = {
            "Ivan123",         // цифры в имени
            "Иванов!",         // спецсимволы
            "Иванов<script>",  // попытка XSS
            "12345"            // только цифры
    })
    void humanName_invalid_rejected(String name) {
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.requireHumanName(name, "Имя", 100),
                "Некорректное имя должно быть отклонено: " + name);
    }

    @Test
    void humanName_blank_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.requireHumanName("  ", "Имя", 100));
    }

    @Test
    void humanName_null_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.requireHumanName(null, "Имя", 100));
    }

    @Test
    void humanName_tooLong_rejected() {
        String tooLong = "А".repeat(60);
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.requireHumanName(tooLong, "Имя", 50));
    }

    // ==================== Email (нормализация) ====================

    @Test
    void normalizeEmail_trims_and_lowercases() {
        assertEquals("user@mail.ru", ValidationRules.normalizeEmail("  USER@Mail.RU  "));
    }

    @Test
    void normalizeEmail_blank_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.normalizeEmail("   "));
    }

    @Test
    void normalizeEmail_null_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.normalizeEmail(null));
    }

    // ==================== Уровень доступа ====================

    @Test
    void accessLevel_valid_accepted() {
        assertDoesNotThrow(() -> ValidationRules.validateAccessLevel(0));
        assertDoesNotThrow(() -> ValidationRules.validateAccessLevel(50));
        assertDoesNotThrow(() -> ValidationRules.validateAccessLevel(100));
    }

    @Test
    void accessLevel_negative_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.validateAccessLevel(-1));
    }

    @Test
    void accessLevel_above100_rejected() {
        assertThrows(IllegalArgumentException.class,
                () -> ValidationRules.validateAccessLevel(101));
    }

    @Test
    void accessLevel_null_returnsNull() {
        assertNull(ValidationRules.validateAccessLevel(null));
    }
}

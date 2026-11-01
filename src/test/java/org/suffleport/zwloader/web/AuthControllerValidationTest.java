package org.suffleport.zwloader.web;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Role;
import org.suffleport.zwloader.repository.RoleRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты валидации AuthController.
 * Цель: убедиться, что бекенд ОТКЛОНЯЕТ невалидные данные и ПРИНИМАЕТ корректные.
 *
 * Покрываем:
 *  - /api/auth/register: иностранные домены, слабые пароли, пустые поля → 400
 *  - /api/auth/register: дублирующийся email → 409
 *  - /api/auth/login: пустые поля → 400; иностранный email → 401 (не 400)
 *
 * Используем H2 in-memory БД (профиль test). /api/auth/** открыт без аутентификации.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerValidationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    RoleRepository roleRepository;

    @BeforeEach
    void seedRoles() {
        // Роли нужны для register (ищет roleId=0 = USER).
        // H2 создаёт схему из JPA-энтити, но строки заполняем вручную.
        if (roleRepository.findByRoleId(0).isEmpty()) {
            roleRepository.save(new Role(0, "USER"));
        }
        if (roleRepository.findByRoleId(1).isEmpty()) {
            roleRepository.save(new Role(1, "ADMIN"));
        }
    }

    // ── Вспомогательные методы ───────────────────────────────────────────────
    private static String registerJson(String email, String password) {
        return """
                {"email": "%s", "username": "%s", "password": "%s"}
                """.formatted(email, email, password);
    }

    private static String loginJson(String email, String password) {
        return """
                {"username": "%s", "password": "%s"}
                """.formatted(email, password);
    }

    // ==================== Регистрация: иностранные домены → 400 ====================

    @ParameterizedTest(name = "иностранный домен: {0}")
    @ValueSource(strings = {
            "user@gmail.com",
            "admin@yahoo.com",
            "test@hotmail.com",
            "info@outlook.com",
            "user@company.de"
    })
    void register_foreignEmail_returns400(String email) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "StrongPass1")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Ошибка валидации данных"));
    }

    // ==================== Регистрация: российские домены → 200 ====================

    @ParameterizedTest(name = "российский домен: {0}")
    @ValueSource(strings = {
            "user@mail.ru",
            "admin@yandex.ru",
            "test@corp.su"
    })
    void register_russianEmail_returns200(String email) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, "StrongPass1")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Регистрация успешна"))
                .andExpect(jsonPath("$.email").value(email.toLowerCase()));
    }

    // ==================== Регистрация: слабые пароли → 400 ====================

    @ParameterizedTest(name = "слабый пароль: \"{0}\"")
    @ValueSource(strings = {
            "abc",           // слишком короткий
            "12345678",      // нет букв
            "abcdefgh",      // нет цифр
            "1234567",       // 7 символов
            "short"          // короткий без цифр
    })
    void register_weakPassword_returns400(String password) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("valid@mail.ru", password)))
                .andExpect(status().isBadRequest());
    }

    // ==================== Регистрация: пустые / отсутствующие поля → 400 ====================

    @Test
    void register_blankEmail_returns400() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "", "username": "", "password": "StrongPass1"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_missingPassword_returns400() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "user@mail.ru", "username": "user@mail.ru"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_emptyJson_returns400() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void register_malformedJson_returns400() throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not-json"))
                .andExpect(status().isBadRequest());
    }

    // ==================== Регистрация: дублирующийся email → 409 ====================

    @Test
    void register_duplicateEmail_returns409() throws Exception {
        String json = registerJson("duplicate@mail.ru", "StrongPass1");

        // Первая регистрация — успех
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        // Вторая с тем же email — конфликт
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Пользователь с таким email уже существует"));
    }

    // ==================== Вход: пустые поля → 400 ====================

    @Test
    void login_blankUsername_returns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("", "StrongPass1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_blankPassword_returns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("user@mail.ru", "")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_emptyJson_returns400() throws Exception {
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ==================== Вход: gmail — валидация НЕ блокирует → 401 ====================

    @Test
    void login_gmailEmail_notBlockedByValidation_returns401() throws Exception {
        // @RussianEmail убран из LoginRequest — пользователи с gmail могут войти.
        // Если аккаунт не найден — 401 (неверные учётные данные), а НЕ 400 (ошибка валидации).
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("olduser@gmail.com", "StrongPass1")))
                .andExpect(status().isUnauthorized());
    }

    // ==================== Полный цикл: регистрация → вход ====================

    @Test
    void register_then_login_full_success() throws Exception {
        String email = "newuser@company.ru";
        String password = "StrongPass1";

        // Шаг 1: регистрация
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Регистрация успешна"));

        // Шаг 2: вход с теми же данными
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson(email, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.token").exists());
    }

    @Test
    void login_wrongPassword_returns401() throws Exception {
        // Регистрируем пользователя
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("checkpass@mail.ru", "CorrectPass1")))
                .andExpect(status().isOk());

        // Пытаемся войти с неверным паролем
        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("checkpass@mail.ru", "WrongPass999")))
                .andExpect(status().isUnauthorized());
    }
}

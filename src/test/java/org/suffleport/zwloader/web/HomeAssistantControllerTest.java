package org.suffleport.zwloader.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Card;
import org.suffleport.zwloader.domain.Guest;
import org.suffleport.zwloader.domain.GuestVisit;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.repository.CardRepository;
import org.suffleport.zwloader.repository.GuestRepository;
import org.suffleport.zwloader.repository.GuestVisitRepository;
import org.suffleport.zwloader.repository.PersonnelRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Тесты интеграции контроллера Home Assistant.
 * Задача: убедиться, что поведение эндпоинтов соответствует бизнес-логике
 * (регистрация карты, переключение направления прохода, проверка доступа).
 * Используем профиль test + H2 in-memory БД.
 * Security: @WithMockUser упрощает аутентификацию (Basic Security включена).
 */

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "zwloader", password = "change_me")
@ActiveProfiles("test")
@Transactional
class HomeAssistantControllerTest {

    @Autowired
    MockMvc mvc; // MockMvc: позволяет вызывать REST без запуска реального сервера.

    @Autowired
    PersonnelRepository personnelRepository; // Репозиторий сотрудников для подготовки данных.

    @Autowired
    CardRepository cardRepository; // Репозиторий карт (нужно для NFC toggle теста).

        @Autowired
        GuestRepository guestRepository;

        @Autowired
        GuestVisitRepository guestVisitRepository;

    @Test
    void hasAccessUnknownReturnsFalse() throws Exception {
        // Проверяем: неизвестная карта -> доступ запрещен (false).
        mvc.perform(get("/api/ha/has-access").param("uid", "UNKNOWN123"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));

        mvc.perform(get("/api/ha/has_access").param("uid", "UNKNOWN123"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));

        mvc.perform(post("/api/ha/has_access")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"p_uid\":\"UNKNOWN123\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void registerCardCompatWithTokenWorks() throws Exception {
        Personnel p = new Personnel("Совместимость", "ХА", "Тест", LocalDate.of(1991, 3, 3), null, null);
        personnelRepository.save(p);

        String body = """
                {
                  "uid": "CARD-COMPAT-1",
                  "name": "%s"
                }
                """.formatted(p.getFullName());

        mvc.perform(post("/api/ha/register-card")
                        .param("token", "ha_local_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("auto_assigned"))
                .andExpect(jsonPath("$.uid").value("CARD-COMPAT-1"));
    }

    @Test
    void scanCardUnknownNeedsAssignment() throws Exception {
        // Скан карты без заранее созданной записи -> статус needs_assignment
        // (т.к. карта не существует и не может быть автоматически сопоставлена).
        mvc.perform(post("/api/cards/registration/start").param("ttlSec", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        mvc.perform(post("/api/ha/card/scan").param("uid", "CARD001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("needs_assignment"));
    }

    @Test
    void registerCardByNameAutoAssign() throws Exception {
        // 1. Создаем сотрудника.
        Personnel p = new Personnel("Иванов", "Иван", "Иванович", LocalDate.of(1990,1,1), null, null);
        personnelRepository.save(p);
        String fullName = p.getFullName(); // full_name генерируется из ФИО.

        // 2. Регистрируем карту по ФИО -> должна автоматически привязаться.
        mvc.perform(post("/api/ha/card/register-by-name")
                        .param("uid", "CARD777")
                        .param("name", fullName)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("auto_assigned"))
                .andExpect(jsonPath("$.person_name").value(fullName));
    }

    @Test
    void nfcToggleDirectionFlips() throws Exception {
        // Подготовка: создаем сотрудника + карту (карта должна существовать, иначе проверка доступа была бы false).
        Personnel p = new Personnel("Иванов", "Петр", "Сергеевич", LocalDate.of(1992,2,2), null, null);
        personnelRepository.save(p);
        Card card = new Card();
        card.setUid("CARDX");
        card.setPerson(p); // карта привязана к сотруднику (NOT NULL constraint).
        card.setActive(true);
        cardRepository.save(card);

        // Первый проход -> направление IN (начальная точка цикла).
        mvc.perform(post("/api/ha/nfc/toggle").param("uid", "CARDX").param("device", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("IN"));

        // Второй быстрый проход в окне debounce -> должен вернуться тот же IN.
        mvc.perform(post("/api/ha/nfc/toggle").param("uid", "CARDX").param("device", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("IN"));

        // И при этом не должно создаваться дублей событий.
        mvc.perform(get("/api/events/by-card/{uid}", "CARDX"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // Legacy HA payload variant with p_uid should also work.
        mvc.perform(post("/api/ha/nfc/toggle").param("p_uid", "CARDX").param("device", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("IN"));
    }

    @Test
    void nfcToggleUnknownCardReturnsBadRequest() throws Exception {
        mvc.perform(post("/api/ha/nfc/toggle").param("uid", "UNKNOWN-CARD-999").param("device", "dev-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void faceToggleDirectionFlips() throws Exception {
        // В режиме распознавания лица сотрудник может быть не найден (тест использует имя без создания Personnel).
        // Логика: первый вызов -> IN, второй -> OUT (цикл по имени лица).
        mvc.perform(post("/api/ha/face/toggle")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("face", "Test User")
                        .param("device", "cam-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("IN"));
        mvc.perform(post("/api/ha/face/toggle")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("face", "Test User")
                        .param("device", "cam-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("OUT"));
    }

                @Test
                void securityAlertReturnsOk() throws Exception {
                                mvc.perform(post("/api/security/alert")
                                                                                                .param("token", "ha_local_token")
                                                                                                .contentType(MediaType.APPLICATION_JSON)
                                                                                                .content("""
                                                                                                                                {
                                                                                                                                        "alert_type": "unauthorized_access",
                                                                                                                                        "uid": "UNKNOWN-SECURITY-1",
                                                                                                                                        "device_id": "door-a"
                                                                                                                                }
                                                                                                                                """))
                                                                .andExpect(status().isOk())
                                                                .andExpect(jsonPath("$.status").value("ok"))
                                                                .andExpect(jsonPath("$.alert_type").value("unauthorized_access"));
                }

    @Test
    void qrWebhookActivatesVisitFromNestedPayload() throws Exception {
        GuestVisit visit = createVisitForNow();

        String payload = """
                {
                  "payload": {
                    "code": "VISIT:%d"
                  }
                }
                """.formatted(visit.getId());

        mvc.perform(post("/api/ha/qr_webhook")
                        .param("token", "ha_local_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Визит " + visit.getId())));

        mvc.perform(get("/api/guest-visits/{id}", visit.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void qrWebhookRepeatedScanIsIdempotent() throws Exception {
        GuestVisit visit = createVisitForNow();
        String payload = "{\"code\":\"VISIT:" + visit.getId() + "\"}";

        mvc.perform(post("/api/ha/qr_webhook")
                        .param("token", "ha_local_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());

        mvc.perform(post("/api/ha/qr_webhook")
                        .param("token", "ha_local_token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Визит " + visit.getId())));
    }

    private GuestVisit createVisitForNow() {
        Personnel host = new Personnel("Петров", "Иван", "Сергеевич", LocalDate.of(1991, 1, 1), null, null);
        personnelRepository.save(host);

        Guest guest = new Guest("Сидоров", "Гость", null, "+79991234567", "ООО Тест");
        guestRepository.save(guest);

        OffsetDateTime from = OffsetDateTime.now().minusMinutes(10);
        OffsetDateTime to = OffsetDateTime.now().plusMinutes(50);
        GuestVisit visit = new GuestVisit(guest, host, from, to, "Встреча");
        return guestVisitRepository.save(visit);
    }
}

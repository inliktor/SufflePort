package org.suffleport.zwloader.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Card;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.repository.CardRepository;
import org.suffleport.zwloader.repository.DeviceRepository;
import org.suffleport.zwloader.repository.PersonnelRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "zwloader", password = "change_me")
@ActiveProfiles("test")
@Transactional
class EventManualAndDebounceTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PersonnelRepository personnelRepository;

    @Autowired
    CardRepository cardRepository;

        @Autowired
        DeviceRepository deviceRepository;

    @Test
    void manualEventCreatedWithManualSource() throws Exception {
        Personnel p = new Personnel("Иванов", "Ручной", "Тест", LocalDate.of(1990, 5, 5), null, null);
        personnelRepository.save(p);

        String body = """
                {
                  "personId": "%s",
                  "direction": "IN",
                  "reason": "Ручная корректировка"
                }
                """.formatted(p.getId());

        mvc.perform(post("/api/events/manual")
                        .contentType("application/json")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("MANUAL"))
                .andExpect(jsonPath("$.meta.decision").value("Ручная корректировка"));
    }

    @Test
    void nfcDebouncePreventsDuplicateEvent() throws Exception {
        Personnel p = new Personnel("Петров", "Дребезг", "Тест", LocalDate.of(1992, 2, 2), null, null);
        personnelRepository.save(p);

        Card card = new Card();
        card.setUid("CARD-DEBOUNCE-1");
        card.setPerson(p);
        card.setActive(true);
        cardRepository.save(card);

        mvc.perform(post("/api/ha/nfc/toggle").param("uid", card.getUid()).param("device", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("IN"));

        mvc.perform(post("/api/ha/nfc/toggle").param("uid", card.getUid()).param("device", "dev-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("IN"));

        mvc.perform(get("/api/events/by-card/{uid}", card.getUid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].device.id").value("dev-1"));

        assertTrue(deviceRepository.existsById("dev-1"));
    }

    @Test
    void nfcEventCreatedAfterIncludesDeviceForFrontend() throws Exception {
        Personnel p = new Personnel("Сидоров", "Устройство", "Тест", LocalDate.of(1993, 4, 4), null, null);
        personnelRepository.save(p);

        Card card = new Card();
        card.setUid("CARD-DEVICE-RECENT-1");
        card.setPerson(p);
        card.setActive(true);
        cardRepository.save(card);

        String from = OffsetDateTime.now().minusMinutes(1).toString();

        mvc.perform(post("/api/ha/nfc/toggle")
                        .param("uid", card.getUid())
                        .param("device_id", "reader-entrance-1"))
                .andExpect(status().isOk())
                .andExpect(content().string("IN"));

        mvc.perform(get("/api/events/created-after").param("date", from))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.card.uid == 'CARD-DEVICE-RECENT-1')].device.id")
                        .value(org.hamcrest.Matchers.hasItem("reader-entrance-1")));
    }

    @Test
    void haLogFormAcceptsDeviceAliasAndReturnsDeviceId() throws Exception {
        Personnel p = new Personnel("Кузнецов", "Форма", "Тест", LocalDate.of(1994, 6, 6), null, null);
        personnelRepository.save(p);

        Card card = new Card();
        card.setUid("CARD-FORM-DEVICE-1");
        card.setPerson(p);
        card.setActive(true);
        cardRepository.save(card);

        mvc.perform(post("/api/events/log")
                        .contentType("application/x-www-form-urlencoded")
                        .param("token", "ha_local_token")
                        .param("uid", card.getUid())
                        .param("device", "reader-form-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.direction").value("IN"))
                .andExpect(jsonPath("$.deviceId").value("reader-form-1"));
    }

    @Test
    void deviceWithEventsCannotBeDeleted() throws Exception {
        Personnel p = new Personnel("Орлов", "Удаление", "Тест", LocalDate.of(1995, 7, 7), null, null);
        personnelRepository.save(p);

        Card card = new Card();
        card.setUid("CARD-DEVICE-DELETE-1");
        card.setPerson(p);
        card.setActive(true);
        cardRepository.save(card);

        mvc.perform(post("/api/ha/nfc/toggle")
                        .param("uid", card.getUid())
                        .param("device", "reader-delete-1"))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/devices/{id}", "reader-delete-1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Нельзя удалить устройство: по нему уже есть события прохода"));
    }
}

package org.suffleport.zwloader.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.domain.Card;
import org.suffleport.zwloader.repository.CardRepository;
import org.suffleport.zwloader.repository.PersonnelRepository;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.empty;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "zwloader", password = "change_me")
@ActiveProfiles("test")
@Transactional
class PersonnelCardManagementControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PersonnelRepository personnelRepository;

        @Autowired
        CardRepository cardRepository;

    @Test
    void personnelScopedCardEndpointsManageCards() throws Exception {
        Personnel first = savePerson("Иванов", "Иван");
        Personnel second = savePerson("Петров", "Петр");

        mvc.perform(post("/api/personnel/{id}/cards", first.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "uid": "CARD-PERSON-1"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("CARD-PERSON-1"));

        mvc.perform(get("/api/personnel/{id}/cards", first.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uid").value("CARD-PERSON-1"));

        mvc.perform(put("/api/personnel/{id}/cards/{uid}/reassign", second.getId(), "CARD-PERSON-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uid").value("CARD-PERSON-1"));

        mvc.perform(get("/api/personnel/{id}/cards", second.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].uid").value("CARD-PERSON-1"));

        mvc.perform(delete("/api/personnel/{id}/cards/{uid}", second.getId(), "CARD-PERSON-1"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/personnel/{id}/cards", second.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void personnelUpdateCanClearPhotoWithBlankString() throws Exception {
        Personnel person = savePerson("Сидоров", "Сергей");

        mvc.perform(put("/api/personnel/{id}", person.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "photoBase64": "data:image/jpeg;base64,SGVsbG8="
                                }
                                """))
                .andExpect(status().isOk());

        mvc.perform(put("/api/personnel/{id}", person.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "photoBase64": ""
                                }
                                """))
                .andExpect(status().isOk());

        Personnel reloaded = personnelRepository.findById(person.getId()).orElseThrow();
        assertThat(reloaded.getPhotoBase64()).isNull();
    }

    @Test
    void personnelDeleteArchivesEmployeeWithNfcHistory() throws Exception {
        Personnel person = savePerson("Архивов", "Проход");
        Card card = new Card();
        card.setUid("CARD-ARCHIVE-1");
        card.setPerson(person);
        card.setActive(true);
        cardRepository.save(card);

        mvc.perform(post("/api/ha/nfc/toggle").param("uid", card.getUid()).param("device", "reader-archive-1"))
                .andExpect(status().isOk());

        mvc.perform(delete("/api/personnel/{id}", person.getId()))
                .andExpect(status().isOk());

        Personnel archived = personnelRepository.findById(person.getId()).orElseThrow();
        assertThat(archived.getArchivedAt()).isNotNull();
        assertThat(cardRepository.findByUid(card.getUid()).isActive()).isFalse();

        mvc.perform(get("/api/personnel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '%s')]".formatted(person.getId()), empty()));

        mvc.perform(get("/api/events/by-card/{uid}", card.getUid()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].person.id").value(person.getId().toString()));
    }

    private Personnel savePerson(String lastName, String firstName) {
        Personnel person = new Personnel(lastName, firstName, "Тестович", LocalDate.of(1990, 1, 1), null, "+79991234567");
        return personnelRepository.save(person);
    }
}
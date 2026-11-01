package org.suffleport.zwloader.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.repository.PersonnelRepository;

import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sandwich тест: Controller -> Service -> Repository -> DB -> обратно.
 * Сценарий: создать сотрудника -> зарегистрировать карту по ФИО -> проверить has-access.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class HaCardRegistrationSandwichTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    PersonnelRepository personnelRepository;

    @Test
    void registerCardByName_then_hasAccessTrue() throws Exception {
        Personnel p = new Personnel("Иванов", "Иван", "Иванович", LocalDate.of(1990, 1, 1), null, null);
        personnelRepository.save(p);

        String fullName = p.getFullName();

        mvc.perform(post("/api/ha/card/register-by-name")
                        .param("uid", "CARD999")
                        .param("name", fullName))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("auto_assigned"))
                .andExpect(jsonPath("$.person_name").value(fullName))
                .andExpect(jsonPath("$.uid").value("CARD999"));

        mvc.perform(get("/api/ha/has-access").param("uid", "CARD999"))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }
}

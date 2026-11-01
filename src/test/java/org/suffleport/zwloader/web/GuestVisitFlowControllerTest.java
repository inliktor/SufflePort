package org.suffleport.zwloader.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Guest;
import org.suffleport.zwloader.domain.GuestVisit;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.repository.GuestRepository;
import org.suffleport.zwloader.repository.GuestVisitRepository;
import org.suffleport.zwloader.repository.PersonnelRepository;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "zwloader", password = "change_me")
@ActiveProfiles("test")
@Transactional
class GuestVisitFlowControllerTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    GuestRepository guestRepository;

    @Autowired
    PersonnelRepository personnelRepository;

    @Autowired
    GuestVisitRepository guestVisitRepository;

    @Test
    void startThenFinishVisitFlow() throws Exception {
        GuestVisit visit = createVisitForNow();

        mvc.perform(put("/api/guest-visits/{id}/start", visit.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(put("/api/guest-visits/{id}/finish", visit.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FINISHED"));
    }

    @Test
    void finishWithoutStartReturnsConflict() throws Exception {
        GuestVisit visit = createVisitForNow();

        mvc.perform(put("/api/guest-visits/{id}/finish", visit.getId()))
                .andExpect(status().isConflict());
    }

    @Test
    void todayFilterByStatusWorks() throws Exception {
        createVisitForNow();
        GuestVisit active = createVisitForNow();
        active.setStatus("ACTIVE");
        guestVisitRepository.save(active);

        mvc.perform(get("/api/guest-visits/today").param("status", "PLANNED"))
                .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("PLANNED"));
    }

    @Test
    void qrAndCheckInByCodeWork() throws Exception {
        GuestVisit visit = createVisitForNow();

        mvc.perform(get("/api/guest-visits/{id}/qr", visit.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));

        String payload = "{\"code\":\"VISIT:" + visit.getId() + "\"}";
        mvc.perform(post("/api/guest-visits/checkin-by-code")
                        .contentType("application/json")
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.visitStatus").value("ACTIVE"));
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

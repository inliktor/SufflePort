package org.suffleport.zwloader.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Проверка, что права работают на уровне API, а не только в интерфейсе.
 * Здесь важен именно код ответа: 403 вместо 200 или 500.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class VisitRequestAuthorizationTest {

    @Autowired
    MockMvc mvc;

    @Test
    @WithMockUser(username = "employee@suffleport.ru", roles = "EMPLOYEE")
    void employeeCannotOpenApprovalQueue() throws Exception {
        mvc.perform(get("/api/visit-requests/queue"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "security@suffleport.ru", roles = "SECURITY")
    void securityCannotApproveRequest() throws Exception {
        mvc.perform(post("/api/visit-requests/1/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"comment\":\"ok\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "employee@suffleport.ru", roles = "EMPLOYEE")
    void employeeCannotSeeAuditLog() throws Exception {
        mvc.perform(get("/api/audit"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "employee@suffleport.ru", roles = "EMPLOYEE")
    void employeeCannotImportPersonnel() throws Exception {
        // Файл-заглушка: важно, чтобы запрос дошёл до метода и упёрся в проверку прав,
        // а не в согласование типа содержимого.
        MockMultipartFile file = new MockMultipartFile(
                "file", "personnel.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                new byte[]{1, 2, 3});

        mvc.perform(multipart("/api/personnel-import/preview").file(file))
                .andExpect(status().isForbidden());
    }
}

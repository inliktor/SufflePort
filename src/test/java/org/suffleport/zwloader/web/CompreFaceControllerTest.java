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
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.repository.PersonnelRepository;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Интеграционные тесты контроллера CompreFaceController.
 * Цель: проверить CRUD операции над фото (subject создание, загрузка, листинг, удаление).
 * Работаем в stub режиме (api.compreface.url=stub://local), чтобы не вызывать реальный API.
 * Используем H2 in-memory БД и профиль test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = "zwloader", password = "change_me")
@ActiveProfiles("test")
@Transactional
class CompreFaceControllerTest {

    @Autowired
    MockMvc mvc; // MockMvc для HTTP вызовов без запуска сервера.

    @Autowired
    PersonnelRepository personnelRepository; // Подготовка сущности сотрудника.

    private UUID createPerson() {
        // Создаем сотрудника с минимальным набором полей.
        Personnel p = new Personnel("Петров", "Илья", "Александрович", LocalDate.of(1991,3,3), null, null);
        personnelRepository.save(p);
        return p.getId();
    }

    @Test
    void subjectCreated() throws Exception {
        // Проверка: при запросе /subject для сотрудника создается comprefaceSubject.
        UUID id = createPerson();
        mvc.perform(get("/api/face/" + id + "/subject"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subject").value("person-" + id)) // subject формируется по шаблону
                .andExpect(jsonPath("$.stub").value(true)); // stub режим активен
    }

    @Test
    void uploadAndListAndDeleteFace() throws Exception {
        // 1. Создаем сотрудника.
        UUID id = createPerson();
        // 2. Загружаем 1 файл (эмуляция фото лица).
        MockMultipartFile file = new MockMultipartFile("file", "face.jpg", "image/jpeg", new byte[]{1,2,3});
        mvc.perform(multipart("/api/face/" + id + "/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"))
                .andExpect(jsonPath("$.face_id").exists()); // face_id сгенерирован
        // 3. Листинг должен вернуть одну запись.
        mvc.perform(get("/api/face/" + id + "/faces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));
        // 4. Извлекаем face_id (упрощенная регулярка).
        String json = mvc.perform(get("/api/face/" + id + "/faces"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String faceId = json.replaceAll(".*\"face_id\":\"([^\"]+)\".*", "$1");
        // 5. Удаляем конкретное фото.
        mvc.perform(delete("/api/face/" + id + "/faces/" + faceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted_face_id").value(faceId));
        // 6. Повторный листинг -> 0.
        mvc.perform(get("/api/face/" + id + "/faces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }

    @Test
    void deleteAllFaces() throws Exception {
        // Подготовка: сотрудник + два загруженных изображения.
        UUID id = createPerson();
        MockMultipartFile file = new MockMultipartFile("file", "face.jpg", "image/jpeg", new byte[]{9,8,7});
        mvc.perform(multipart("/api/face/" + id + "/upload").file(file))
                .andExpect(status().isOk());
        mvc.perform(multipart("/api/face/" + id + "/upload").file(file))
                .andExpect(status().isOk());
        mvc.perform(get("/api/face/" + id + "/faces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));
        // Массовое удаление.
        mvc.perform(delete("/api/face/" + id + "/faces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted_count").value(2));
        // Проверка что пусто.
        mvc.perform(get("/api/face/" + id + "/faces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));
    }
}

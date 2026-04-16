package org.suffleport.zwloader.service;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.repository.PersonnelRepository;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompreFaceService {

    @Value("${api.compreface.url}")
    private String baseUrl;

    @Value("${api.compreface}")
    private String apiKey;

    private final PersonnelRepository personnelRepository;

    private final Map<String, List<FaceRecord>> stubStorage = new ConcurrentHashMap<>();

    private RestTemplate restTemplate() { return new RestTemplate(); }

    @Getter
    public static class FaceRecord {
        private final String faceId;
        private final String imageUrl;
        private final Date createdAt;
        public FaceRecord(String faceId, String imageUrl) {
            this.faceId = faceId;
            this.imageUrl = imageUrl;
            this.createdAt = new Date();
        }
    }

    public boolean isStub() { return baseUrl == null || baseUrl.startsWith("stub"); }

    @Transactional
    public String ensureSubject(UUID personId) {
        Personnel p = personnelRepository.findById(personId)
                .orElseThrow(() -> new NoSuchElementException("Сотрудник не найден: " + personId));
        if (p.getComprefaceSubject() != null && !p.getComprefaceSubject().isBlank()) {
            return p.getComprefaceSubject();
        }
        String subject = "person-" + personId;
        p.setComprefaceSubject(subject);
        personnelRepository.save(p);
        if (isStub()) {
            log.info("[STUB] create subject {}", subject);
            return subject;
        }
        // Реальный вызов создания subject (если API требует). Допускаем что CompreFace сам создаст при первой загрузке.
        return subject;
    }

    @Transactional(readOnly = true)
    public List<FaceRecord> listFaces(UUID personId) {
        Personnel p = personnelRepository.findById(personId)
                .orElseThrow(() -> new NoSuchElementException("Сотрудник не найден: " + personId));
        String subject = p.getComprefaceSubject();
        if (subject == null || subject.isBlank()) return List.of();
        if (isStub()) {
            return stubStorage.getOrDefault(subject, List.of());
        }
        // Реальный запрос: GET /api/v1/faces?subject=...
        String url = baseUrl + "/api/v1/faces?subject=" + subject;
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        ResponseEntity<Map> resp = restTemplate().exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        // Ожидаем JSON с массивом faces [{face_id, image_url}]
        Object facesObj = resp.getBody() != null ? resp.getBody().get("faces") : null;
        if (!(facesObj instanceof List<?> list)) return List.of();
        return list.stream().map(o -> {
            if (o instanceof Map<?,?> m) {
                String faceId = Objects.toString(m.get("face_id"), "");
                String imageUrl = Objects.toString(m.get("image_url"), "");
                return new FaceRecord(faceId, imageUrl);
            }
            return null;
        }).filter(Objects::nonNull).collect(Collectors.toList());
    }

    @Transactional
    public FaceRecord uploadFace(UUID personId, MultipartFile file) {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("image file is empty");
        String subject = ensureSubject(personId);
        if (isStub()) {
            String faceId = UUID.randomUUID().toString();
            String imageUrl = "stub://face/" + faceId;
            FaceRecord fr = new FaceRecord(faceId, imageUrl);
            stubStorage.computeIfAbsent(subject, k -> new ArrayList<>()).add(fr);
            return fr;
        }
        String url = baseUrl + "/api/v1/faces?subject=" + subject;
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        var body = new LinkedHashMap<String, Object>();
        body.put("file", file.getResource());
        HttpEntity<?> entity = new HttpEntity<>(body, headers);
        ResponseEntity<Map> resp = restTemplate().postForEntity(url, entity, Map.class);
        Object faceIdObj = resp.getBody() != null ? resp.getBody().get("face_id") : null;
        Object imageUrlObj = resp.getBody() != null ? resp.getBody().get("image_url") : null;
        String faceId = Objects.toString(faceIdObj, "");
        String imageUrl = Objects.toString(imageUrlObj, "");
        return new FaceRecord(faceId, imageUrl);
    }

    @Transactional
    public void deleteFace(UUID personId, String faceId) {
        if (faceId == null || faceId.isBlank()) throw new IllegalArgumentException("faceId required");
        Personnel p = personnelRepository.findById(personId)
                .orElseThrow(() -> new NoSuchElementException("Сотрудник не найден: " + personId));
        String subject = p.getComprefaceSubject();
        if (subject == null) return; // ничего
        if (isStub()) {
            stubStorage.computeIfPresent(subject, (k, list) -> list.stream().filter(fr -> !fr.getFaceId().equals(faceId)).collect(Collectors.toList()));
            return;
        }
        String url = baseUrl + "/api/v1/faces/" + faceId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        restTemplate().exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
    }

    @Transactional
    public int deleteAllFaces(UUID personId) {
        Personnel p = personnelRepository.findById(personId)
                .orElseThrow(() -> new NoSuchElementException("Сотрудник не найден: " + personId));
        String subject = p.getComprefaceSubject();
        if (subject == null) return 0;
        if (isStub()) {
            int count = stubStorage.getOrDefault(subject, List.of()).size();
            stubStorage.remove(subject);
            return count;
        }
        // Реальная реализация: получить список и удалить по одному
        List<FaceRecord> faces = listFaces(personId);
        for (FaceRecord fr : faces) deleteFace(personId, fr.getFaceId());
        return faces.size();
    }

    @Transactional
    public void deleteSubject(UUID personId) {
        Personnel p = personnelRepository.findById(personId)
                .orElseThrow(() -> new NoSuchElementException("Сотрудник не найден: " + personId));
        String subject = p.getComprefaceSubject();
        if (subject == null) return;
        deleteAllFaces(personId);
        if (isStub()) {
            p.setComprefaceSubject(null);
            personnelRepository.save(p);
            return;
        }
        String url = baseUrl + "/api/v1/subjects/" + subject;
        HttpHeaders headers = new HttpHeaders();
        headers.set("x-api-key", apiKey);
        restTemplate().exchange(url, HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        p.setComprefaceSubject(null);
        personnelRepository.save(p);
    }
}


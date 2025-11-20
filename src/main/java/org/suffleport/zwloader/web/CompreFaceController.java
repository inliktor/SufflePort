package org.suffleport.zwloader.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.suffleport.zwloader.service.CompreFaceService;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/face")
@RequiredArgsConstructor
public class CompreFaceController {

    private final CompreFaceService service;

    @GetMapping("/{personId}/subject")
    public Map<String,Object> getOrCreateSubject(@PathVariable UUID personId) {
        String subject = service.ensureSubject(personId);
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("subject", subject);
        out.put("stub", service.isStub());
        return out;
    }

    @GetMapping("/{personId}/faces")
    public Map<String,Object> listFaces(@PathVariable UUID personId) {
        var faces = service.listFaces(personId);
        return Map.of(
                "count", faces.size(),
                "faces", faces.stream().map(fr -> Map.of(
                        "face_id", fr.getFaceId(),
                        "image_url", fr.getImageUrl(),
                        "created_at", fr.getCreatedAt().getTime()
                )).collect(Collectors.toList())
        );
    }

    @PostMapping(value = "/{personId}/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String,Object> uploadFace(@PathVariable UUID personId,
                                         @RequestPart("file") MultipartFile file) {
        var rec = service.uploadFace(personId, file);
        return Map.of(
                "status", "ok",
                "face_id", rec.getFaceId(),
                "image_url", rec.getImageUrl()
        );
    }

    @DeleteMapping("/{personId}/faces/{faceId}")
    public Map<String,Object> deleteFace(@PathVariable UUID personId, @PathVariable String faceId) {
        service.deleteFace(personId, faceId);
        return Map.of("status", "ok", "deleted_face_id", faceId);
    }

    @DeleteMapping("/{personId}/faces")
    public Map<String,Object> deleteAllFaces(@PathVariable UUID personId) {
        int count = service.deleteAllFaces(personId);
        return Map.of("status", "ok", "deleted_count", count);
    }

    @DeleteMapping("/{personId}/subject")
    public Map<String,Object> deleteSubject(@PathVariable UUID personId) {
        service.deleteSubject(personId);
        return Map.of("status", "ok");
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<Map<String,Object>> notFound(NoSuchElementException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of(
                "error", "not_found",
                "message", ex.getMessage()
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String,Object>> badRequest(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "error", "bad_request",
                "message", ex.getMessage()
        ));
    }
}


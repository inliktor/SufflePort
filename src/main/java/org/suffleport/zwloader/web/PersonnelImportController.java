package org.suffleport.zwloader.web;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.suffleport.zwloader.dto.ImportReport;
import org.suffleport.zwloader.service.PersonnelImportService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Импорт сотрудников из Excel. Доступен администратору: операция массовая
 * и затрагивает справочник персонала целиком.
 */
@RestController
@RequestMapping("/api/personnel-import")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class PersonnelImportController {

    private static final String XLSX =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final PersonnelImportService personnelImportService;

    @GetMapping(value = "/template", produces = XLSX)
    public ResponseEntity<byte[]> template() {
        byte[] file = personnelImportService.buildTemplate();
        String fileName = "personnel-import-template.xlsx";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + fileName)
                .contentType(MediaType.parseMediaType(XLSX))
                .body(file);
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReport preview(@RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return personnelImportService.preview(input);
        }
    }

    @PostMapping(value = "/apply", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ImportReport apply(@RequestParam("file") MultipartFile file) throws IOException {
        try (InputStream input = file.getInputStream()) {
            return personnelImportService.apply(input);
        }
    }
}

package org.suffleport.zwloader.web;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.domain.GuestVisit;
import org.suffleport.zwloader.service.GuestVisitService;

import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/guest-visits")
@RequiredArgsConstructor
public class GuestVisitController {

    private final GuestVisitService guestVisitService;

    @GetMapping
    public Object listAll(@RequestParam(name = "page", required = false) Integer page,
                          @RequestParam(name = "size", required = false) Integer size,
                          @RequestParam(name = "sort", required = false) String sortBy,
                          @RequestParam(name = "dir", required = false) String sortDir) {
        if (PaginationSupport.isPaged(page, size)) {
            return guestVisitService.listPage(PaginationSupport.buildPageable(page, size, sortBy, sortDir,
                    "plannedFrom", "plannedFrom", "plannedTo", "status", "createdAt"));
        }
        return guestVisitService.listAll();
    }

    @GetMapping("/{id}")
    public GuestVisit get(@PathVariable Long id) { return guestVisitService.getOrThrow(id); }

    @GetMapping("/by-guest/{guestId}")
    public List<GuestVisit> byGuest(@PathVariable UUID guestId) { return guestVisitService.listByGuest(guestId); }

    @GetMapping("/by-host/{hostId}")
    public List<GuestVisit> byHost(@PathVariable UUID hostId) { return guestVisitService.listByHost(hostId); }

    @GetMapping("/planned-between")
    public List<GuestVisit> plannedBetween(@RequestParam("start") OffsetDateTime start,
                                           @RequestParam("end") OffsetDateTime end) {
        return guestVisitService.listPlannedBetween(start, end);
    }

    @GetMapping("/today")
    public List<GuestVisit> today(@RequestParam(name = "status", required = false) String status) {
        return guestVisitService.listToday(status);
    }

    @PostMapping
    public GuestVisit create(@Valid @RequestBody CreateGuestVisitRequest req) {
        return guestVisitService.create(req.getGuestId(), req.getHostPersonId(), req.getPlannedFrom(), req.getPlannedTo(), req.getReason());
    }

    @PutMapping("/{id}/plan")
    public GuestVisit updatePlan(@PathVariable Long id, @Valid @RequestBody UpdatePlanRequest req) {
        return guestVisitService.updatePlan(id, req.getPlannedFrom(), req.getPlannedTo(), req.getReason());
    }

    @PutMapping("/{id}/status")
    public GuestVisit setStatus(@PathVariable Long id, @RequestParam("status") String status) {
        return guestVisitService.setStatus(id, status);
    }

    @PutMapping("/{id}/start")
    public GuestVisit startVisit(@PathVariable Long id) {
        return guestVisitService.startVisit(id);
    }

    @PutMapping("/{id}/finish")
    public GuestVisit finishVisit(@PathVariable Long id) {
        return guestVisitService.finishVisit(id);
    }

    @PutMapping("/{id}/cancel")
    public GuestVisit cancelVisit(@PathVariable Long id) {
        return guestVisitService.cancelVisit(id);
    }

    @GetMapping(value = "/{id}/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> qrByVisit(@PathVariable Long id) {
        String code = guestVisitService.buildVisitCode(id);
        byte[] png = generateQrPng(code, 320);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.IMAGE_PNG)
                .body(png);
    }

    @PostMapping(value = "/checkin-by-code", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> checkinByCode(@Valid @RequestBody CheckInByCodeRequest request) {
        GuestVisit visit = guestVisitService.checkInByCode(request.getCode());
        return Map.of(
                "status", "ok",
                "visitId", visit.getId(),
                "visitStatus", visit.getStatus()
        );
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) { guestVisitService.delete(id); }

    private byte[] generateQrPng(String text, int size) {
        try {
            BitMatrix matrix = new MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException | java.io.IOException ex) {
            throw new IllegalStateException("Не удалось сгенерировать QR-код", ex);
        }
    }

    @Data
    public static class CreateGuestVisitRequest {
        @NotNull(message = "guestId обязателен")
        private UUID guestId;

        @NotNull(message = "hostPersonId обязателен")
        private UUID hostPersonId;

        @NotNull(message = "plannedFrom обязателен")
        private OffsetDateTime plannedFrom;

        @NotNull(message = "plannedTo обязателен")
        private OffsetDateTime plannedTo;

        @Size(max = 300, message = "Причина визита не может быть длиннее 300 символов")
        private String reason;
    }

    @Data
    public static class UpdatePlanRequest {
        private OffsetDateTime plannedFrom;
        private OffsetDateTime plannedTo;

        @Size(max = 300, message = "Причина визита не может быть длиннее 300 символов")
        private String reason;
    }

    @Data
    public static class CheckInByCodeRequest {
        @NotBlank(message = "Код визита обязателен")
        @Size(max = 120, message = "Код визита не может быть длиннее 120 символов")
        private String code;
    }
}


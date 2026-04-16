package org.suffleport.zwloader.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.domain.Camera;
import org.suffleport.zwloader.service.CameraService;

import java.util.List;

@RestController
@RequestMapping("/api/cameras")
@RequiredArgsConstructor
public class CameraController {

    private final CameraService cameraService;

    @GetMapping
    public Object list(@RequestParam(name = "page", required = false) Integer page,
                       @RequestParam(name = "size", required = false) Integer size,
                       @RequestParam(name = "sort", required = false) String sortBy,
                       @RequestParam(name = "dir", required = false) String sortDir) {
        if (PaginationSupport.isPaged(page, size)) {
            return cameraService.listPage(PaginationSupport.buildPageable(page, size, sortBy, sortDir,
                    "name", "name", "id", "location", "createdAt"));
        }
        return cameraService.listAll();
    }

    @GetMapping("/{id}")
    public Camera get(@PathVariable String id) { return cameraService.getOrThrow(id); }

    @GetMapping("/by-device/{deviceId}")
    public List<Camera> byDevice(@PathVariable String deviceId) { return cameraService.findByDevice(deviceId); }

    @PostMapping
    public Camera create(@Valid @RequestBody CreateCameraRequest req) {
        return cameraService.create(req.getId(), req.getName(), req.getRtspUrl(), req.getLocation(), req.getDeviceId());
    }

    @PutMapping("/{id}")
    public Camera updateBasic(@PathVariable String id, @Valid @RequestBody UpdateCameraRequest req) {
        return cameraService.updateBasic(id, req.getName(), req.getRtspUrl(), req.getLocation());
    }

    @PutMapping("/{id}/reassign-device")
    public Camera reassignDevice(@PathVariable String id, @RequestParam(name = "deviceId", required = false) String deviceId) {
        return cameraService.reassignDevice(id, deviceId);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) { cameraService.delete(id); }

    @Data
    public static class CreateCameraRequest {
        @NotBlank(message = "ID камеры обязателен")
        @Size(max = 100, message = "ID камеры не может быть длиннее 100 символов")
        @jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{1,99}$", message = "ID камеры содержит недопустимые символы")
        private String id;

        @NotBlank(message = "Название камеры обязательно")
        @Size(max = 100, message = "Название камеры не может быть длиннее 100 символов")
        private String name;

        @Size(max = 500, message = "RTSP URL не может быть длиннее 500 символов")
        private String rtspUrl;

        @Size(max = 200, message = "Расположение камеры не может быть длиннее 200 символов")
        private String location;

        @Size(max = 100, message = "ID устройства не может быть длиннее 100 символов")
        private String deviceId;
    }

    @Data
    public static class UpdateCameraRequest {
        @Size(max = 100, message = "Название камеры не может быть длиннее 100 символов")
        private String name;

        @Size(max = 500, message = "RTSP URL не может быть длиннее 500 символов")
        private String rtspUrl;

        @Size(max = 200, message = "Расположение камеры не может быть длиннее 200 символов")
        private String location;
    }
}


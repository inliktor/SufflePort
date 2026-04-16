package org.suffleport.zwloader.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.domain.Device;
import org.suffleport.zwloader.service.DeviceService;

import java.util.List;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public Object list(@RequestParam(name = "page", required = false) Integer page,
                       @RequestParam(name = "size", required = false) Integer size,
                       @RequestParam(name = "sort", required = false) String sortBy,
                       @RequestParam(name = "dir", required = false) String sortDir) {
        if (PaginationSupport.isPaged(page, size)) {
            return deviceService.listPage(PaginationSupport.buildPageable(page, size, sortBy, sortDir,
                    "id", "id", "kind", "location"));
        }
        return deviceService.listAll();
    }

    @GetMapping("/{id}")
    public Device get(@PathVariable String id) { return deviceService.getOrThrow(id); }

    @GetMapping("/by-kind")
    public List<Device> byKind(@RequestParam("kind") String kind) { return deviceService.findByKind(kind); }

    @GetMapping("/search/location")
    public List<Device> searchByLocation(@RequestParam("q") String fragment) { return deviceService.searchByLocation(fragment); }

    @PostMapping
    public Device create(@Valid @RequestBody CreateDeviceRequest req) { return deviceService.create(req.getId(), req.getKind(), req.getLocation()); }

    @PutMapping("/{id}")
    public Device update(@PathVariable String id, @Valid @RequestBody UpdateDeviceRequest req) { return deviceService.update(id, req.getKind(), req.getLocation()); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable String id) { deviceService.delete(id); }

    @Data
    public static class CreateDeviceRequest {
        @NotBlank(message = "ID устройства обязателен")
        @Size(max = 100, message = "ID устройства не может быть длиннее 100 символов")
        @jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._:-]{1,99}$", message = "ID устройства содержит недопустимые символы")
        private String id;

        @NotBlank(message = "Тип устройства обязателен")
        @Size(max = 50, message = "Тип устройства не может быть длиннее 50 символов")
        private String kind;

        @Size(max = 200, message = "Расположение не может быть длиннее 200 символов")
        private String location;
    }

    @Data
    public static class UpdateDeviceRequest {
        @Size(max = 50, message = "Тип устройства не может быть длиннее 50 символов")
        private String kind;

        @Size(max = 200, message = "Расположение не может быть длиннее 200 символов")
        private String location;
    }
}


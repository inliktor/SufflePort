package org.suffleport.zwloader.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.domain.Position;
import org.suffleport.zwloader.service.PositionService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/positions")
@RequiredArgsConstructor
public class PositionController {

    private final PositionService positionService;

    @GetMapping
    public Object listAll(@RequestParam(name = "page", required = false) Integer page,
                          @RequestParam(name = "size", required = false) Integer size,
                          @RequestParam(name = "sort", required = false) String sortBy,
                          @RequestParam(name = "dir", required = false) String sortDir) {
        if (PaginationSupport.isPaged(page, size)) {
            return positionService.listPage(PaginationSupport.buildPageable(page, size, sortBy, sortDir,
                    "name", "name", "accessLevel", "createdAt"));
        }
        return positionService.listAll();
    }

    @GetMapping("/{id}")
    public Position get(@PathVariable UUID id) { return positionService.getOrThrow(id); }

    @GetMapping("/by-name")
    public Position findByName(@RequestParam("name") String name) { return positionService.findByName(name); }

    @GetMapping("/search/prefix")
    public List<Position> findByPrefix(@RequestParam("q") String prefix) { return positionService.findByNamePrefix(prefix); }

    @PostMapping
    public Position create(@Valid @RequestBody CreatePositionRequest req) { return positionService.create(req.getName(), req.getAccessLevel()); }

    @PutMapping("/{id}")
    public Position update(@PathVariable UUID id, @Valid @RequestBody UpdatePositionRequest req) { return positionService.update(id, req.getName(), req.getAccessLevel()); }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) { positionService.delete(id); }

    @Data
    public static class CreatePositionRequest {
        @NotBlank(message = "Название должности обязательно")
        @Size(max = 100, message = "Название должности не может быть длиннее 100 символов")
        private String name;

        @Min(value = 0, message = "Уровень доступа должен быть не меньше 0")
        @Max(value = 100, message = "Уровень доступа должен быть не больше 100")
        private Integer accessLevel;
    }

    @Data
    public static class UpdatePositionRequest {
        @Size(max = 100, message = "Название должности не может быть длиннее 100 символов")
        private String name;

        @Min(value = 0, message = "Уровень доступа должен быть не меньше 0")
        @Max(value = 100, message = "Уровень доступа должен быть не больше 100")
        private Integer accessLevel;
    }
}
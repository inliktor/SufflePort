package org.suffleport.zwloader.web;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.suffleport.zwloader.domain.VisitRequest;
import org.suffleport.zwloader.domain.VisitRequestHistory;
import org.suffleport.zwloader.domain.VisitRequestStatus;
import org.suffleport.zwloader.dto.VisitRequestCreateRequest;
import org.suffleport.zwloader.dto.VisitRequestDecisionRequest;
import org.suffleport.zwloader.dto.VisitRequestPlanRequest;
import org.suffleport.zwloader.security.CurrentUserService;
import org.suffleport.zwloader.service.VisitRequestService;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * REST API заявок на гостевые посещения.
 *
 * Права проверяются дважды: аннотацией @PreAuthorize (быстрый отказ до вызова
 * кода) и внутри сервиса (там, где правило зависит от самой заявки, например
 * «менять может только автор»).
 */
@RestController
@RequestMapping("/api/visit-requests")
@RequiredArgsConstructor
public class VisitRequestController {

    private final VisitRequestService visitRequestService;
    private final CurrentUserService currentUserService;

    @GetMapping("/mine")
    public List<VisitRequest> mine() {
        return visitRequestService.listMine(currentUserService.current());
    }

    @GetMapping("/queue")
    @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
    public List<VisitRequest> queue() {
        return visitRequestService.listQueue(currentUserService.current());
    }

    /** Все заявки, включая чужие и завершённые. */
    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
    public List<VisitRequest> all(@RequestParam(name = "status", required = false) String status) {
        return visitRequestService.listAll(status, currentUserService.current());
    }

    @GetMapping("/expected")
    @PreAuthorize("hasAnyRole('SECURITY','APPROVER','ADMIN')")
    public List<VisitRequest> expected(
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return visitRequestService.listExpected(date, currentUserService.current());
    }

    /** Справочник статусов и допустимых переходов — используется формой и тестами. */
    @GetMapping("/statuses")
    public Map<String, Object> statuses() {
        Map<String, Object> result = new LinkedHashMap<>();
        for (VisitRequestStatus status : VisitRequestStatus.values()) {
            Set<VisitRequestStatus> targets = VisitRequestService.allowedTargets(status);
            result.put(status.name(), targets.stream().map(Enum::name).sorted().toList());
        }
        return result;
    }

    @GetMapping("/{id}")
    public VisitRequest get(@PathVariable Long id) {
        return visitRequestService.getForActor(id, currentUserService.current());
    }

    @GetMapping("/{id}/history")
    public List<VisitRequestHistory> history(@PathVariable Long id) {
        return visitRequestService.history(id, currentUserService.current());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public VisitRequest create(@Valid @RequestBody VisitRequestCreateRequest request) {
        return visitRequestService.create(request, currentUserService.current());
    }

    @PostMapping("/{id}/submit")
    public VisitRequest submit(@PathVariable Long id,
                               @RequestBody(required = false) VisitRequestDecisionRequest body) {
        return visitRequestService.submit(id, body != null ? body.comment() : null,
                currentUserService.current());
    }

    /** Правка плана черновика: время и цель. */
    @PutMapping("/{id}/plan")
    public VisitRequest updatePlan(@PathVariable Long id,
                                   @Valid @RequestBody VisitRequestPlanRequest body) {
        return visitRequestService.updatePlan(id, body.plannedFrom(), body.plannedTo(), body.purpose(),
                currentUserService.current());
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
    public VisitRequest approve(@PathVariable Long id,
                                @Valid @RequestBody(required = false) VisitRequestDecisionRequest body) {
        return visitRequestService.approve(id, body != null ? body.comment() : null,
                currentUserService.current());
    }

    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('APPROVER','ADMIN')")
    public VisitRequest reject(@PathVariable Long id,
                               @Valid @RequestBody(required = false) VisitRequestDecisionRequest body) {
        return visitRequestService.reject(id, body != null ? body.comment() : null,
                currentUserService.current());
    }

    @PostMapping("/{id}/cancel")
    public VisitRequest cancel(@PathVariable Long id,
                               @RequestBody(required = false) VisitRequestDecisionRequest body) {
        return visitRequestService.cancel(id, body != null ? body.comment() : null,
                currentUserService.current());
    }
}

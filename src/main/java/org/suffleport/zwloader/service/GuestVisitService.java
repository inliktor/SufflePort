package org.suffleport.zwloader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Guest;
import org.suffleport.zwloader.domain.GuestVisit;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.repository.GuestRepository;
import org.suffleport.zwloader.repository.GuestVisitRepository;
import org.suffleport.zwloader.repository.PersonnelRepository;
import org.suffleport.zwloader.validation.ValidationRules;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestVisitService {

    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Saratov");

    private final GuestVisitRepository guestVisitRepository;
    private final GuestRepository guestRepository;
    private final PersonnelRepository personnelRepository;

    @Transactional
    public GuestVisit create(UUID guestId,
                             UUID hostPersonId,
                             OffsetDateTime plannedFrom,
                             OffsetDateTime plannedTo,
                             String reason) {
        if (guestId == null) throw new IllegalArgumentException("guestId is required");
        if (hostPersonId == null) throw new IllegalArgumentException("hostPersonId is required");
        ValidationRules.validateDateRange(plannedFrom, plannedTo, "Время начала визита", "Время окончания визита", true);
        String safeReason = ValidationRules.normalizeOptionalText(reason, "Причина визита", 300);
        Guest guest = guestRepository.findById(guestId)
                .orElseThrow(() -> new NoSuchElementException("Гость не найден: " + guestId));
        Personnel host = personnelRepository.findById(hostPersonId)
                .orElseThrow(() -> new NoSuchElementException("Сотрудник-хост не найден: " + hostPersonId));
        GuestVisit gv = new GuestVisit(guest, host, plannedFrom, plannedTo, safeReason);
        return guestVisitRepository.save(gv);
    }

    @Transactional(readOnly = true)
    public GuestVisit getOrThrow(Long id) {
        return guestVisitRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Визит не найден: " + id));
    }

    @Transactional
    public GuestVisit updatePlan(Long id,
                                 OffsetDateTime plannedFrom,
                                 OffsetDateTime plannedTo,
                                 String reason) {
        GuestVisit gv = getOrThrow(id);
        OffsetDateTime effectiveFrom = plannedFrom != null ? plannedFrom : gv.getPlannedFrom();
        OffsetDateTime effectiveTo = plannedTo != null ? plannedTo : gv.getPlannedTo();
        ValidationRules.validateDateRange(effectiveFrom, effectiveTo, "Время начала визита", "Время окончания визита", true);
        if (plannedFrom != null) gv.setPlannedFrom(plannedFrom);
        if (plannedTo != null) gv.setPlannedTo(plannedTo);
        if (reason != null) gv.setReason(ValidationRules.normalizeOptionalText(reason, "Причина визита", 300));
        return guestVisitRepository.save(gv);
    }

    @Transactional
    public GuestVisit setStatus(Long id, String status) {
        GuestVisit gv = getOrThrow(id);
        gv.setStatus(ValidationRules.normalizeVisitStatus(status));
        return guestVisitRepository.save(gv);
    }

    @Transactional
    public GuestVisit startVisit(Long id) {
        GuestVisit gv = getOrThrow(id);
        if (!"PLANNED".equalsIgnoreCase(gv.getStatus())) {
            throw new IllegalStateException("Запустить можно только визит в статусе PLANNED");
        }
        gv.setStatus("ACTIVE");
        if (gv.getStartedAt() == null) {
            gv.setStartedAt(OffsetDateTime.now(APP_ZONE));
        }
        return guestVisitRepository.save(gv);
    }

    @Transactional
    public GuestVisit finishVisit(Long id) {
        GuestVisit gv = getOrThrow(id);
        if (!"ACTIVE".equalsIgnoreCase(gv.getStatus())) {
            throw new IllegalStateException("Завершить можно только визит в статусе ACTIVE");
        }
        gv.setStatus("FINISHED");
        if (gv.getStartedAt() == null) {
            gv.setStartedAt(OffsetDateTime.now(APP_ZONE));
        }
        gv.setFinishedAt(OffsetDateTime.now(APP_ZONE));
        return guestVisitRepository.save(gv);
    }

    @Transactional
    public GuestVisit cancelVisit(Long id) {
        GuestVisit gv = getOrThrow(id);
        if ("FINISHED".equalsIgnoreCase(gv.getStatus()) || "CANCELLED".equalsIgnoreCase(gv.getStatus())) {
            throw new IllegalStateException("Нельзя отменить завершенный или уже отмененный визит");
        }
        gv.setStatus("CANCELLED");
        return guestVisitRepository.save(gv);
    }

    @Transactional(readOnly = true)
    public List<GuestVisit> listToday(String status) {
        OffsetDateTime start = LocalDate.now(APP_ZONE).atStartOfDay(APP_ZONE).toOffsetDateTime();
        OffsetDateTime end = LocalDate.now(APP_ZONE).plusDays(1).atStartOfDay(APP_ZONE).toOffsetDateTime();
        if (status == null || status.isBlank()) {
            return guestVisitRepository.findByPlannedFromBetween(start, end);
        }
        String normalizedStatus = ValidationRules.normalizeVisitStatus(status);
        return guestVisitRepository.findByPlannedFromBetweenAndStatus(start, end, normalizedStatus);
    }

    @Transactional
    public GuestVisit checkInByCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Код визита обязателен");
        }
        String normalized = code.trim();
        if (!normalized.toUpperCase().startsWith("VISIT:")) {
            throw new IllegalArgumentException("Некорректный формат кода визита");
        }
        String idPart = normalized.substring("VISIT:".length()).trim();
        long visitId;
        try {
            visitId = Long.parseLong(idPart);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Некорректный идентификатор визита в коде");
        }
        GuestVisit visit = getOrThrow(visitId);
        if ("ACTIVE".equalsIgnoreCase(visit.getStatus())) {
            return visit;
        }
        if ("FINISHED".equalsIgnoreCase(visit.getStatus()) || "CANCELLED".equalsIgnoreCase(visit.getStatus()) || "DENIED".equalsIgnoreCase(visit.getStatus())) {
            throw new IllegalStateException("Нельзя активировать визит в статусе " + visit.getStatus());
        }
        return startVisit(visitId);
    }

    @Transactional(readOnly = true)
    public String buildVisitCode(Long id) {
        getOrThrow(id);
        return "VISIT:" + id;
    }

    @Transactional(readOnly = true)
    public List<GuestVisit> listByGuest(UUID guestId) { return guestVisitRepository.findByGuest_Id(guestId); }

    @Transactional(readOnly = true)
    public List<GuestVisit> listByHost(UUID hostId) { return guestVisitRepository.findByHost_Id(hostId); }

    @Transactional(readOnly = true)
    public List<GuestVisit> listPlannedBetween(OffsetDateTime start, OffsetDateTime end) {
        ValidationRules.validateDateRange(start, end, "Начало периода", "Конец периода", true);
        return guestVisitRepository.findByPlannedFromBetween(start, end);
    }

    @Transactional(readOnly = true)
    public Page<GuestVisit> listPage(Pageable pageable) { return guestVisitRepository.findAll(pageable); }

    @Transactional(readOnly = true)
    public List<GuestVisit> listAll() { return guestVisitRepository.findAll(); }

    @Transactional
    public void delete(Long id) { guestVisitRepository.delete(getOrThrow(id)); }
}


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
import java.time.format.DateTimeFormatter;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestVisitService {

    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Saratov");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");
    /** Допуск до начала визита (гость может прийти не раньше, чем за N часов) */
    private static final int CHECKIN_AHEAD_HOURS = 2;
    /** Допуск после окончания визита (принять гостя можно не позже N минут после plannedTo) */
    private static final int CHECKIN_OVERDUE_MINUTES = 30;

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
        return guestVisitRepository.findByIdWithGuestAndHost(id)
                .orElseThrow(() -> new NoSuchElementException("Визит не найден: " + id));
    }

    @Transactional(readOnly = true)
    public GuestVisit getWithGuestAndHost(Long id) {
        return guestVisitRepository.findByIdWithGuestAndHost(id)
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
        // Если QR содержит полный URL (http/https), извлекаем параметр code=
        String normalized = extractCodeFromUrlIfNeeded(code.trim());
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

        OffsetDateTime now = OffsetDateTime.now(APP_ZONE);

        // Первое сканирование: PLANNED → ACTIVE (гость вошёл)
        if ("PLANNED".equalsIgnoreCase(visit.getStatus())) {
            if (visit.getPlannedFrom() != null && visit.getPlannedFrom().isAfter(now.plusHours(CHECKIN_AHEAD_HOURS))) {
                String from = visit.getPlannedFrom().atZoneSameInstant(APP_ZONE).toOffsetDateTime().format(DATE_FMT);
                throw new IllegalStateException("Визит запланирован только с " + from + ". Активация пока недоступна.");
            }
            if (visit.getPlannedTo() != null && visit.getPlannedTo().isBefore(now.minusMinutes(CHECKIN_OVERDUE_MINUTES))) {
                throw new IllegalStateException("Визит недоступен — гость не явился в отведённое время.");
            }
            return startVisit(visitId);
        }

        // Второе сканирование: ACTIVE → FINISHED (гость вышел)
        if ("ACTIVE".equalsIgnoreCase(visit.getStatus())) {
            return finishVisit(visitId);
        }

        if ("FINISHED".equalsIgnoreCase(visit.getStatus())) {
            throw new IllegalStateException("Гость уже покинул здание.");
        }
        if ("CANCELLED".equalsIgnoreCase(visit.getStatus())) {
            throw new IllegalStateException("Визит отменён.");
        }
        throw new IllegalStateException("Визит недоступен (статус: " + visit.getStatus() + ").");
    }

    @Transactional(readOnly = true)
    public String buildVisitCode(Long id) {
        getOrThrow(id);
        return "VISIT:" + id;
    }

    /**
     * Если переданная строка является полным URL (из QR-кода, отсканированного телефоном),
     * извлекает значение query-параметра {@code code}. Иначе возвращает строку без изменений.
     * Пример: http://host/api/public/guest-checkin?code=VISIT:123 → VISIT:123
     */
    private String extractCodeFromUrlIfNeeded(String raw) {
        if (!raw.startsWith("http://") && !raw.startsWith("https://")) {
            return raw;
        }
        try {
            URI uri = URI.create(raw);
            String query = uri.getRawQuery();
            if (query == null) return raw;
            for (String param : query.split("&")) {
                if (param.startsWith("code=")) {
                    return URLDecoder.decode(param.substring(5), StandardCharsets.UTF_8);
                }
            }
        } catch (Exception ignored) {
            // URL не распарсился — возвращаем как есть, дальше упадёт с понятной ошибкой
        }
        return raw;
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


package org.suffleport.zwloader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Guest;
import org.suffleport.zwloader.domain.GuestVisit;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.domain.VisitRequest;
import org.suffleport.zwloader.domain.VisitRequestHistory;
import org.suffleport.zwloader.domain.VisitRequestStatus;
import org.suffleport.zwloader.dto.VisitRequestCreateRequest;
import org.suffleport.zwloader.repository.GuestRepository;
import org.suffleport.zwloader.repository.GuestVisitRepository;
import org.suffleport.zwloader.repository.PersonnelRepository;
import org.suffleport.zwloader.repository.VisitRequestHistoryRepository;
import org.suffleport.zwloader.repository.VisitRequestRepository;
import org.suffleport.zwloader.security.Actor;
import org.suffleport.zwloader.validation.ValidationRules;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Бизнес-логика заявок на гостевые посещения.
 *
 * Здесь сосредоточен весь процесс: черновик, отправка, решение, отмена.
 * Контроллеры только принимают HTTP-запросы и передают сюда текущего пользователя.
 */
@Service
@RequiredArgsConstructor
public class VisitRequestService {

    private static final ZoneId APP_ZONE = ZoneId.of("Europe/Saratov");
    private static final DateTimeFormatter PERIOD_FORMAT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    /**
     * Матрица допустимых переходов.
     *
     * Она же используется в тестах и в документации: если правило меняется,
     * меняется только эта таблица, а не набор if-ов по коду.
     */
    private static final Map<VisitRequestStatus, Set<VisitRequestStatus>> ALLOWED_TRANSITIONS = buildTransitions();

    private final VisitRequestRepository visitRequestRepository;
    private final VisitRequestHistoryRepository historyRepository;
    private final PersonnelRepository personnelRepository;
    private final GuestRepository guestRepository;
    private final GuestVisitRepository guestVisitRepository;
    private final GuestVisitService guestVisitService;
    private final NotificationService notificationService;
    private final AuditService auditService;

    private static Map<VisitRequestStatus, Set<VisitRequestStatus>> buildTransitions() {
        Map<VisitRequestStatus, Set<VisitRequestStatus>> map = new EnumMap<>(VisitRequestStatus.class);
        map.put(VisitRequestStatus.DRAFT,
                EnumSet.of(VisitRequestStatus.SUBMITTED, VisitRequestStatus.CANCELLED));
        map.put(VisitRequestStatus.SUBMITTED,
                EnumSet.of(VisitRequestStatus.APPROVED, VisitRequestStatus.REJECTED, VisitRequestStatus.CANCELLED));
        map.put(VisitRequestStatus.APPROVED,
                EnumSet.of(VisitRequestStatus.CANCELLED));
        map.put(VisitRequestStatus.REJECTED, EnumSet.noneOf(VisitRequestStatus.class));
        map.put(VisitRequestStatus.CANCELLED, EnumSet.noneOf(VisitRequestStatus.class));
        return Map.copyOf(map);
    }

    public static boolean isTransitionAllowed(VisitRequestStatus from, VisitRequestStatus to) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of()).contains(to);
    }

    public static Set<VisitRequestStatus> allowedTargets(VisitRequestStatus from) {
        return ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
    }

    // ------------------------------------------------------------------ создание

    @Transactional
    public VisitRequest create(VisitRequestCreateRequest request, Actor actor) {
        requireUser(actor);

        Personnel host = personnelRepository.findById(request.hostPersonId())
                .orElseThrow(() -> new NoSuchElementException("Сотрудник-хост не найден: " + request.hostPersonId()));
        if (host.isArchived()) {
            throw new IllegalArgumentException("Сотрудник переведён в архив, заявку на него оформить нельзя");
        }

        String lastName = ValidationRules.requireHumanName(request.visitorLastName(), "Фамилия посетителя", 100);
        String firstName = ValidationRules.requireHumanName(request.visitorFirstName(), "Имя посетителя", 100);
        String middleName = ValidationRules.optionalHumanName(request.visitorMiddleName(), "Отчество посетителя", 100);
        String organization = ValidationRules.normalizeOptionalText(request.organization(), "Организация", 200);
        String purpose = ValidationRules.normalizeOptionalText(request.purpose(), "Цель посещения", 300);
        String phone = ValidationRules.normalizePhone(request.phone());

        ValidationRules.validateDateRange(request.plannedFrom(), request.plannedTo(),
                "Начало посещения", "Окончание посещения", true);
        if (request.plannedFrom().isBefore(OffsetDateTime.now(APP_ZONE).minusMinutes(5))) {
            throw new IllegalArgumentException("Нельзя оформить заявку на время, которое уже прошло");
        }

        VisitRequest entity = new VisitRequest();
        entity.setHost(host);
        entity.setVisitorLastName(lastName);
        entity.setVisitorFirstName(firstName);
        entity.setVisitorMiddleName(middleName);
        entity.setOrganization(organization);
        entity.setPurpose(purpose);
        entity.setPhone(phone);
        entity.setPlannedFrom(request.plannedFrom());
        entity.setPlannedTo(request.plannedTo());
        entity.setStatus(VisitRequestStatus.DRAFT);
        entity.setAuthorUserId(actor.userId());
        entity.setAuthorEmail(actor.email());

        VisitRequest saved = visitRequestRepository.save(entity);
        writeHistory(saved, null, VisitRequestStatus.DRAFT, "CREATE", null, actor);
        auditService.record("VisitRequest", saved.getId().toString(), "CREATE",
                null, snapshot(saved), actor.email(), "Оформлен черновик заявки");
        return saved;
    }

    // ------------------------------------------------------------------ процесс

    @Transactional
    public VisitRequest submit(Long id, String comment, Actor actor) {
        VisitRequest request = getOrThrow(id);
        requireAuthorOrAdmin(request, actor);

        // Черновик может пролежать несколько дней: проверяем, что период ещё актуален
        if (request.getPlannedTo().isBefore(OffsetDateTime.now(APP_ZONE))) {
            throw new IllegalStateException("Время посещения уже прошло. Измените период перед отправкой");
        }

        String safeComment = ValidationRules.normalizeOptionalText(comment, "Комментарий", 1000);
        applyTransition(request, VisitRequestStatus.SUBMITTED, "SUBMIT", safeComment, actor);

        notificationService.notifyApprovers(
                "VISIT_REQUEST_SUBMITTED",
                "Новая заявка на посещение",
                request.getVisitorFullName() + ", " + describePeriod(request)
                        + ". Автор: " + request.getAuthorEmail(),
                "/visit-requests.html?request=" + request.getId());
        return request;
    }

    @Transactional
    public VisitRequest approve(Long id, String comment, Actor actor) {
        if (actor == null || !actor.isApprover()) {
            throw new AccessDeniedException("Согласовывать заявки может только ответственный сотрудник");
        }
        VisitRequest request = getOrThrow(id);
        Map<String, Object> before = snapshot(request);

        String safeComment = ValidationRules.normalizeOptionalText(comment, "Комментарий", 1000);
        applyTransition(request, VisitRequestStatus.APPROVED, "APPROVE", safeComment, actor);

        request.setDecisionComment(safeComment);
        request.setDecidedAt(OffsetDateTime.now(APP_ZONE));
        if (actor.personId() != null) {
            personnelRepository.findById(actor.personId()).ifPresent(request::setApprover);
        }
        visitRequestRepository.save(request);

        GuestVisit visit = createExpectedVisit(request);

        auditService.record("VisitRequest", request.getId().toString(), "APPROVE",
                before, snapshot(request), actor.email(),
                "Одобрено, создан ожидаемый визит №" + visit.getId());

        notificationService.create(
                request.getAuthorUserId(),
                "VISIT_REQUEST_APPROVED",
                "Заявка одобрена",
                "Посетитель " + request.getVisitorFullName() + ", " + describePeriod(request),
                "/visit-requests.html?request=" + request.getId());
        return request;
    }

    @Transactional
    public VisitRequest reject(Long id, String comment, Actor actor) {
        if (actor == null || !actor.isApprover()) {
            throw new AccessDeniedException("Отклонять заявки может только ответственный сотрудник");
        }
        String safeComment = ValidationRules.normalizeRequiredText(comment, "Причина отказа", 1000);
        if (safeComment.length() < 3) {
            throw new IllegalArgumentException("Причина отказа должна содержать хотя бы три символа");
        }

        VisitRequest request = getOrThrow(id);
        Map<String, Object> before = snapshot(request);

        applyTransition(request, VisitRequestStatus.REJECTED, "REJECT", safeComment, actor);
        request.setDecisionComment(safeComment);
        request.setDecidedAt(OffsetDateTime.now(APP_ZONE));
        if (actor.personId() != null) {
            personnelRepository.findById(actor.personId()).ifPresent(request::setApprover);
        }
        visitRequestRepository.save(request);

        auditService.record("VisitRequest", request.getId().toString(), "REJECT",
                before, snapshot(request), actor.email(), safeComment);

        notificationService.create(
                request.getAuthorUserId(),
                "VISIT_REQUEST_REJECTED",
                "Заявка отклонена",
                "Причина: " + safeComment,
                "/visit-requests.html?request=" + request.getId());
        return request;
    }

    @Transactional
    public VisitRequest cancel(Long id, String comment, Actor actor) {
        VisitRequest request = getOrThrow(id);
        requireAuthorOrAdmin(request, actor);

        String safeComment = ValidationRules.normalizeOptionalText(comment, "Комментарий", 1000);
        VisitRequestStatus previous = request.getStatus();
        Map<String, Object> before = snapshot(request);

        applyTransition(request, VisitRequestStatus.CANCELLED, "CANCEL", safeComment, actor);

        // Если заявка была одобрена, ожидаемый визит тоже нужно снять,
        // иначе охрана продолжит видеть гостя в списке.
        if (previous == VisitRequestStatus.APPROVED) {
            guestVisitRepository.findByRequestId(request.getId()).ifPresent(visit -> {
                if ("PLANNED".equalsIgnoreCase(visit.getStatus())) {
                    guestVisitService.cancelVisit(visit.getId());
                }
            });
        }

        auditService.record("VisitRequest", request.getId().toString(), "CANCEL",
                before, snapshot(request), actor.email(), safeComment);
        return request;
    }

    // ------------------------------------------------------------------ чтение

    /**
     * Изменение плана черновика. Пока заявка не отправлена, автор может
     * поправить время и цель — иначе единственным способом было бы отменять заявку.
     */
    @Transactional
    public VisitRequest updatePlan(Long id,
                                   OffsetDateTime plannedFrom,
                                   OffsetDateTime plannedTo,
                                   String purpose,
                                   Actor actor) {
        VisitRequest request = getOrThrow(id);
        requireAuthorOrAdmin(request, actor);
        if (request.getStatus() != VisitRequestStatus.DRAFT) {
            throw new IllegalStateException("Изменить план можно только у черновика");
        }

        OffsetDateTime effectiveFrom = plannedFrom != null ? plannedFrom : request.getPlannedFrom();
        OffsetDateTime effectiveTo = plannedTo != null ? plannedTo : request.getPlannedTo();
        ValidationRules.validateDateRange(effectiveFrom, effectiveTo,
                "Начало посещения", "Окончание посещения", true);
        if (effectiveFrom.isBefore(OffsetDateTime.now(APP_ZONE).minusMinutes(5))) {
            throw new IllegalArgumentException("Нельзя указать время, которое уже прошло");
        }

        Map<String, Object> before = snapshot(request);
        request.setPlannedFrom(effectiveFrom);
        request.setPlannedTo(effectiveTo);
        if (purpose != null) {
            request.setPurpose(ValidationRules.normalizeOptionalText(purpose, "Цель посещения", 300));
        }
        VisitRequest saved = visitRequestRepository.save(request);

        writeHistory(saved, VisitRequestStatus.DRAFT, VisitRequestStatus.DRAFT, "UPDATE", null, actor);
        auditService.record("VisitRequest", saved.getId().toString(), "UPDATE",
                before, snapshot(saved), actor.email(), "Изменён план посещения");
        return saved;
    }
    @Transactional(readOnly = true)
    public VisitRequest getOrThrow(Long id) {
        return visitRequestRepository.findDetailedById(id)
                .orElseThrow(() -> new NoSuchElementException("Заявка не найдена: " + id));
    }

    /**
     * Заявку видит автор, согласующий, охрана и администратор.
     * Остальные получают отказ, а не пустой ответ — так проще отлаживать права.
     */
    @Transactional(readOnly = true)
    public VisitRequest getForActor(Long id, Actor actor) {
        VisitRequest request = getOrThrow(id);
        boolean allowed = actor != null
                && (actor.isAdmin()
                    || actor.isApprover()
                    || actor.isSecurity()
                    || (request.getAuthorUserId() != null && request.getAuthorUserId().equals(actor.userId())));
        if (!allowed) {
            throw new AccessDeniedException("Заявка недоступна текущему пользователю");
        }
        return request;
    }

    @Transactional(readOnly = true)
    public List<VisitRequest> listMine(Actor actor) {
        requireUser(actor);
        return visitRequestRepository.findMine(actor.userId());
    }

    @Transactional(readOnly = true)
    public List<VisitRequest> listQueue(Actor actor) {
        if (actor == null || !actor.isApprover()) {
            throw new AccessDeniedException("Очередь согласования доступна только ответственному сотруднику");
        }
        return visitRequestRepository.findByStatus(VisitRequestStatus.SUBMITTED);
    }

    /**
     * Все заявки в системе, включая чужие и завершённые. Нужны согласующему
     * и администратору, чтобы видеть процесс целиком, а не только свой список.
     */
    @Transactional(readOnly = true)
    public List<VisitRequest> listAll(String status, Actor actor) {
        if (actor == null || !actor.isApprover()) {
            throw new AccessDeniedException("Все заявки доступны согласующим и администраторам");
        }
        if (status == null || status.isBlank()) {
            return visitRequestRepository.findAllDetailed();
        }
        return visitRequestRepository.findByStatus(VisitRequestStatus.parse(status));
    }

    /**
     * Ожидаемые посещения на дату. Нужны охране: она видит только одобренные
     * заявки, черновики и отказы туда не попадают.
     */
    @Transactional(readOnly = true)
    public List<VisitRequest> listExpected(LocalDate date, Actor actor) {
        if (actor == null || !(actor.isSecurity() || actor.isApprover())) {
            throw new AccessDeniedException("Список ожидаемых посещений доступен охране и согласующим");
        }
        LocalDate target = date != null ? date : LocalDate.now(APP_ZONE);
        OffsetDateTime from = target.atStartOfDay(APP_ZONE).toOffsetDateTime();
        OffsetDateTime to = target.plusDays(1).atStartOfDay(APP_ZONE).toOffsetDateTime();
        List<VisitRequest> requests = visitRequestRepository.findExpected(VisitRequestStatus.APPROVED, from, to);

        // Охране нужно видеть, пришёл гость или уже ушёл, — подмешиваем состояние визита
        if (!requests.isEmpty()) {
            List<Long> ids = requests.stream().map(VisitRequest::getId).toList();
            Map<Long, GuestVisit> visits = guestVisitRepository.findByRequestIdIn(ids).stream()
                    .filter(visit -> visit.getRequestId() != null)
                    .collect(Collectors.toMap(GuestVisit::getRequestId, visit -> visit, (first, second) -> first));

            for (VisitRequest request : requests) {
                GuestVisit visit = visits.get(request.getId());
                if (visit != null) {
                    request.setVisitId(visit.getId());
                    request.setVisitStatus(visit.getStatus());
                }
            }
        }
        return requests;
    }

    @Transactional(readOnly = true)
    public List<VisitRequestHistory> history(Long id, Actor actor) {
        VisitRequest request = getOrThrow(id);
        boolean allowed = actor != null
                && (actor.isAdmin()
                    || actor.isApprover()
                    || (request.getAuthorUserId() != null && request.getAuthorUserId().equals(actor.userId())));
        if (!allowed) {
            throw new AccessDeniedException("История заявки доступна автору, согласующему и администратору");
        }
        return historyRepository.findByRequest_IdOrderByCreatedAtAsc(id);
    }

    // ------------------------------------------------------------------ внутреннее

    private void applyTransition(VisitRequest request,
                                 VisitRequestStatus target,
                                 String action,
                                 String comment,
                                 Actor actor) {
        VisitRequestStatus current = request.getStatus();
        if (current == target) {
            throw new IllegalStateException("Заявка уже находится в статусе " + current);
        }
        if (!isTransitionAllowed(current, target)) {
            throw new IllegalStateException(
                    "Недопустимый переход: " + current + " → " + target);
        }
        request.setStatus(target);
        visitRequestRepository.save(request);
        writeHistory(request, current, target, action, comment, actor);
    }

    private void writeHistory(VisitRequest request,
                              VisitRequestStatus from,
                              VisitRequestStatus to,
                              String action,
                              String comment,
                              Actor actor) {
        VisitRequestHistory history = new VisitRequestHistory();
        history.setRequest(request);
        history.setFromStatus(from != null ? from.name() : null);
        history.setToStatus(to.name());
        history.setAction(action);
        history.setComment(comment);
        if (actor != null) {
            history.setActorUserId(actor.userId());
            history.setActorEmail(actor.email());
        }
        historyRepository.save(history);
    }

    /**
     * Одобрение превращает заявку в ожидаемый визит. Если карточки гостя ещё нет,
     * она создаётся из снимка заявки — так справочник гостей остаётся актуальным
     * без ручного дублирования данных.
     */
    private GuestVisit createExpectedVisit(VisitRequest request) {
        Guest guest = request.getGuest();
        if (guest == null) {
            guest = guestRepository.save(new Guest(
                    request.getVisitorLastName(),
                    request.getVisitorFirstName(),
                    request.getVisitorMiddleName(),
                    request.getPhone(),
                    request.getOrganization()));
            if (request.getPurpose() != null) {
                guest.setNotes("Заявка №" + request.getId() + ": " + request.getPurpose());
            }
            request.setGuest(guest);
        }

        GuestVisit visit = guestVisitService.create(
                guest.getId(),
                request.getHost().getId(),
                request.getPlannedFrom(),
                request.getPlannedTo(),
                request.getPurpose());
        visit.setRequestId(request.getId());
        return guestVisitRepository.save(visit);
    }

    private void requireUser(Actor actor) {
        if (actor == null || actor.userId() == null) {
            throw new AccessDeniedException("Требуется вход в систему");
        }
    }

    private void requireAuthorOrAdmin(VisitRequest request, Actor actor) {
        requireUser(actor);
        boolean isAuthor = request.getAuthorUserId() != null
                && request.getAuthorUserId().equals(actor.userId());
        if (!isAuthor && !actor.isAdmin()) {
            throw new AccessDeniedException("Действие доступно только автору заявки или администратору");
        }
    }

    private String describePeriod(VisitRequest request) {
        return "с " + PERIOD_FORMAT.format(request.getPlannedFrom().atZoneSameInstant(APP_ZONE))
                + " по " + PERIOD_FORMAT.format(request.getPlannedTo().atZoneSameInstant(APP_ZONE));
    }

    private Map<String, Object> snapshot(VisitRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", request.getId());
        map.put("status", request.getStatus() != null ? request.getStatus().name() : null);
        map.put("visitor", request.getVisitorFullName());
        map.put("organization", request.getOrganization());
        map.put("plannedFrom", request.getPlannedFrom() != null ? request.getPlannedFrom().toString() : null);
        map.put("plannedTo", request.getPlannedTo() != null ? request.getPlannedTo().toString() : null);
        map.put("decisionComment", request.getDecisionComment());
        map.put("authorEmail", request.getAuthorEmail());
        return map;
    }
}

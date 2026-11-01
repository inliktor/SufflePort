package org.suffleport.zwloader.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.AuditLog;
import org.suffleport.zwloader.domain.GuestVisit;
import org.suffleport.zwloader.domain.Notification;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.domain.Position;
import org.suffleport.zwloader.domain.VisitRequest;
import org.suffleport.zwloader.domain.VisitRequestStatus;
import org.suffleport.zwloader.dto.VisitRequestCreateRequest;
import org.suffleport.zwloader.repository.GuestVisitRepository;
import org.suffleport.zwloader.repository.NotificationRepository;
import org.suffleport.zwloader.repository.PersonnelRepository;
import org.suffleport.zwloader.repository.PositionRepository;
import org.suffleport.zwloader.repository.VisitRequestHistoryRepository;
import org.suffleport.zwloader.repository.VisitRequestRepository;
import org.suffleport.zwloader.security.Actor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Проверка процесса заявки целиком: черновик → отправка → решение.
 * Тест идёт через сервисы, без HTTP: так проверяется именно бизнес-логика.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class VisitRequestFlowTest {

    private static final ZoneId ZONE = ZoneId.of("Europe/Saratov");

    @Autowired VisitRequestService visitRequestService;
    @Autowired PersonnelRepository personnelRepository;
    @Autowired PositionRepository positionRepository;
    @Autowired GuestVisitRepository guestVisitRepository;
    @Autowired VisitRequestHistoryRepository historyRepository;
    @Autowired NotificationRepository notificationRepository;
    @Autowired VisitRequestRepository visitRequestRepository;
    @Autowired NotificationService notificationService;
    @Autowired AuditService auditService;

    private Personnel host;
    private Actor author;
    private Actor approver;

    @BeforeEach
    void setUp() {
        Position position = positionRepository.save(new Position("Инженер-программист", 2));
        host = personnelRepository.save(new Personnel(
                "Сидоров", "Олег", "Петрович", LocalDate.of(1988, 3, 12), position, "+79001234567"));

        author = new Actor(101, "author@suffleport.ru", Actor.ROLE_EMPLOYEE, null);
        approver = new Actor(102, "approver@suffleport.ru", Actor.ROLE_APPROVER, null);
    }

    private VisitRequestCreateRequest sampleRequest() {
        OffsetDateTime from = OffsetDateTime.now(ZONE).plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        return new VisitRequestCreateRequest(
                host.getId(),
                "Кузнецов",
                "Артём",
                "Сергеевич",
                "ООО «Ромашка»",
                "+79005554433",
                "Согласование договора",
                from,
                from.plusHours(2));
    }

    @Test
    void draftBecomesVisitAfterApproval() {
        VisitRequest request = visitRequestService.create(sampleRequest(), author);
        assertEquals(VisitRequestStatus.DRAFT, request.getStatus());

        visitRequestService.submit(request.getId(), "Прошу согласовать", author);
        assertEquals(VisitRequestStatus.SUBMITTED, request.getStatus());

        visitRequestService.approve(request.getId(), "Согласовано", approver);
        assertEquals(VisitRequestStatus.APPROVED, request.getStatus());

        Optional<GuestVisit> visit = guestVisitRepository.findByRequestId(request.getId());
        assertTrue(visit.isPresent(), "При одобрении должен создаваться ожидаемый визит");
        assertEquals("PLANNED", visit.get().getStatus());
        assertEquals(request.getPlannedFrom().toInstant(), visit.get().getPlannedFrom().toInstant());

        // История: создание, отправка, одобрение
        assertEquals(3, historyRepository.findByRequest_IdOrderByCreatedAtAsc(request.getId()).size());

        // Автор получил уведомление о решении
        List<Notification> inbox = notificationRepository
                .findTop100ByRecipientUserIdOrderByCreatedAtDesc(author.userId());
        assertFalse(inbox.isEmpty());
        assertEquals("VISIT_REQUEST_APPROVED", inbox.get(0).getType());
    }

    @Test
    void rejectedRequestCannotBeApproved() {
        VisitRequest request = visitRequestService.create(sampleRequest(), author);
        visitRequestService.submit(request.getId(), null, author);
        visitRequestService.reject(request.getId(), "Нет свободного переговорного зала", approver);

        assertEquals(VisitRequestStatus.REJECTED, request.getStatus());
        assertTrue(guestVisitRepository.findByRequestId(request.getId()).isEmpty());

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> visitRequestService.approve(request.getId(), null, approver));
        assertTrue(ex.getMessage().contains("Недопустимый переход"));
    }

    @Test
    void employeeCannotApproveOwnRequest() {
        VisitRequest request = visitRequestService.create(sampleRequest(), author);
        visitRequestService.submit(request.getId(), null, author);

        assertThrows(AccessDeniedException.class,
                () -> visitRequestService.approve(request.getId(), null, author));
    }

    @Test
    void otherEmployeeCannotCancelForeignRequest() {
        VisitRequest request = visitRequestService.create(sampleRequest(), author);
        Actor stranger = new Actor(555, "someone@suffleport.ru", Actor.ROLE_EMPLOYEE, null);

        assertThrows(AccessDeniedException.class,
                () -> visitRequestService.cancel(request.getId(), "передумал", stranger));
    }

    @Test
    void transitionMatrixIsClosedForFinalStatuses() {
        assertTrue(VisitRequestService.isTransitionAllowed(
                VisitRequestStatus.DRAFT, VisitRequestStatus.SUBMITTED));
        assertTrue(VisitRequestService.isTransitionAllowed(
                VisitRequestStatus.SUBMITTED, VisitRequestStatus.APPROVED));
        assertFalse(VisitRequestService.isTransitionAllowed(
                VisitRequestStatus.REJECTED, VisitRequestStatus.APPROVED));
        assertFalse(VisitRequestService.isTransitionAllowed(
                VisitRequestStatus.CANCELLED, VisitRequestStatus.SUBMITTED));
        assertFalse(VisitRequestService.isTransitionAllowed(
                VisitRequestStatus.DRAFT, VisitRequestStatus.APPROVED));
    }

    @Test
    void staleDraftCannotBeSubmitted() {
        VisitRequest request = visitRequestService.create(sampleRequest(), author);

        // Имитируем черновик, который пролежал несколько дней
        request.setPlannedFrom(OffsetDateTime.now(ZONE).minusHours(3));
        request.setPlannedTo(OffsetDateTime.now(ZONE).minusHours(1));
        visitRequestRepository.save(request);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> visitRequestService.submit(request.getId(), null, author));
        assertTrue(ex.getMessage().contains("уже прошло"));
        assertEquals(VisitRequestStatus.DRAFT, request.getStatus());
    }

    @Test
    void unreadCounterAndMarkAllWork() {
        VisitRequest request = visitRequestService.create(sampleRequest(), author);
        visitRequestService.submit(request.getId(), null, author);
        visitRequestService.approve(request.getId(), null, approver);

        assertTrue(notificationService.unreadCount(author) > 0);
        assertTrue(notificationService.markAllRead(author) > 0);
        assertEquals(0, notificationService.unreadCount(author));
    }

    @Test
    void auditSearchWithoutFiltersReturnsEntries() {
        VisitRequest request = visitRequestService.create(sampleRequest(), author);
        visitRequestService.submit(request.getId(), null, author);
        visitRequestService.approve(request.getId(), null, approver);

        Page<AuditLog> page = auditService.search(null, null, null, null, null, PageRequest.of(0, 50));
        assertFalse(page.getContent().isEmpty());
        assertTrue(page.getContent().stream().anyMatch(entry -> "APPROVE".equals(entry.getAction())));
    }
}

package org.suffleport.zwloader.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.suffleport.zwloader.domain.GuestVisit;

public interface GuestVisitRepository extends JpaRepository<GuestVisit, Long> {
    // Все визиты конкретного гостя
    List<GuestVisit> findByGuest_Id(UUID guestId);

    // Все визиты сотрудника как принимающей стороны
    List<GuestVisit> findByHost_Id(java.util.UUID hostId);

    long deleteByGuest_Id(UUID guestId);

    long deleteByHost_Id(UUID hostId);

    // Визиты, запланированные в интервале времени (с принудительной загрузкой guest+host)
    @Query("SELECT v FROM GuestVisit v JOIN FETCH v.guest LEFT JOIN FETCH v.host WHERE v.plannedFrom BETWEEN :start AND :end ORDER BY v.plannedFrom")
    List<GuestVisit> findByPlannedFromBetween(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    @Query("SELECT v FROM GuestVisit v JOIN FETCH v.guest LEFT JOIN FETCH v.host WHERE v.plannedFrom BETWEEN :start AND :end AND v.status = :status ORDER BY v.plannedFrom")
    List<GuestVisit> findByPlannedFromBetweenAndStatus(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end, @Param("status") String status);

    // Получить визит с принудительной загрузкой guest и host
    @Query("SELECT v FROM GuestVisit v JOIN FETCH v.guest LEFT JOIN FETCH v.host WHERE v.id = :id")
    Optional<GuestVisit> findByIdWithGuestAndHost(@Param("id") Long id);

    // Визит, созданный из заявки на посещение (для отмены вместе с заявкой)
    Optional<GuestVisit> findByRequestId(Long requestId);

    // Визиты пачкой по номерам заявок — чтобы список ожидаемых не делал N запросов
    List<GuestVisit> findByRequestIdIn(java.util.Collection<Long> requestIds);
}

package org.suffleport.zwloader.repository;

import org.suffleport.zwloader.domain.GuestVisit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public interface GuestVisitRepository extends JpaRepository<GuestVisit, Long> {
    // Все визиты конкретного гостя
    List<GuestVisit> findByGuest_Id(UUID guestId);

    // Все визиты сотрудника как принимающей стороны
    List<GuestVisit> findByHost_Id(java.util.UUID hostId);

    // Визиты, запланированные в интервале времени
    List<GuestVisit> findByPlannedFromBetween(OffsetDateTime start, OffsetDateTime end);
}

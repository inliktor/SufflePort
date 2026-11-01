package org.suffleport.zwloader.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.suffleport.zwloader.domain.VisitRequest;
import org.suffleport.zwloader.domain.VisitRequestStatus;

public interface VisitRequestRepository extends JpaRepository<VisitRequest, Long> {

    @EntityGraph(attributePaths = {"guest", "host", "approver"})
    @Query("select r from VisitRequest r where r.id = :id")
    Optional<VisitRequest> findDetailedById(@Param("id") Long id);

    @EntityGraph(attributePaths = {"guest", "host", "approver"})
    @Query("select r from VisitRequest r where r.authorUserId = :userId order by r.createdAt desc")
    List<VisitRequest> findMine(@Param("userId") Integer userId);

    @EntityGraph(attributePaths = {"guest", "host", "approver"})
    @Query("select r from VisitRequest r where r.status = :status order by r.createdAt asc")
    List<VisitRequest> findByStatus(@Param("status") VisitRequestStatus status);

    /** Все заявки без фильтра — для согласующего и администратора. */
    @EntityGraph(attributePaths = {"guest", "host", "approver"})
    @Query("select r from VisitRequest r order by r.createdAt desc")
    List<VisitRequest> findAllDetailed();

    @EntityGraph(attributePaths = {"guest", "host", "approver"})
    @Query("""
            select r from VisitRequest r
            where r.status = :status
              and r.plannedFrom >= :from
              and r.plannedFrom < :to
            order by r.plannedFrom asc
            """)
    List<VisitRequest> findExpected(@Param("status") VisitRequestStatus status,
                                    @Param("from") OffsetDateTime from,
                                    @Param("to") OffsetDateTime to);

    long countByStatus(VisitRequestStatus status);
}

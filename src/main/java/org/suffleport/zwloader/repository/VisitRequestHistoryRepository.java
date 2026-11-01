package org.suffleport.zwloader.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.suffleport.zwloader.domain.VisitRequestHistory;

public interface VisitRequestHistoryRepository extends JpaRepository<VisitRequestHistory, Long> {

    List<VisitRequestHistory> findByRequest_IdOrderByCreatedAtAsc(Long requestId);
}

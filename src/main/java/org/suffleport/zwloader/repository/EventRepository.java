package org.suffleport.zwloader.repository;

import org.suffleport.zwloader.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.EntityGraph;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.suffleport.zwloader.domain.Source;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.suffleport.zwloader.domain.Direction;

public interface EventRepository extends JpaRepository<Event, Long> {
    // Все события одного сотрудника
    List<Event> findByPerson_Id(UUID personId);

    // События по устройству за дату/период
    List<Event> findByDevice_IdAndCreatedAtBetween(String deviceId, OffsetDateTime start, OffsetDateTime end);

    // Все события по карте
    List<Event> findByCard_Uid(String cardUid);

    // Поиск событий по source/direction
    List<Event> findBySource(Source source);

    // События за последние n дней
    List<Event> findByCreatedAtAfter(OffsetDateTime date);

    // События между датами
    List<Event> findByCreatedAtBetween(OffsetDateTime start, OffsetDateTime end);

    // Новые методы для получения последнего события
    Event findTop1ByCard_UidOrderByCreatedAtDesc(String cardUid);
    Event findTop1ByFaceNameOrderByCreatedAtDesc(String faceName);
    Event findTop1ByDevice_IdOrderByCreatedAtDesc(String deviceId);

        @EntityGraph(attributePaths = {"person", "card", "device"})
        @Query("""
                        select e from Event e
                        where (:source is null or e.source = :source)
                            and (:direction is null or e.direction = :direction)
                        """)
        Page<Event> findBySourceAndDirection(@Param("source") Source source,
                                                                                 @Param("direction") Direction direction,
                                                                                 Pageable pageable);

        @EntityGraph(attributePaths = {"person", "card", "device"})
        @Query("""
                        select e from Event e
                        where (:source is null or e.source = :source)
                            and (:direction is null or e.direction = :direction)
                            and e.createdAt >= :start
                            and e.createdAt < :end
                        """)
        Page<Event> findBySourceAndDirectionAndCreatedAtBetween(@Param("source") Source source,
                                                                                                                         @Param("direction") Direction direction,
                                                                                                                         @Param("start") OffsetDateTime start,
                                                                                                                         @Param("end") OffsetDateTime end,
                                                                                                                         Pageable pageable);

        @Override
        @EntityGraph(attributePaths = {"person", "card", "device"})
        Page<Event> findAll(Pageable pageable);

        @EntityGraph(attributePaths = {"person", "card", "device"})
        Page<Event> findByCreatedAtAfter(OffsetDateTime date, Pageable pageable);

    @Modifying
    @Query("update Event e set e.person = null where e.person.id = :personId")
    int clearPerson(@Param("personId") UUID personId);

    @Modifying
    @Query("update Event e set e.card = null where e.card.uid in :uids")
    int clearCardByUids(@Param("uids") List<String> uids);
}

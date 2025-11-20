package org.suffleport.zwloader.repository;

import org.suffleport.zwloader.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.suffleport.zwloader.domain.Source;

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
}

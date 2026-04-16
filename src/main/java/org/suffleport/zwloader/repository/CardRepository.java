package org.suffleport.zwloader.repository;
import org.suffleport.zwloader.domain.Card;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.suffleport.zwloader.domain.Personnel;

import java.util.List;
import java.util.UUID;

@Repository
public interface CardRepository extends JpaRepository<Card, String> {
    // Найти карту по UID
    Card findByUid(String uid);

    // Найти все карты сотрудника по person_id
    List<Card> findByPerson_Id(UUID personId);

    // Найти все активные карты сотрудника
    List<Card> findByPerson_IdAndActiveTrue(UUID personId);

    // Найти все неактивные карты у сотрудника
    List<Card> findByPerson_IdAndActiveFalse(UUID personId);

    // Найти карты по части uid (LIKE %…%)
    List<Card> findByUidContainingIgnoreCase(String fragment);

    // Найти карты созданные после указанной даты
    List<Card> findByCreatedAtAfter(java.time.OffsetDateTime date);

    // Подсчитать количество активных карт у сотрудника
    long countByPerson_IdAndActiveTrue(UUID personId);

    long countByActiveTrue();

    // Удалить все карты сотрудника (удаление по person_id)
    void deleteByPerson_Id(UUID personId);

}

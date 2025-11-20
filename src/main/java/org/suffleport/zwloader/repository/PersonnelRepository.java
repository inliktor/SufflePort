package org.suffleport.zwloader.repository;

import org.suffleport.zwloader.domain.Personnel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Repository // не обязательно: Spring распознает сам, но можно добавить для читабельности
public interface PersonnelRepository extends JpaRepository<Personnel, UUID> {

    // Специальный метод поиска по фамилии (генерируется автоматически)
    List<Personnel> findByLastName(String lastName);

    List<Personnel> findByFirstName(String firstName);

    List<Personnel> findByFirstNameAndLastName(String firstName, String lastName);

    List<Personnel> findByFullName(String fullName);

    // По связанной сущности (Position) — если есть @ManyToOne:
    // (Spring сам строит join)
    List<Personnel> findByPosition_Name(String positionName);

    List<Personnel> findByLastNameIgnoreCase(String lastName);

    List<Personnel> findByPhoneNotNull();
    // Все сотрудники, у которых есть телефон

    List<Personnel> findByPosition_NameOrderByLastNameAsc(String positionName);
    // Всех сотрудников определённой должности — отсортировать по фамилии по возрастанию


    List<Personnel> findByCreatedAtAfter(OffsetDateTime date);

    List<Personnel> findByCreatedAtBetween(OffsetDateTime start, OffsetDateTime end);

    Page<Personnel> findByLastName(String lastName, Pageable pageable);

}

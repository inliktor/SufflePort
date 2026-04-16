package org.suffleport.zwloader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Personnel;
import org.suffleport.zwloader.domain.Position;
import org.suffleport.zwloader.repository.CardRepository;
import org.suffleport.zwloader.repository.EventRepository;
import org.suffleport.zwloader.repository.GuestVisitRepository;
import org.suffleport.zwloader.repository.PersonnelRepository;
import org.suffleport.zwloader.repository.PositionRepository;
import org.suffleport.zwloader.repository.UserRepository;
import org.suffleport.zwloader.validation.ValidationRules;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonnelService {

    private final PersonnelRepository personnelRepository;
    private final PositionRepository positionRepository;
    private final CardRepository cardRepository;
    private final EventRepository eventRepository;
    private final GuestVisitRepository guestVisitRepository;
    private final UserRepository userRepository;

    @Transactional
    public Personnel create(String lastName,
                            String firstName,
                            String middleName,
                            LocalDate dateOfBirth,
                            UUID positionId,
                            String phone,
                            String photoBase64) {
        String safeLastName = ValidationRules.requireHumanName(lastName, "Фамилия", 100);
        String safeFirstName = ValidationRules.requireHumanName(firstName, "Имя", 100);
        String safeMiddleName = ValidationRules.optionalHumanName(middleName, "Отчество", 100);
        LocalDate safeDateOfBirth = ValidationRules.validateDateOfBirth(dateOfBirth);
        String safePhone = ValidationRules.normalizePhone(phone);
        String safePhotoBase64 = ValidationRules.normalizePhotoBase64(photoBase64);

        Personnel p = new Personnel(safeLastName, safeFirstName, safeMiddleName, safeDateOfBirth, null, safePhone);
        if (positionId != null) {
            Position pos = positionRepository.findById(positionId)
                    .orElseThrow(() -> new NoSuchElementException("Должность не найдена: " + positionId));
            p.setPosition(pos);
        }
        p.setPhotoBase64(safePhotoBase64);
        return personnelRepository.save(p);
    }

    @Transactional(readOnly = true)
    public Personnel getOrThrow(UUID id) {
        return personnelRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Сотрудник не найден: " + id));
    }

    @Transactional
    public Personnel update(UUID id,
                            String lastName,
                            String firstName,
                            String middleName,
                            LocalDate dateOfBirth,
                            UUID positionId,
                            String phone,
                            String photoBase64) {
        Personnel p = getOrThrow(id);
        if (lastName != null) {
            p.setLastName(ValidationRules.requireHumanName(lastName, "Фамилия", 100));
        }
        if (firstName != null) {
            p.setFirstName(ValidationRules.requireHumanName(firstName, "Имя", 100));
        }
        if (middleName != null) { p.setMiddleName(ValidationRules.optionalHumanName(middleName, "Отчество", 100)); }
        if (dateOfBirth != null) p.setDateOfBirth(ValidationRules.validateDateOfBirth(dateOfBirth));
        if (phone != null) { p.setPhone(ValidationRules.normalizePhone(phone)); }
        if (positionId != null) {
            Position pos = positionRepository.findById(positionId)
                    .orElseThrow(() -> new NoSuchElementException("Должность не найдена: " + positionId));
            p.setPosition(pos);
        }
        if (photoBase64 != null) p.setPhotoBase64(ValidationRules.normalizePhotoBase64(photoBase64));
        return personnelRepository.save(p);
    }

    @Transactional
    public Personnel reassignPosition(UUID personId, UUID positionId) {
        Personnel p = getOrThrow(personId);
        Position pos = positionRepository.findById(positionId)
                .orElseThrow(() -> new NoSuchElementException("Должность не найдена: " + positionId));
        p.setPosition(pos);
        return personnelRepository.save(p);
    }

    @Transactional
    public void delete(UUID id) {
        Personnel person = getOrThrow(id);

        // 1) Detach user accounts from this personnel (person_id is nullable in DB)
        userRepository.clearPersonId(id);

        // 2) Guest visits: host_person_id is NOT NULL, so we must remove dependent visits
        guestVisitRepository.deleteByHost_Id(id);

        // 3) Events may reference this person and/or their cards; detach both before card deletion
        List<String> cardUids = cardRepository.findByPerson_Id(id)
                .stream()
                .map(c -> c.getUid())
                .collect(Collectors.toList());

        if (!cardUids.isEmpty()) {
            eventRepository.clearCardByUids(cardUids);
        }
        eventRepository.clearPerson(id);

        // 4) Cards: remove explicitly after detaching events to avoid FK violations
        cardRepository.deleteByPerson_Id(id);

        // 5) Finally delete personnel
        personnelRepository.delete(person);
    }

    // Поисковые методы
    @Transactional(readOnly = true)
    public List<Personnel> findByLastName(String lastName) { return personnelRepository.findByLastName(lastName); }

    @Transactional(readOnly = true)
    public List<Personnel> findByFirstName(String firstName) { return personnelRepository.findByFirstName(firstName); }

    @Transactional(readOnly = true)
    public List<Personnel> findByFirstAndLast(String firstName, String lastName) {
        return personnelRepository.findByFirstNameAndLastName(firstName, lastName);
    }

    @Transactional(readOnly = true)
    public List<Personnel> findByFullName(String fullName) { return personnelRepository.findByFullName(fullName); }

    @Transactional(readOnly = true)
    public List<Personnel> findByPositionName(String positionName) {
        return personnelRepository.findByPosition_Name(positionName);
    }

    @Transactional(readOnly = true)
    public List<Personnel> findByLastNameIgnoreCase(String lastName) {
        return personnelRepository.findByLastNameIgnoreCase(lastName);
    }

    @Transactional(readOnly = true)
    public List<Personnel> findWithPhone() { return personnelRepository.findByPhoneNotNull(); }

    @Transactional(readOnly = true)
    public List<Personnel> findByPositionOrdered(String positionName) {
        return personnelRepository.findByPosition_NameOrderByLastNameAsc(positionName);
    }

    @Transactional(readOnly = true)
    public List<Personnel> createdAfter(OffsetDateTime date) { return personnelRepository.findByCreatedAtAfter(date); }

    @Transactional(readOnly = true)
    public List<Personnel> createdBetween(OffsetDateTime start, OffsetDateTime end) {
        ValidationRules.validateDateRange(start, end, "Начало периода", "Конец периода", true);
        return personnelRepository.findByCreatedAtBetween(start, end);
    }

    @Transactional(readOnly = true)
    public Page<Personnel> pageByLastName(String lastName, Pageable pageable) {
        return personnelRepository.findByLastName(lastName, pageable);
    }

    @Transactional(readOnly = true)
    public Page<Personnel> listPage(Pageable pageable) {
        return personnelRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Page<Personnel> listPage(String query, Pageable pageable) {
        String safeQuery = query == null ? null : query.trim();
        if (safeQuery == null || safeQuery.isBlank()) {
            return personnelRepository.findAll(pageable);
        }
        return personnelRepository.search(safeQuery, pageable);
    }

    @Transactional(readOnly = true)
    public List<Personnel> listAll(String query) {
        String safeQuery = query == null ? null : query.trim();
        if (safeQuery == null || safeQuery.isBlank()) {
            return personnelRepository.findAll();
        }
        return personnelRepository.search(safeQuery, Pageable.unpaged()).getContent();
    }

    @Transactional(readOnly = true)
    public List<Personnel> listAll() { return personnelRepository.findAll(); }
}

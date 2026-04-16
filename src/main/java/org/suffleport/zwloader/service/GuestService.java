package org.suffleport.zwloader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Guest;
import org.suffleport.zwloader.repository.GuestRepository;
import org.suffleport.zwloader.repository.GuestVisitRepository;
import org.suffleport.zwloader.validation.ValidationRules;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestService {

    private final GuestRepository guestRepository;
    private final GuestVisitRepository guestVisitRepository;

    @Transactional
    public Guest create(String lastName,
                        String firstName,
                        String middleName,
                        String phone,
                        String company,
                        String photoBase64) {
        String safeLastName = ValidationRules.requireHumanName(lastName, "Фамилия", 100);
        String safeFirstName = ValidationRules.requireHumanName(firstName, "Имя", 100);
        String safeMiddleName = ValidationRules.optionalHumanName(middleName, "Отчество", 100);
        String safePhone = ValidationRules.normalizePhone(phone);
        String safeCompany = ValidationRules.normalizeOptionalText(company, "Компания", 200);
        Guest g = new Guest(safeLastName, safeFirstName, safeMiddleName, safePhone, safeCompany);
        if (photoBase64 != null) g.setPhotoBase64(ValidationRules.normalizePhotoBase64(photoBase64));
        return guestRepository.save(g);
    }

    @Transactional(readOnly = true)
    public Guest getOrThrow(UUID id) {
        return guestRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Гость не найден: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Guest> listPage(Pageable pageable) { return guestRepository.findAll(pageable); }

    @Transactional(readOnly = true)
    public Page<Guest> listPage(String query, Pageable pageable) {
        String safeQuery = query == null ? null : query.trim();
        if (safeQuery == null || safeQuery.isBlank()) {
            return guestRepository.findAll(pageable);
        }
        return guestRepository.search(safeQuery, pageable);
    }

    @Transactional(readOnly = true)
    public List<Guest> listAll(String query) {
        String safeQuery = query == null ? null : query.trim();
        if (safeQuery == null || safeQuery.isBlank()) {
            return guestRepository.findAll();
        }
        return guestRepository.search(safeQuery, Pageable.unpaged()).getContent();
    }

    @Transactional(readOnly = true)
    public List<Guest> listAll() { return guestRepository.findAll(); }

    @Transactional
    public Guest update(UUID id,
                        String lastName,
                        String firstName,
                        String middleName,
                        String phone,
                        String company,
                        String document,
                        String notes,
                        String photoBase64) {
        Guest g = getOrThrow(id);
        if (lastName != null) {
            g.setLastName(ValidationRules.requireHumanName(lastName, "Фамилия", 100));
        }
        if (firstName != null) { g.setFirstName(ValidationRules.requireHumanName(firstName, "Имя", 100)); }
        if (middleName != null) { g.setMiddleName(ValidationRules.optionalHumanName(middleName, "Отчество", 100)); }
        if (phone != null) { g.setPhone(ValidationRules.normalizePhone(phone)); }
        if (company != null) { g.setCompany(ValidationRules.normalizeOptionalText(company, "Компания", 200)); }
        if (document != null) { g.setDocument(ValidationRules.normalizeOptionalText(document, "Документ", 200)); }
        if (notes != null) { g.setNotes(ValidationRules.normalizeOptionalText(notes, "Примечания", 500)); }
        if (photoBase64 != null) g.setPhotoBase64(ValidationRules.normalizePhotoBase64(photoBase64));
        // пересоберём ФИО
        g.setFullName(buildFullName(g.getLastName(), g.getFirstName(), g.getMiddleName()));
        return guestRepository.save(g);
    }

    @Transactional
    public void delete(UUID id) {
        getOrThrow(id);
        guestVisitRepository.deleteByGuest_Id(id);
        guestRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Guest> findByLastName(String lastName) { return guestRepository.findByLastName(lastName); }

    @Transactional(readOnly = true)
    public List<Guest> searchByDocument(String fragment) {
        if (fragment == null || fragment.isBlank()) return List.of();
        return guestRepository.findByDocumentContainingIgnoreCase(fragment);
    }

    private String buildFullName(String lastName, String firstName, String middleName) {
        StringBuilder sb = new StringBuilder();
        if (lastName != null) sb.append(lastName).append(" ");
        if (firstName != null) sb.append(firstName).append(" ");
        if (middleName != null) sb.append(middleName);
        return sb.toString().trim();
    }

}


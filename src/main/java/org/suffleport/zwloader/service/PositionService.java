package org.suffleport.zwloader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Position;
import org.suffleport.zwloader.repository.PersonnelRepository;
import org.suffleport.zwloader.repository.PositionRepository;
import org.suffleport.zwloader.validation.ValidationRules;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PositionService {

    private final PositionRepository positionRepository;
    private final PersonnelRepository personnelRepository;

    @Transactional
    public Position create(String name, Integer accessLevel) {
        String safeName = ValidationRules.normalizeRequiredText(name, "Название должности", 100);
        Integer safeAccessLevel = ValidationRules.validateAccessLevel(accessLevel);
        Position p = new Position(safeName, safeAccessLevel);
        return positionRepository.save(p);
    }

    @Transactional(readOnly = true)
    public Position getOrThrow(UUID id) {
        return positionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Должность не найдена: " + id));
    }

    @Transactional
    public Position update(UUID id, String name, Integer accessLevel) {
        Position p = getOrThrow(id);
        if (name != null) {
            p.setName(ValidationRules.normalizeRequiredText(name, "Название должности", 100));
        }
        if (accessLevel != null) p.setAccessLevel(ValidationRules.validateAccessLevel(accessLevel));
        return positionRepository.save(p);
    }

    @Transactional
    public void delete(UUID id) {
        if (personnelRepository.existsByPosition_Id(id)) {
            throw new IllegalStateException("Нельзя удалить должность: на неё назначены сотрудники");
        }
        positionRepository.delete(getOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Position findByName(String name) { return positionRepository.findByName(name); }

    @Transactional(readOnly = true)
    public List<Position> findByNamePrefix(String prefix) { return positionRepository.findByNameStartingWithIgnoreCase(prefix); }

    @Transactional(readOnly = true)
    public Page<Position> listPage(Pageable pageable) { return positionRepository.findAll(pageable); }

    @Transactional(readOnly = true)
    public List<Position> listAll() { return positionRepository.findAll(); }
}
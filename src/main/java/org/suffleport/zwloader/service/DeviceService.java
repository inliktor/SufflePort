package org.suffleport.zwloader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Device;
import org.suffleport.zwloader.repository.CameraRepository;
import org.suffleport.zwloader.repository.DeviceRepository;
import org.suffleport.zwloader.validation.ValidationRules;

import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class DeviceService {

    private final DeviceRepository deviceRepository;
    private final CameraRepository cameraRepository;

    @Transactional
    public Device create(String id, String kind, String location) {
        String safeId = ValidationRules.normalizeSystemId(id, "ID устройства");
        String safeKind = ValidationRules.normalizeRequiredText(kind, "Тип устройства", 50);
        String safeLocation = ValidationRules.normalizeOptionalText(location, "Расположение", 200);
        if (deviceRepository.existsById(safeId)) throw new IllegalStateException("Устройство уже существует: " + safeId);
        Device d = new Device();
        d.setId(safeId);
        d.setKind(safeKind);
        d.setLocation(safeLocation);
        return deviceRepository.save(d);
    }

    @Transactional(readOnly = true)
    public Device getOrThrow(String id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Устройство не найдено: " + id));
    }

    @Transactional
    public Device update(String id, String kind, String location) {
        Device d = getOrThrow(id);
        if (kind != null) d.setKind(ValidationRules.normalizeRequiredText(kind, "Тип устройства", 50));
        if (location != null) d.setLocation(ValidationRules.normalizeOptionalText(location, "Расположение", 200));
        return deviceRepository.save(d);
    }

    @Transactional
    public void delete(String id) {
        if (cameraRepository.existsByDevice_Id(id)) {
            throw new IllegalStateException("Нельзя удалить устройство: к нему привязаны камеры");
        }
        Device d = getOrThrow(id);
        deviceRepository.delete(d);
    }

    @Transactional(readOnly = true)
    public List<Device> findByKind(String kind) {
        return deviceRepository.findByKind(kind);
    }

    @Transactional(readOnly = true)
    public List<Device> searchByLocation(String fragment) {
        if (fragment == null || fragment.isBlank()) return List.of();
        return deviceRepository.findByLocationContainingIgnoreCase(fragment);
    }

    @Transactional(readOnly = true)
    public Page<Device> listPage(Pageable pageable) { return deviceRepository.findAll(pageable); }

    @Transactional(readOnly = true)
    public List<Device> listAll() { return deviceRepository.findAll(); }
}


package org.suffleport.zwloader.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.suffleport.zwloader.domain.Camera;
import org.suffleport.zwloader.domain.Device;
import org.suffleport.zwloader.repository.CameraRepository;
import org.suffleport.zwloader.repository.DeviceRepository;
import org.suffleport.zwloader.validation.ValidationRules;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CameraService {

    private final CameraRepository cameraRepository;
    private final DeviceRepository deviceRepository;

    @Transactional
    public Camera create(String id, String name, String rtspUrl, String location, String deviceId) {
        String safeId = ValidationRules.normalizeSystemId(id, "ID камеры");
        String safeName = ValidationRules.normalizeRequiredText(name, "Название камеры", 100);
        String safeRtspUrl = ValidationRules.normalizeRtspUrl(rtspUrl);
        String safeLocation = ValidationRules.normalizeOptionalText(location, "Расположение камеры", 200);
        String safeDeviceId = ValidationRules.normalizeOptionalSystemId(deviceId, "ID устройства");
        if (cameraRepository.existsById(safeId)) {
            throw new IllegalStateException("Камера уже существует: " + safeId);
        }
        Camera cam = new Camera();
        cam.setId(safeId);
        cam.setName(safeName);
        cam.setRtspUrl(safeRtspUrl);
        cam.setLocation(safeLocation);
        if (safeDeviceId != null) {
            Device dev = deviceRepository.findById(safeDeviceId)
                    .orElseThrow(() -> new NoSuchElementException("Устройство не найдено: " + safeDeviceId));
            cam.setDevice(dev);
        }
        return cameraRepository.save(cam);
    }

    @Transactional(readOnly = true)
    public Camera getOrThrow(String id) {
        return cameraRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Камера не найдена: " + id));
    }

    @Transactional(readOnly = true)
    public Page<Camera> listPage(Pageable pageable) {
        return cameraRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Camera> listAll() {
        return cameraRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Camera> findByDevice(String deviceId) {
        Objects.requireNonNull(deviceId, "deviceId is required");
        return cameraRepository.findByDevice_Id(deviceId);
    }

    @Transactional
    public Camera updateBasic(String id, String name, String rtspUrl, String location) {
        Camera cam = getOrThrow(id);
        if (name != null) cam.setName(ValidationRules.normalizeRequiredText(name, "Название камеры", 100));
        if (rtspUrl != null) cam.setRtspUrl(ValidationRules.normalizeRtspUrl(rtspUrl));
        if (location != null) cam.setLocation(ValidationRules.normalizeOptionalText(location, "Расположение камеры", 200));
        return cameraRepository.save(cam);
    }

    @Transactional
    public Camera reassignDevice(String id, String deviceId) {
        Camera cam = getOrThrow(id);
        String safeDeviceId = ValidationRules.normalizeOptionalSystemId(deviceId, "ID устройства");
        if (safeDeviceId == null) {
            cam.setDevice(null);
        } else {
            Device dev = deviceRepository.findById(safeDeviceId)
                    .orElseThrow(() -> new NoSuchElementException("Устройство не найдено: " + safeDeviceId));
            cam.setDevice(dev);
        }
        return cameraRepository.save(cam);
    }

    @Transactional
    public void delete(String id) {
        Camera cam = getOrThrow(id);
        cameraRepository.delete(cam);
    }
}


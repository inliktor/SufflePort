package org.suffleport.zwloader.repository;

import org.suffleport.zwloader.domain.Device;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceRepository extends JpaRepository<Device, String> {
    // Поиск устройств по типу
    List<Device> findByKind(String kind);

    // Поиск по подстроке в локации
    List<Device> findByLocationContainingIgnoreCase(String fragment);
}

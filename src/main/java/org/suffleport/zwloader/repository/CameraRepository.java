package org.suffleport.zwloader.repository;

import org.suffleport.zwloader.domain.Camera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CameraRepository extends JpaRepository<Camera, String> {
    List<Camera> findByDevice_Id(String deviceId);
}

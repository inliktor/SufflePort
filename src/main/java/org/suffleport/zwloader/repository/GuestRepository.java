package org.suffleport.zwloader.repository;

import org.suffleport.zwloader.domain.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GuestRepository extends JpaRepository<Guest, UUID> {
    List<Guest> findByLastName(String lastName);
    List<Guest> findByDocumentContainingIgnoreCase(String fragment);
}

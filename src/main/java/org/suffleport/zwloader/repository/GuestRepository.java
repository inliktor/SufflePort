package org.suffleport.zwloader.repository;

import org.suffleport.zwloader.domain.Guest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface GuestRepository extends JpaRepository<Guest, UUID> {
    List<Guest> findByLastName(String lastName);
    List<Guest> findByDocumentContainingIgnoreCase(String fragment);

    @Query("""
            select g from Guest g
            where lower(coalesce(g.fullName, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(g.lastName, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(g.firstName, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(g.middleName, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(g.company, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(g.document, '')) like lower(concat('%', :query, '%'))
               or lower(coalesce(g.phone, '')) like lower(concat('%', :query, '%'))
            """)
    Page<Guest> search(@Param("query") String query, Pageable pageable);
}

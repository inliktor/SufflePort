package org.suffleport.zwloader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.suffleport.zwloader.domain.User;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, Integer> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    List<User> findByPersonId(UUID personId);

    @Modifying
    @Query("update User u set u.personId = null where u.personId = :personId")
    int clearPersonId(@Param("personId") UUID personId);
}

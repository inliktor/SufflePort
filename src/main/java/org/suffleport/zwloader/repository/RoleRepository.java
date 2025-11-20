package org.suffleport.zwloader.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.suffleport.zwloader.domain.Role;

import java.util.Optional;

@Repository
public interface RoleRepository extends JpaRepository<Role, Integer> {
    Optional<Role> findByRoleId(Integer roleId);
    Optional<Role> findByName(String name);
}

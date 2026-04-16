package org.suffleport.zwloader.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "roles")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    @Id
    @Column(name = "role_id")
    private Integer roleId;

    @Column(name = "name", nullable = false)
    private String name;

    public boolean isAdmin() {
        return roleId != null && roleId == 1;
    }
}

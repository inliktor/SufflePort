package org.suffleport.zwloader.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "positions")
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Position {

    @Id
    @Column(name = "position_id")
    private UUID id;

    @Column(name = "position_name", nullable = false)
    private String name; // название должности

    @Column(name = "access_level")
    private Integer accessLevel;

    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    public Position() {
    }

    public Position(String name, Integer accessLevel) {
        this.id = UUID.randomUUID();
        this.name = name;
        this.accessLevel = accessLevel;
    }
}

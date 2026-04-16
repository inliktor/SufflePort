package org.suffleport.zwloader.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "devices")
public class Device {
    @Id
    @Column(name = "device_id")
    private String id;          // например "gate-1", "turnstile-2"

    @Column(name = "kind")
    private String kind;        // тип устройства: "turnstile", "door", "camera-box" и т.п.

    @Column(name = "location")
    private String location;    // где стоит: "Цех 1, ворота 3"

    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    public Device() {
    }

    public Device(String id, String kind, String location) {
        this.id = id;
        this.kind = kind;
        this.location = location;
    }
}

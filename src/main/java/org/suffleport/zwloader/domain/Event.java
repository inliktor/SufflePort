package org.suffleport.zwloader.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.hibernate.annotations.ColumnTransformer;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "events")
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uid")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Card card;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Personnel person;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Device device;

    @Column(name = "face_name")
    private String faceName;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private Direction direction;    // IN / OUT

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false)
    private Source source;          // NFC / FACE

    // Смотри ниже — JSONB как объект!
    @Column(name = "meta", columnDefinition = "jsonb")
    @ColumnTransformer(write = "?::jsonb")
    @Convert(converter = EventMetaConverter.class)
    private EventMeta meta;

    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    public Event() {}

    public Event(Card card,
                 Personnel person,
                 Device device,
                 String faceName,
                 Direction direction,
                 Source source,
                 EventMeta meta) {
        this.card = card;
        this.person = person;
        this.device = device;
        this.faceName = faceName;
        this.direction = direction;
        this.source = source;
        this.meta = meta;
    }
}

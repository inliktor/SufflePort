package org.suffleport.zwloader.domain;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "guest_visits")
public class GuestVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "visit_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Guest guest;                 // сам гость

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_person_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Personnel host;              // сотрудник-хост, кто принимает

    @Column(name = "planned_from")
    private OffsetDateTime plannedFrom;  // плановое время начала

    @Column(name = "planned_to")
    private OffsetDateTime plannedTo;    // плановое время окончания

    @Column(name = "reason")
    private String reason;               // причина визита

    @Column(name = "status")
    private String status;               // 'PLANNED', 'FINISHED', 'CANCELLED' и т.п.

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at")
    private OffsetDateTime finishedAt;

    /**
     * Идентификатор заявки, из которой создан визит.
     * Для визитов, оформленных вручную, остаётся пустым.
     */
    @Column(name = "request_id")
    private Long requestId;

    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    public GuestVisit() {
    }

    public GuestVisit(Guest guest,
                      Personnel host,
                      OffsetDateTime plannedFrom,
                      OffsetDateTime plannedTo,
                      String reason) {
        this.guest = guest;
        this.host = host;
        this.plannedFrom = plannedFrom;
        this.plannedTo = plannedTo;
        this.reason = reason;
        this.status = "PLANNED";
    }
}

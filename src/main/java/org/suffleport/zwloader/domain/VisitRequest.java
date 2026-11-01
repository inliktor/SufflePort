package org.suffleport.zwloader.domain;

import java.time.OffsetDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

/**
 * Заявка на гостевой визит.
 *
 * Заявка — это документ о намерении принять гостя. Она хранит снимок данных
 * посетителя на момент оформления: даже если карточку гостя потом поправят,
 * в заявке останется то, что было указано при подаче.
 */
@Entity
@Table(name = "visit_requests")
public class VisitRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "request_id")
    private Long id;

    /** Служебное поле для оптимистичной блокировки при одновременном решении двух согласующих. */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    /** Карточка гостя. Появляется после одобрения, если её не было. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "photoBase64"})
    private Guest guest;

    @Column(name = "visitor_last_name", nullable = false, length = 100)
    private String visitorLastName;

    @Column(name = "visitor_first_name", nullable = false, length = 100)
    private String visitorFirstName;

    @Column(name = "visitor_middle_name", length = 100)
    private String visitorMiddleName;

    @Column(name = "organization", length = 200)
    private String organization;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "purpose", length = 300)
    private String purpose;

    @Column(name = "planned_from", nullable = false)
    private OffsetDateTime plannedFrom;

    @Column(name = "planned_to", nullable = false)
    private OffsetDateTime plannedTo;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private VisitRequestStatus status = VisitRequestStatus.DRAFT;

    /** Сотрудник, к которому идёт посетитель. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "host_person_id", nullable = false)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "photoBase64"})
    private Personnel host;

    @Column(name = "author_user_id")
    private Integer authorUserId;

    @Column(name = "author_email", length = 200)
    private String authorEmail;

    /** Кто принял решение. Может быть пустым, пока заявка не рассмотрена. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_person_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "photoBase64"})
    private Personnel approver;

    @Column(name = "decision_comment", length = 1000)
    private String decisionComment;

    @Column(name = "decided_at")
    private OffsetDateTime decidedAt;

    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    /**
     * Сведения об ожидаемом визите. Не хранятся в заявке, а подставляются
     * сервисом при выдаче списка ожидаемых, чтобы охрана видела текущее состояние.
     */
    @Transient
    private Long visitId;

    @Transient
    private String visitStatus;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    @Transient
    public String getVisitorFullName() {
        StringBuilder sb = new StringBuilder();
        if (visitorLastName != null) sb.append(visitorLastName).append(' ');
        if (visitorFirstName != null) sb.append(visitorFirstName).append(' ');
        if (visitorMiddleName != null) sb.append(visitorMiddleName);
        return sb.toString().trim();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVersion() { return version; }
    public void setVersion(Long version) { this.version = version; }

    public Guest getGuest() { return guest; }
    public void setGuest(Guest guest) { this.guest = guest; }

    public String getVisitorLastName() { return visitorLastName; }
    public void setVisitorLastName(String visitorLastName) { this.visitorLastName = visitorLastName; }

    public String getVisitorFirstName() { return visitorFirstName; }
    public void setVisitorFirstName(String visitorFirstName) { this.visitorFirstName = visitorFirstName; }

    public String getVisitorMiddleName() { return visitorMiddleName; }
    public void setVisitorMiddleName(String visitorMiddleName) { this.visitorMiddleName = visitorMiddleName; }

    public String getOrganization() { return organization; }
    public void setOrganization(String organization) { this.organization = organization; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }

    public OffsetDateTime getPlannedFrom() { return plannedFrom; }
    public void setPlannedFrom(OffsetDateTime plannedFrom) { this.plannedFrom = plannedFrom; }

    public OffsetDateTime getPlannedTo() { return plannedTo; }
    public void setPlannedTo(OffsetDateTime plannedTo) { this.plannedTo = plannedTo; }

    public VisitRequestStatus getStatus() { return status; }
    public void setStatus(VisitRequestStatus status) { this.status = status; }

    public Personnel getHost() { return host; }
    public void setHost(Personnel host) { this.host = host; }

    public Integer getAuthorUserId() { return authorUserId; }
    public void setAuthorUserId(Integer authorUserId) { this.authorUserId = authorUserId; }

    public String getAuthorEmail() { return authorEmail; }
    public void setAuthorEmail(String authorEmail) { this.authorEmail = authorEmail; }

    public Personnel getApprover() { return approver; }
    public void setApprover(Personnel approver) { this.approver = approver; }

    public String getDecisionComment() { return decisionComment; }
    public void setDecisionComment(String decisionComment) { this.decisionComment = decisionComment; }

    public OffsetDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(OffsetDateTime decidedAt) { this.decidedAt = decidedAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public Long getVisitId() { return visitId; }
    public void setVisitId(Long visitId) { this.visitId = visitId; }

    public String getVisitStatus() { return visitStatus; }
    public void setVisitStatus(String visitStatus) { this.visitStatus = visitStatus; }
}

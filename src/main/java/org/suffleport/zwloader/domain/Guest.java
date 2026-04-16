package org.suffleport.zwloader.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "guests")
public class Guest {

    @Id
    @Column(name = "guest_id")
    private UUID id;

    @Column(name = "last_name")
    private String lastName;

    @Column(name = "first_name")
    private String firstName;

    @Column(name = "middle_name")
    private String middleName;

    @Column(name = "full_name")
    private String fullName;

    @Column(name = "phone")
    private String phone;

    @Column(name = "company")
    private String company;      // от какой компании

    @Column(name = "document")
    private String document;     // паспорт / пропуск (опционально)

    @Column(name = "notes")
    private String notes;        // примечания

    @Column(name = "photo_base64", columnDefinition = "TEXT")
    private String photoBase64;  // фото гостя в формате base64

    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    public Guest() {
    }

    public Guest(String lastName,
                 String firstName,
                 String middleName,
                 String phone,
                 String company) {
        this.id = UUID.randomUUID();
        this.lastName = lastName;
        this.firstName = firstName;
        this.middleName = middleName;
        this.phone = phone;
        this.company = company;
        this.fullName = buildFullName();
    }

    private String buildFullName() {
        StringBuilder sb = new StringBuilder();
        if (lastName != null) sb.append(lastName).append(" ");
        if (firstName != null) sb.append(firstName).append(" ");
        if (middleName != null) sb.append(middleName);
        return sb.toString().trim();
    }
}

package org.suffleport.zwloader.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Сущность "персонал" — сотрудник предприятия.
 * Мапится на таблицу personnel.
 */
@Getter
@Setter
@Entity
@Table(name = "personnel")
public class Personnel {

    // id — первичный ключ (person_id в БД)
    @Id
    @Column(name = "person_id")
    private UUID id;

    @Column(name = "last_name")
    private String lastName;      // фамилия

    @Column(name = "first_name")
    private String firstName;     // имя

    @Column(name = "middle_name")
    private String middleName;    // отчество

    @Column(name = "full_name")
    private String fullName;      // полное ФИО (кешируется)

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth; // дата рождения (тип DATE в БД)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "position_id")
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
    private Position position;

    @Column(name = "phone")
    private String phone;         // телефон

    @Column(name = "compreface_subject")
    private String comprefaceSubject; // subject в CompreFace

    @Column(name = "photo_base64", columnDefinition = "TEXT")
    private String photoBase64; // фото сотрудника в формате base64

    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt; // когда запись создана (ставит БД через DEFAULT now())


    // Пустой конструктор нужен JPA
    public Personnel() {
    }

    // Удобный конструктор для создания сотрудника из кода/REST-запроса
    public Personnel(String lastName,
                     String firstName,
                     String middleName,
                     LocalDate dateOfBirth,
                     Position position,
                     String phone) {
        this.id = UUID.randomUUID();
        this.lastName = lastName;
        this.firstName = firstName;
        this.middleName = middleName;
        this.dateOfBirth = dateOfBirth;
        this.position = position;
        this.phone = phone;
        this.fullName = buildFullName(); // собираем ФИО
    }

    //  это чтобы если изменились фамилия имя или отчество чтобы сразу перебилдить фио
    public void setLastName(String lastName) {
        this.lastName = lastName;
        this.fullName = buildFullName();
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
        this.fullName = buildFullName();
    }

    public void setMiddleName(String middleName) {
        this.middleName = middleName;
        this.fullName = buildFullName();
    }
    // Сборка полного ФИО
    private String buildFullName() {
        StringBuilder sb = new StringBuilder();
        if (lastName != null) sb.append(lastName).append(" ");
        if (firstName != null) sb.append(firstName).append(" ");
        if (middleName != null) sb.append(middleName);
        return sb.toString().trim();
    }

}

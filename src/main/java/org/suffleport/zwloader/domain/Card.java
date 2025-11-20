package org.suffleport.zwloader.domain;


import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import java.time.OffsetDateTime;

@Getter
@Setter
@Entity
@Table(name = "cards")
public class Card {

    // uid — первичный ключ (реальный UID карты с ридера)
    @Id
    @Column(name = "uid")
    private String uid;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "person_id")
    private Personnel person;


    @Column(name = "is_active", nullable = false)
    private boolean active;

    // БД сама ставит now(), поэтому insertable=false, updatable=false
    @Column(name = "created_at", updatable = false, insertable = false)
    private OffsetDateTime createdAt;

    public Card() {
    }

    // Удобный конструктор при необходимости
    public Card(String uid, Personnel person) {
        this.uid = uid;
        this.person = person;
        this.active = true; // по умолчанию карта активна
    }
}
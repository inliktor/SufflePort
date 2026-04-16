package org.suffleport.zwloader.domain;

import lombok.Getter;
import lombok.Setter;

/**
 * Доп. данные по событию (результаты нейросети, проверка каски/жилета и т.п.)
 */
@Getter
@Setter
public class EventMeta {

    private Boolean helmet;      // есть каска или нет
    private Boolean safety_vest;        // есть жилет или нет
    private Double confidence;   // уверенность нейросети (0..1)
    private String model;        // имя модели, например "frigate-helmet-v1"
    private String decision;     // "ALLOW", "DENY", "WARNING"

    public EventMeta() {
    }

    public EventMeta(Boolean helmet,
                     Boolean safety_vest,
                     Double confidence,
                     String model,
                     String decision) {
        this.helmet = helmet;
        this.safety_vest = safety_vest;
        this.confidence = confidence;
        this.model = model;
        this.decision = decision;
    }
}

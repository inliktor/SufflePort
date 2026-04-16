package org.suffleport.zwloader.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Конвертер между EventMeta и строкой JSON для JSONB-колонки meta.
 */
@Converter(autoApply = false)
public class EventMetaConverter implements AttributeConverter<EventMeta, String> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(EventMeta attribute) {
        if (attribute == null) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Ошибка сериализации EventMeta в JSON", e);
        }
    }

    @Override
    public EventMeta convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return new EventMeta();
        }
        try {
            return objectMapper.readValue(dbData, EventMeta.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Ошибка десериализации JSON в EventMeta", e);
        }
    }
}

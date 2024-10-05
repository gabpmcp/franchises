package com.nequi.franchises.events;

import io.vavr.collection.Map;

import java.io.Serializable;

public record Event(String type, Map<String, Serializable> data) {
    // Conversión de un valor a un tipo concreto o retorna un valor predeterminado
    public <T> T getAs(String key, Class<T> type, T defaultValue) {
        Object value = data.get(key);
        if (type.isInstance(value)) {
            return type.cast(value);
        }
        return defaultValue;
    }
}

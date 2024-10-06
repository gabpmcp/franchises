package com.nequi.franchises.util;

import io.vavr.Function1;
import io.vavr.collection.Map;

import java.io.Serializable;

public class Utils {
    @SuppressWarnings("unchecked")
    public static <K, T> T getValue(Map<K, ?> map, K key, T defaultValue) {
        var value = map.get(key).toOption();
        return value.isDefined() ? (T) value.get() : defaultValue;
    }

    // Función bind genérica
    public static Map<String, Serializable> bind(Map<String, Serializable> inputMap, Function1<Map<String, Serializable>, Boolean> predicate, Function1<Map<String, Serializable>, Map<String, Serializable>> transformation) {
        // Aplica la transformación al mapa de entrada
        return predicate.apply(inputMap) ? transformation.apply(inputMap) : inputMap;
    }
}

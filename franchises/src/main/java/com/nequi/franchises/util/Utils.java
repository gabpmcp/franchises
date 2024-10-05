package com.nequi.franchises.util;

import io.vavr.Function1;
import io.vavr.collection.Map;

public class Utils {
    @SuppressWarnings("unchecked")
    public static <K, T> T getValue(Map<K, ?> map, K key, T defaultValue) {
        var value = map.get(key).toOption();
        return value.isDefined() ? (T) value.get() : defaultValue;
    }
}

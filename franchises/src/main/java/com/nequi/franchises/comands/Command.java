package com.nequi.franchises.comands;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.collection.Stream;
import io.vavr.control.Option;

import java.io.Serializable;

import static com.nequi.franchises.util.Utils.getValue;

public record Command(String type, Map<String, Serializable> data) {

    // Conversión de un valor a un tipo concreto o retorna un valor predeterminado
    public <T> T getAs(String key, T defaultValue) {
        return getValue(data, key, defaultValue);
    }

    // Ejecuta una lista de validadores y acumula errores
    @SafeVarargs
    public final ValidationResult validate(Validator... validators) {
        var errors = Stream.of(validators)
                .map(validator -> validator.apply(this)) // Aplica cada validación
                .filter(result -> !result.isValid()); // Filtra los que fallan

        return errors.isEmpty() ? new ValidationResult(true, List.empty()) : new ValidationResult(false,
            errors.foldLeft(List.empty(), (accum, current) -> accum.appendAll(current.errors()).distinct()));
    }

    private Option<Object> getNestedValue(Map<String, ?> map, String key) {
        return Stream.of(key.split("\\."))
            .foldLeft(Option.of(map), (optMap, part) ->
                optMap.flatMap(m -> {
                    if (m instanceof Map) {
                        return Option.of(((Map<String, ?>) m).get(part));
                    }
                    return Option.none();
                })
            );
    }
}

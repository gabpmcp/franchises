package com.nequi.franchises.util;

import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import static com.nequi.franchises.IO.EventStoreFactory.*;

public class Utils {
    @SuppressWarnings("unchecked")
    public static <K, T> T getValue(Map<K, ?> map, K key, T defaultValue) {
        // Si la key es un String con ".", buscamos en el path anidado
        if (key instanceof String && ((String) key).contains(".")) {
            return (T) getValueByPath((Map<String, Object>) map, (String) key).getOrElse(defaultValue);
        }
        // Si no es anidado, buscamos el valor atómico
        var value = map.get(key).toOption();
        return value.isDefined() ? (T) value.get() : defaultValue;
    }

    private static Option<Object> getValueByPath(Map<String, Object> map, String path) {
        return Option.of(path.split("\\."))
                .flatMap(keys -> traverse(map, keys, 0));
    }

    private static Option<Object> traverse(Map<String, Object> map, String[] keys, int index) {
        return index == keys.length
                ? Option.none()
                : map.get(keys[index])
                .flatMap(v -> (index == keys.length - 1)
                        ? Option.of(v)
                        : Option.of(v).flatMap(subMap -> traverse((Map<String, Object>) subMap, keys, index + 1)));
    }

    // Función para cargar eventos desde el event store
    public static Function1<Function1<String, List<Map<String, Object>>>, Step> downloadEvents = fetchEvents -> command ->
        "CreateFranchise".equals(command.getOrElse("type", "").toString())
            ? Mono.just(buildResult(command, List.empty()))
            : Mono.fromCallable(() -> fetchEvents.apply(getValue(command, "aggregateId", "")))
            .map(events -> buildResult(command, events))
            .onErrorResume(e -> Mono.error(new RuntimeException("Error loading events for command: %s | %s".formatted(command, e))));

    @NotNull
    private static Map<String, Serializable> buildResult(Map<String, Serializable> command, List<Map<String, Object>> events) {
        return HashMap.of("command", command, "events", events);
    }

    // Función para persistir los eventos generados
    public static Function1<Function2<List<Map<String, Serializable>>, String, List<Map<String, Serializable>>>, Step> persistEvents = saveEvents -> result -> {
        // Persistencia en DynamoDB
        var events = getValue(result, "events", List.<Map<String, Serializable>>empty());
        var aggregateId = getValue(events.get(),"aggregateId", "");
        saveEvents.apply(events, aggregateId);
        return Mono.just(result);
    };

    public static String generateContentHash(String content) {
        return Try.of(() -> {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(content.getBytes(StandardCharsets.UTF_8));
        }).map(hash -> {
            StringBuilder hexString = new StringBuilder(hash.length * 2);
            for (byte b : hash) hexString.append(String.format("%02x", b));
            return hexString.toString();
        }).getOrElseThrow(e -> new RuntimeException("Error generating hash", e));
    }

    // Función para verificar idempotencia
    private static final Function1<Function1<String, Boolean>, Step> checkIdempotency = checkIfHashExists -> command -> {
        String commandContent = command.toString(); // Convertir el contenido del comando a String
        String hash = generateContentHash(commandContent); // Generar el hash del contenido

        return Mono.just(checkIfHashExists.apply(hash))
                .flatMap(exists -> exists
                    ? Mono.error(new IllegalArgumentException("Idempotent request %s, already processed".formatted(commandContent)))
                    : Mono.just(command.put("aggregateId", hash))); // Continuar si no fue procesado
    };

    // Esta función retorna la implementación de eventLoader según el entorno
    public static Map<String, Step> createEventLoader() {
        return HashMap.of(
            "fetchEvents", downloadEvents.apply(fetchEventsFromDynamo()),
            "fetchEventsTest", map -> Mono.just(HashMap.of(
            "command",
                    HashMap.of("aggregateId", "123e4567-e89b-12d3-a456-426614174000")
                        .put("type", "CreateFranchise")
                        .put("franchiseId", "STB123")
                        .put("franchiseName", "Starbucks"),
            "events",
                    List.empty())),
            "saveEvents", persistEvents.apply(saveEventsStrongly()),
            "saveEventsTest", map -> Mono.just(HashMap.of("command", HashMap.empty(), "events", List.empty())),
            "checkIdempotency", checkIdempotency.apply(checkIfHashExistsInDynamo())
        );
    }
}

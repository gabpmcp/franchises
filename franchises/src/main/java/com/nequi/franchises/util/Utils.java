package com.nequi.franchises.util;

import com.nequi.franchises.IO.EventStoreFactory;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.Tuple;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Option;
import io.vavr.control.Try;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.UUID;

import static com.nequi.franchises.IO.EventStoreFactory.*;

public class Utils {
    @SuppressWarnings("unchecked")
    public static <K, V, T> T getValue(Map<K, V> map, K key, T defaultValue) {
        return key instanceof String && ((String) key).contains(".")
            ? (T) getValueByPath((Map<String, V>) map, key.toString()).getOrElse(defaultValue)
            : map.get(key).map(value -> (T) value).getOrElse(defaultValue);
    }

    private static <V> Option<Object> getValueByPath(Map<String, V> map, String path) {
        return traverse(map, path.split("\\."), 0).toOption();
    }

    @SuppressWarnings("unchecked")
    private static <V> Try<Object> traverse(Object current, String[] keys, int index) {
        return index == keys.length
                ? Try.success(current)
                : Try.of(() -> {
            String key = keys[index];
            if (current instanceof Map<?, ?> map) {
                return traverse(((Map<String, V>) map).get(key).getOrNull(), keys, index + 1).get();
            }
            if (current instanceof Vector<?> vector) {
                int arrayIndex = Integer.parseInt(key);
                return traverse(vector.get(arrayIndex), keys, index + 1).get();
            }
            throw new IllegalArgumentException("Invalid path at: " + key);
        }).recoverWith(ex -> Try.success(io.vavr.collection.HashMap.of("error", ex.getMessage())));
    }

    // Función para cargar eventos desde el event store
    public static Function1<Function1<String, List<Map<String, Object>>>, Step> downloadEvents = fetchEvents -> command ->
        "CreateFranchise".equals(getValue(command, "type", ""))
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

        if(getValue(result, "command", HashMap.empty()).contains(Tuple.of("type", "CreateFranchise"))) {
            Function1<String, Map<String, Serializable>> createAggregateFunc =
                    getValue(result, "command.createAggregateFunc", null);
            createAggregateFunc.apply(aggregateId);
        }

        saveEvents.apply(events, aggregateId);
        return Mono.just(result.remove("command"));
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
    private static final Function2<Function1<String, Boolean>, Function2<String, String, Map<String, Serializable>>, Step> checkIdempotency = (checkIfHashExists, createAggregate) -> command -> {
        String commandContent = command.toString(); // Convertir el contenido del comando a String
        String hash = generateContentHash(commandContent); // Generar el hash del contenido
        String aggregateId = UUID.randomUUID().toString();

        return Mono.just(checkIfHashExists.apply(hash))
                .flatMap(exists -> exists && command.contains(Tuple.of("type", "FranchiseCreated")) //Solo verifica idempotencia en la creación
                    ? Mono.error(new IllegalArgumentException("Idempotent request %s, already processed".formatted(commandContent)))
                    : Mono.just(command.computeIfAbsent("aggregateId", key -> aggregateId)._2().put("createAggregateFunc", createAggregate.apply(hash)))); // Continuar si no fue procesado
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
            "saveEventsTest", map -> Mono.empty(),
            "checkIdempotency", checkIdempotency.apply(checkIfHashExistsInDynamo(), createAggregate()),
            "checkIdempotencyTest", checkIdempotency.apply(aggregateId -> false, (aggregateId, hash) -> HashMap.of("aggregateId", UUID.randomUUID().toString()))
        );
    }
}

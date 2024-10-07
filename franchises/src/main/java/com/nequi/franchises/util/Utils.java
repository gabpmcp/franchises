package com.nequi.franchises.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.nequi.franchises.comands.Command;
import com.nequi.franchises.comands.CommandFactory;
import com.nequi.franchises.config.SerializerConfig;
import io.vavr.Function0;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import io.vavr.control.Try;
import reactor.core.publisher.Mono;

import java.io.Serializable;

import static com.nequi.franchises.IO.EventStoreFactory.fetchEventsFromDynamo;
import static com.nequi.franchises.IO.EventStoreFactory.saveEventsStrongly;

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

    // Función para parsear el payload
    public static Map<String, Object> parsePayload(String payloadJson) {
        return Try.of(() -> SerializerConfig.mapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {}))
                .getOrElseThrow(() -> new RuntimeException("Error parsing payload"));
    }

    // Función para cargar eventos desde el event store
    public static Function1<Function1<String, List<Map<String, Object>>>, Step> downloadEvents = fetchEvents -> command ->
        "CreateFranchise".equals(command.getOrElse("type", "").toString())
            ? Mono.just(HashMap.of("command", command, "events", List.empty()))
            : Mono.fromCallable(() -> fetchEvents.apply(command.getOrElse("aggregateId", "").toString()))
            .map(events -> HashMap.<String, Serializable>of("command", command, "events", events))
            .onErrorResume(e -> Mono.error(new RuntimeException("Error loading events for aggregateId: " + command.getOrElse("aggregateId", "").toString(), e)));

    // Función para persistir los eventos generados
    public Function1<Function2<List<Map<String, Object>>, String, List<Map<String, Object>>>, Step> persistEvents = saveEvents -> command ->
        // Persistencia en Cassandra
        Mono.just(events);  // Simulación de persistencia

    // Esta función retorna la implementación de eventLoader según el entorno
    public static Map<String, Step> createEventLoader(String property, Function1<String, String> getEnvironment) {
        String environment = getEnvironment.apply(property); // Obtener el entorno de la variable de entorno

        var deps = HashMap.of(
            "fetchEvents", Utils.downloadEvents.apply(fetchEventsFromDynamo()),
            "fetchEventsTest", map -> Mono.just(HashMap.of("command", HashMap.empty(), "events", List.empty()))
        );

        return deps;

//        return switch (environment != null ? environment : "default") {
//            case "production" ->
//            case "develop" ->
//                    HashMap.of("simulateEvents", Function1.<Map<String, Serializable>, Mono<HashMap<String, Serializable>>>of(aggregateId -> Mono.just(
//                            HashMap.of("command", CommandFactory.createFranchiseCommand("STB").toMap(), "events", List.of( // Simulación para desarrollo
//                                    HashMap.of("eventType", "FranchiseCreated", "aggregateId", aggregateId),
//                                    HashMap.of("eventType", "BranchAdded", "branchId", "456", "franchiseId", "STB", "aggregateId", aggregateId)
//                            )))));
//            default ->
//                    HashMap.of("simulateDefault", () -> Function1.of(aggregateId -> List.empty())); // Entorno por defecto o en caso de que falte la variable
//        };
    }
}

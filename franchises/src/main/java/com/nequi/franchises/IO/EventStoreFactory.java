package com.nequi.franchises.IO;

import com.datastax.oss.driver.api.core.CqlSession;
import io.vavr.Function0;
import io.vavr.Function1;
import io.vavr.Function3;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.apache.logging.log4j.util.Strings;

import java.net.InetSocketAddress;

public class EventStoreFactory {

    // Función memoizada para crear la conexión a Cassandra
    public static Function3<String, Integer, String, CqlSession> sessionProvider = Function3.of((String host, Integer port, String datacenter) ->
        CqlSession.builder()
            .addContactPoint(new InetSocketAddress(host, port)) // Dirección de Cassandra
            .withLocalDatacenter(datacenter)  // Nombre del datacenter
            .build()).memoized();

    public static CqlSession getSession = sessionProvider.apply(System.getProperty("HOST"), Integer.parseInt(System.getProperty("PORT")), System.getProperty("CASSANDRA_DATACENTER"));

    // Esta función retorna la implementación de eventLoader según el entorno
    public static Function1<String, List<Map<String, Object>>> createEventLoader(String property, Function1<String, String> getEnvironment) {
        String environment = getEnvironment.apply(property); // Obtener el entorno de la variable de entorno

        return switch (Strings.isNotBlank(environment) ? environment : "default") {
            case "prod" -> EventStoreFactory::fetchEventsFromCassandra;
            case "dev" -> aggregateId -> List.of( // Simulación para desarrollo
                    HashMap.of("eventType", "FranchiseCreated", "aggregateId", aggregateId),
                    HashMap.of("eventType", "BranchAdded", "branchId", "456", "franchiseId", "STB", "aggregateId", aggregateId)
            );
            default -> aggregateId -> List.empty(); // Entorno por defecto o en caso de que falte la variable
        };
    }

    // Función que interactúa con Cassandra para obtener eventos según el franchiseId
    public static List<Map<String, Object>> fetchEventsFromCassandra(String aggregateId) {
        // Aquí iría la lógica real para obtener los eventos desde Cassandra, sin depender de Reactor
        return List.of(
                HashMap.of("eventType", "FranchiseCreated", "franchiseId", "STB", "aggregateId", aggregateId),
                HashMap.of("eventType", "BranchAdded", "branchId", "123", "franchiseId", "STB", "aggregateId", aggregateId)
        );
    }
}

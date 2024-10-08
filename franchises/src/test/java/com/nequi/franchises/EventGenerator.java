package com.nequi.franchises;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;
import io.vavr.collection.Map;
import org.junit.jupiter.api.Test;
import org.quicktheories.WithQuickTheories;
import org.quicktheories.core.Gen;
import org.quicktheories.generators.SourceDSL;
import reactor.core.publisher.Mono;

import java.io.Serializable;

import static com.nequi.franchises.CommandController.projectState;

public class EventGenerator implements WithQuickTheories {

    // Generadores para tus IDs
    public Gen<String> generateFranchiseId() {
        return SourceDSL.integers().between(1000, 9999)
                .map(n -> "FR-" + n);
    }

    public Gen<String> generateBranchId() {
        return SourceDSL.integers().between(100, 999)
                .map(n -> "BR-" + n);
    }

    public Gen<String> generateProductId() {
        return SourceDSL.integers().between(10000, 99999)
                .map(n -> "PR-" + n);
    }

    // Generador de eventos para pruebas
    public Gen<Map<String, Serializable>> generateEvent() {
        return SourceDSL.strings().allPossible().ofLength(10)  // Genera un string para el tipo de evento
                .map(eventType -> {
                    Map<String, Serializable> event = HashMap.of("type", eventType);

                    switch (eventType) {
                        case "FranchiseCreated":
                            event = event.put("franchiseId", "FR-" + (int)(Math.random() * 10000));
                            event = event.put("franchiseName", "Test Franchise");
                            break;
                        case "BranchAdded":
                            event = event.put("branchId", "BR-" + (int)(Math.random() * 1000));
                            event = event.put("branchName", "Test Branch");
                            break;
                        case "ProductAddedToBranch":
                            event = event.put("branchId", "BR-" + (int)(Math.random() * 1000));
                            event = event.put("productId", "PR-" + (int)(Math.random() * 100000));
                            event = event.put("productName", "Test Product");
                            event = event.put("initialStock", 100);
                            break;
                        // Agrega otros eventos según sea necesario
                    }
                    return event;
                });
    }

    // Definir las pruebas basadas en propiedades
    @Test
    public void testStateProjection() {
        qt()
                .forAll(generateEvent(), generateEvent()) // Genera dos eventos para probar
                .check((event1, event2) -> {
                    Map<String, Serializable> initialState = HashMap.empty(); // Estado inicial vacío

                    // Simula la aplicación de eventos en el estado
                    Mono<Map<String, Serializable>> projectedState = projectState.apply(
                            initialState,
                            List.of(event2) // Lista de eventos generados
                    );

                    // Verificar que el estado resultante es válido
                    return Boolean.TRUE.equals(projectedState.map(state -> state.containsKey("franchiseExists") && state.get("franchiseExists").isDefined()).block());
                });
    }
}

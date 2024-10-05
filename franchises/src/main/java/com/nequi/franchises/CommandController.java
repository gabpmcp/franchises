package com.nequi.franchises;

import com.nequi.franchises.comands.Command;
import com.nequi.franchises.comands.ValidationResult;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;

import io.vavr.collection.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.function.Function;

import static com.nequi.franchises.comands.Validators.*;

@RestController
public class CommandController {

    @PostMapping("/command")
    public Mono<ResponseEntity<Map<String, Serializable>>> handleCommand(@RequestBody Map<String, Serializable> commandMap) {
        return createCommandHandler().apply(commandMap) // Directamente invoca createCommandHandler
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(HashMap.of("error", e.getMessage()))));
    }

    // Función para crear el handler reactivo que maneja los comandos
    private Function<Map<String, Serializable>, Mono<Map<String, Serializable>>> createCommandHandler() {
        return commandMap -> Mono.just(commandMap)
                .flatMap(this::validateCommand);    // Validación del comando
//                .flatMap(this::downloadEvents)         // Carga de eventos del event store
//                .flatMap(this::projectState)       // Proyección del estado a partir de los eventos
//                .flatMap(this::decide)             // Toma de decisiones de negocio
//                .flatMap(this::persistEvents)      // Persistencia de los eventos generados
//                .flatMap(this::notifyEvents);      // Notificación de eventos externos
    }

    // Función de validación del comando
    private Mono<Map<String, Serializable>> validateCommand(Map<String, Serializable> command) {
        var a = Mono.justOrEmpty(command.getOrElse("type", ""))
                .map(type -> switch (type.toString()) {
                    case "CreateFranchise" -> new Command("CreateFranchise", command)
                            .validate(required("franchiseName"), isNonEmptyString("franchiseName"));
                    case "UpdateFranchiseName" -> new Command("UpdateFranchiseName", command)
                            .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                                    required("newName"), isNonEmptyString("newName"),
                                    matchesPattern("franchiseId", "[A-Z]*\\d+"));
                    case "AddBranch" -> new Command("AddBranch", command)
                            .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                                    matchesPattern("franchiseId", "[A-Z]*\\d+"),
                                    required("branchName"), isNonEmptyString("branchName"));
                    case "UpdateBranchName" -> new Command("UpdateBranchName", command)
                            .validate(required("branchId"), isNonEmptyString("branchId"),
                                    required("newName"), isNonEmptyString("newName"));
                    case "AddProductToBranch" -> new Command("AddProductToBranch", command)
                            .validate(required("branchId"), isNonEmptyString("branchId"),
                                    required("productName"), isNonEmptyString("productName"),
                                    required("initialStock"), isPositive("initialStock"));
                    case "UpdateProductStock" -> new Command("UpdateProductStock", command)
                            .validate(required("productId"), isNonEmptyString("productId"),
                                    required("quantityChange"), isNumeric("quantityChange"));
                    case "RemoveProductFromBranch" -> new Command("RemoveProductFromBranch", command)
                            .validate(required("branchId"), isNonEmptyString("branchId"),
                                    required("productId"), isNonEmptyString("productId"));
                    case "RemoveBranch" -> new Command("RemoveBranch", command)
                            .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                                    matchesPattern("franchiseId", "[A-Z]*\\d+"), required("branchId"), isNonEmptyString("branchId"));
                    case "RemoveFranchise" -> new Command("RemoveFranchise", command)
                            .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                                    matchesPattern("franchiseId", "[A-Z]*\\d+"));
                    case "NotifyStockDepleted" -> new Command("NotifyStockDepleted", command)
                            .validate(required("productId"), isNonEmptyString("productId"));
                    case "TransferProductBetweenBranches" -> new Command("TransferProductBetweenBranches", command)
                            .validate(required("fromBranchId"), isNonEmptyString("fromBranchId"),
                                    required("toBranchId"), isNonEmptyString("toBranchId"),
                                    required("productId"), isNonEmptyString("productId"),
                                    required("quantity"), isPositive("quantity"));
                    case "AdjustProductStock" -> new Command("AdjustProductStock", command)
                            .validate(required("productId"), isNonEmptyString("productId"),
                                    required("newStock"), isPositive("newStock"));
                    default -> new ValidationResult(false, List.of("Type doesn't exist in the system!"));
                }).map((ValidationResult result) -> result.isValid() ? command // Devuelve el comando si es válido
                     : result.toMap()) // Devuelve el ValidationResult si no es válido
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Missing command type")));

        return a;
    }

    // Función para cargar eventos desde el event store
    private Mono<Map<String, Serializable>> downloadEvents(Map<String, Serializable> command) {
        // Lógica para obtener eventos desde Cassandra
        return Mono.just(command);  // Simulación de eventos cargados
    }

    // Función para proyectar el estado actual
    private Mono<Map<String, Serializable>> projectState(Map<String, Serializable> commandMap) {
        // Proyección del estado
        return Mono.just(commandMap);  // Simulación de proyección de estado
    }

    // Función para tomar decisiones de negocio
    private Mono<Map<String, Serializable>> decide(Map<String, Serializable> commandMap) {
        // Toma de decisiones según el estado y el comando
        return Mono.just(commandMap);  // Simulación de generación de eventos
    }

    // Función para persistir los eventos generados
    private Mono<Map<String, Serializable>> persistEvents(Map<String, Serializable> events) {
        // Persistencia en Cassandra
        return Mono.just(events);  // Simulación de persistencia
    }

    // Función para notificar eventos a sistemas externos
    private Mono<Map<String, Serializable>> notifyEvents(Map<String, Serializable> events) {
        // Notificación de eventos
        return Mono.just(events);  // Simulación de notificación
    }
}

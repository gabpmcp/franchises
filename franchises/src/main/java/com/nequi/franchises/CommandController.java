package com.nequi.franchises;

import com.nequi.franchises.comands.Command;
import com.nequi.franchises.comands.ValidationResult;
import com.nequi.franchises.IO.EventStoreFactory;
import com.nequi.franchises.util.Step;
import com.nequi.franchises.util.Utils;
import io.vavr.Function0;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;

import io.vavr.collection.Map;
import io.vavr.control.Option;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.function.Function;

import static com.nequi.franchises.comands.Validators.*;

@RestController
public class CommandController {

    // Inyectamos la función del eventLoader usando la fábrica
    private final Map<String, Step> depsLoader = Utils.createEventLoader();

    @PostMapping("/command")
    public Mono<ResponseEntity<Map<String, Serializable>>> handleCommand(@RequestBody java.util.Map<String, Serializable> commandMap) {
        return createCommandHandler().apply(HashMap.ofAll(commandMap)) // Directamente invoca createCommandHandler
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(HashMap.of("error", e.getMessage()))));
    }

    // Función para crear el handler reactivo que maneja los comandos
    private Function<Map<String, Serializable>, Mono<Map<String, Serializable>>> createCommandHandler() {
        return commandMap -> Mono.just(commandMap)
                .flatMap(this::validateCommand)    // Validación del comando
                .flatMap(depsLoader.get("fetchEvents").get());         // Carga de eventos del event store
//                .flatMap(this::projectState)       // Proyección del estado a partir de los eventos
//                .flatMap(this::decide)             // Toma de decisiones de negocio
//                .flatMap(this::persistEvents)      // Persistencia de los eventos generados
//                .flatMap(this::notifyEvents);      // Notificación de eventos externos
    }

    // Función de validación del comando
    private Mono<Map<String, Serializable>> validateCommand(Map<String, Serializable> command) {
        return Mono.justOrEmpty(command.getOrElse("type", ""))
            .map(type -> switch (type.toString()) {
                case "CreateFranchise" -> new Command("CreateFranchise", command)
                        .validate(required("franchiseName"), isNonEmptyString("franchiseName"),
                            required("franchiseId"), isNonEmptyString("franchiseId"));
                case "UpdateFranchiseName" -> new Command("UpdateFranchiseName", command)
                        .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                            required("newName"), isNonEmptyString("newName"),
                            matchesPattern("franchiseId", "[A-Z]*\\d+"));
                case "AddBranch" -> new Command("AddBranch", command)
                        .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                            matchesPattern("franchiseId", "[A-Z]*\\d+"),
                            required("branchId"), isNonEmptyString("branchId"),
                            matchesPattern("branchId", "[A-Z]*\\d+"),
                            required("branchName"), isNonEmptyString("branchName"));
                case "AddProductToBranch" -> new Command("AddProductToBranch", command)
                        .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                            matchesPattern("franchiseId", "[A-Z]*\\d+"),
                            required("branchId"), isNonEmptyString("branchId"), matchesPattern("branchId", "[A-Z]*\\d+"),
                            required("productName"), isNonEmptyString("productName"),
                            required("initialStock"), isPositive("initialStock"), isNumeric("initialStock"));
                case "UpdateBranchName" -> new Command("UpdateBranchName", command)
                        .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                            matchesPattern("franchiseId", "[A-Z]*\\d+"),
                            required("branchId"), isNonEmptyString("branchId"),
                            matchesPattern("branchId", "[A-Z]*\\d+"), required("newName"),
                            isNonEmptyString("newName"));
                case "UpdateProductStock" -> new Command("UpdateProductStock", command)
                        .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                            matchesPattern("franchiseId", "[A-Z]*\\d+"),
                            required("branchId"), isNonEmptyString("branchId"),
                            matchesPattern("branchId", "[A-Z]*\\d+"),
                            required("productId"), isNonEmptyString("productId"), isUUID("productId"),
                            required("quantityChange"), isNumeric("quantityChange"));
                case "RemoveProductFromBranch" -> new Command("RemoveProductFromBranch", command)
                        .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                            matchesPattern("franchiseId", "[A-Z]*\\d+"), required("branchId"),
                            isNonEmptyString("branchId"), matchesPattern("branchId", "[A-Z]*\\d+"),
                            required("productId"), isNonEmptyString("productId"));
                case "RemoveBranch" -> new Command("RemoveBranch", command)
                        .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                            matchesPattern("franchiseId", "[A-Z]*\\d+"),
                            required("branchId"), isNonEmptyString("branchId"), matchesPattern("branchId", "[A-Z]*\\d+"));
                case "RemoveFranchise" -> new Command("RemoveFranchise", command)
                        .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                                matchesPattern("franchiseId", "[A-Z]*\\d+"));
                case "NotifyStockDepleted" -> new Command("NotifyStockDepleted", command)
                        .validate(required("franchiseId"), isNonEmptyString("franchiseId"),
                            matchesPattern("franchiseId", "[A-Z]*\\d+"), required("branchId"),
                            isNonEmptyString("branchId"), matchesPattern("branchId", "[A-Z]*\\d+"),
                            required("productId"), isNonEmptyString("productId"), isUUID("productId"));
                case "TransferProductBetweenBranches" -> new Command("TransferProductBetweenBranches", command)
                        .validate(required("fromBranchId"), isNonEmptyString("fromBranchId"),
                                required("toBranchId"), isNonEmptyString("toBranchId"),
                                required("productId"), isNonEmptyString("productId"),
                                required("quantity"), isNumeric("quantity"), isPositive("quantity"));
                case "AdjustProductStock" -> new Command("AdjustProductStock", command)
                        .validate(required("productId"), isNonEmptyString("productId"),
                                required("newStock"), isNumeric("newStock"), isPositive("newStock"));
                default -> new ValidationResult(false, List.of("Type doesn't exist in the system!"));
            }).flatMap((ValidationResult result) -> result.isValid()
                ? Mono.just(command) // Si es válido, devolver el comando
                : Mono.error(new IllegalArgumentException("Validation failed: " + result.errors().mkString(", ")))); // Si no es válido, devolver un Mono.error con los errores de validación)
    }

//    private Mono<Map<String, Serializable>> projectState(List<Map<String, Object>> events) {
//        // Estado inicial vacío
//        Map<String, Serializable> initialState = io.vavr.collection.HashMap.empty();
//
//        // Proyectar cada evento sobre el estado
//        return Mono.just(events.foldLeft(initialState, (state, event) -> {
//            // Dispatcher de eventos
//            return switch (event.get("eventType").toString()) {
//                case "FranchiseCreated" -> state.put("franchiseId", event.get("aggregateId"))
//                        .put("franchiseName", event.get("payload"));
//                case "FranchiseNameUpdated" -> state.put("oldFranchiseName", state.get("franchiseName"))
//                        .put("franchiseName", event.get("payload").get("newFranchiseName"));
//                case "BranchAdded" -> state.put("branchId", event.get("payload").get("branchId"))
//                        .put("branchName", event.get("payload").get("branchName"));
//                case "BranchNameUpdated" -> state.put("oldBranchName", state.get("branchName"))
//                        .put("branchName", event.get("payload").get("newBranchName"));
//                case "ProductAddedToBranch" -> state.put("productId", event.get("payload").get("productId"))
//                        .put("initialStock", event.get("payload").get("initialStock"));
//                case "ProductStockUpdated" -> state.put("productId", event.get("payload").get("productId"))
//                        .put("stockChange", event.get("payload").get("quantityChange"));
//                case "ProductRemovedFromBranch" -> state.remove("productId")
//                        .put("branchId", event.get("payload").get("branchId"));
//                case "BranchRemoved" -> state.remove("branchId");
//                case "FranchiseRemoved" -> state.remove("franchiseId").remove("branchId");
//                case "ProductTransferredBetweenBranches" -> state.put("oldBranchId", state.get("branchId"))
//                        .put("branchId", event.get("payload").get("toBranchId"))
//                        .put("productId", event.get("payload").get("productId"));
//                case "ProductStockAdjusted" -> state.put("productId", event.get("payload").get("productId"))
//                        .put("newStock", event.get("payload").get("newStock"));
//                case "NotifyStockDepleted" -> state.put("productId", event.get("payload").get("productId"));
//                default -> state; // Si no se reconoce el evento, se devuelve el estado tal como está.
//            };
//        }));
//    }

    // Función para tomar decisiones de negocio
    private Mono<Map<String, Serializable>> decide(Map<String, Serializable> commandMap) {
        // Toma de decisiones según el estado y el comando
        return Mono.just(commandMap);  // Simulación de generación de eventos
    }



    // Función para notificar eventos a sistemas externos
    private Mono<Map<String, Serializable>> notifyEvents(Map<String, Serializable> events) {
        // Notificación de eventos
        return Mono.just(events);  // Simulación de notificación
    }
}

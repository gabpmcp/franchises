package com.nequi.franchises;

import com.nequi.franchises.comands.Command;
import com.nequi.franchises.comands.ValidationResult;
import com.nequi.franchises.IO.EventStoreFactory;
import com.nequi.franchises.util.Step;
import com.nequi.franchises.util.Utils;
import io.vavr.Function0;
import io.vavr.Function1;
import io.vavr.Function2;
import io.vavr.Function3;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;

import io.vavr.collection.Map;
import io.vavr.control.Option;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.io.Serializable;
import java.util.UUID;
import java.util.function.Function;

import static com.nequi.franchises.comands.Validators.*;
import static com.nequi.franchises.util.Utils.getValue;

@RestController
public class CommandController {

    // Inyectamos la función del eventLoader usando la fábrica
    private final Map<String, Step> depsLoader = Utils.createEventLoader();
    private static final Function2<String, String, List<Map<String, Serializable>>> initStateMachine = (franchiseId, franchiseName) ->
        List.of(HashMap.of("aggregateId", UUID.randomUUID(), "type", "FranchiseCreated", "payload", HashMap.of("franchiseId", franchiseId, "franchiseName", franchiseName)));

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
                // Carga de eventos del event store
                .flatMap(depsLoader.get("fetchEvents").get())
                // Proyección del estado a partir de los eventos
                .flatMap(result -> {
                    var projectionPrepared = projectState.apply(getValue(result, "command", HashMap.empty()), HashMap.empty());
                    return projectionPrepared.apply(getValue(result, "events", List.empty()));
                });
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

    public static Function3<Map<String, Serializable>, Map<String, Serializable>, List<Map<String, Serializable>>, Mono<Map<String, Serializable>>> projectState = (command, initialState, events) ->
        Mono.defer(() -> ("CreateFranchise".equals(getValue(command, "type", "")) && events.isEmpty()
                        ? Mono.just(initStateMachine.apply(getValue(command, "franchiseId", ""), getValue(command, "franchiseName", "")))
                        : Mono.just(events)))
        .flatMap(evts -> Mono.just(evts.foldLeft(initialState, (state, event) ->
            switch (getValue(event, "type", "")) {
                case "FranchiseCreated" -> state
                        .put("franchiseExists", true)
                        .put("franchiseId", getValue(event, "aggregateId", ""))
                        .put("franchiseName", getValue(getValue(event, "payload", HashMap.empty()), "franchiseName", ""))
                        .put("branches", HashMap.empty());

                case "FranchiseNameUpdated" -> state.put("franchiseName", getValue(getValue(event, "payload", HashMap.empty()), "newFranchiseName", ""));

                case "BranchAdded" -> state.put("branches", getBranches(state).put(
                        getValue(getValue(event, "payload", HashMap.empty()), "branchId", ""),
                        HashMap.of("branchName", getValue(getValue(event, "payload", HashMap.empty()), "branchName", ""), "products", HashMap.empty())));

                case "BranchNameUpdated" -> state.put("branches", getBranches(state).put(
                        getValue(getValue(event, "payload", HashMap.empty()), "branchId", ""),
                        getValue(getBranches(state), getValue(getValue(event, "payload", HashMap.empty()), "branchId", ""), HashMap.<String, Serializable>empty())
                                .put("branchName", getValue(getValue(event, "payload", HashMap.empty()), "newBranchName", ""))));

                case "ProductAddedToBranch" -> state.put("branches", getBranches(state).put(
                        getValue(getValue(event, "payload", HashMap.empty()), "branchId", ""),
                        getValue(getBranches(state), getValue(getValue(event, "payload", HashMap.empty()), "branchId", ""), HashMap.<String, Serializable>empty())
                                .put("products", getProducts(getValue(getBranches(state), getValue(getValue(event, "payload", HashMap.empty()), "branchId", ""), HashMap.<String, Serializable>empty())
                                        .put(getValue(getValue(event, "payload", HashMap.empty()), "productId", ""),
                                                HashMap.of("productName", getValue(getValue(event, "payload", HashMap.empty()), "productName", ""),
                                                        "currentStock", getValue(getValue(event, "payload", HashMap.empty()), "initialStock", 0)))))));

                case "ProductStockUpdated" -> {
                    String branchId = getValue(getValue(event, "payload", HashMap.empty()), "branchId", "");
                    String productId = getValue(getValue(event, "payload", HashMap.empty()), "productId", "");
                    int quantityChange = getValue(getValue(event, "payload", HashMap.empty()), "quantity", 0);
                    yield state.put("branches", getBranches(state).put(branchId, updateProductStock(getValue(getBranches(state), branchId, HashMap.<String, Serializable>empty()), productId, quantityChange)));
                }

                case "ProductStockAdjusted" -> {
                    String branchId = getValue(getValue(event, "payload", HashMap.empty()), "branchId", "");
                    String productId = getValue(getValue(event, "payload", HashMap.empty()), "productId", "");
                    yield state.put("branches", getBranches(state).put(
                            branchId,
                            getValue(getBranches(state), branchId, HashMap.<String, Serializable>empty())
                                    .put("products", getProducts(getValue(getBranches(state), branchId, HashMap.<String, Serializable>empty())
                                            .put(productId, HashMap.of("currentStock", getValue(getValue(event, "payload", HashMap.empty()), "newStock", 0)))))));
                }

                case "ProductTransferredBetweenBranches" -> state.put("branches", transferProductBetweenBranches(state, getValue(event, "payload", HashMap.empty())));

                case "ProductRemovedFromBranch" -> state.put("branches", getBranches(state).put(
                        getValue(getValue(event, "payload", HashMap.empty()), "branchId", ""),
                        getValue(getBranches(state), getValue(getValue(event, "payload", HashMap.empty()), "branchId", ""), HashMap.<String, Serializable>empty())
                                .put("products", getProducts(getValue(getBranches(state), getValue(getValue(event, "payload", HashMap.empty()), "branchId", ""), HashMap.<String, Serializable>empty())
                                        .remove(getValue(getValue(event, "payload", HashMap.empty()), "productId", ""))))));

                case "BranchRemoved" -> state.put("branches", getBranches(state).remove(getValue(getValue(event, "payload", HashMap.empty()), "branchId", "")));

                case "FranchiseRemoved" -> state.put("franchiseExists", false).put("branches", HashMap.empty());

                case "NotifyStockDepleted" -> state;

                default -> state;
            })
        ));


    // Función auxiliar para obtener el mapa de sucursales (branches)
    private static Map<String, Map<String, Serializable>> getBranches(Map<String, Serializable> state) {
        return getValue(state, "branches", HashMap.empty());
    }

    // Función auxiliar para obtener el mapa de productos dentro de una sucursal
    private static Map<String, Map<String, Serializable>> getProducts(Map<String, Serializable> branchData) {
        return getValue(branchData, "products", HashMap.empty());
    }

    // Función para actualizar el stock de un producto
    private static Map<String, Serializable> updateProductStock(Map<String, Serializable> branchData, String productId, int quantityChange) {
        return getProducts(branchData)
                .get(productId)
                .map(product -> product.put("currentStock", getValue(product, "currentStock", 0) + quantityChange))
                .getOrElse(HashMap.of("currentStock", quantityChange));
    }

    // Función para manejar la transferencia de productos entre sucursales
    private static Map<String, Map<String, Serializable>> transferProductBetweenBranches(Map<String, Serializable> state, Map<String, Object> payload) {
        String fromBranchId = getValue(payload, "fromBranchId", "");
        String toBranchId = getValue(payload, "toBranchId", "");
        String productId = getValue(payload, "productId", "");
        int quantity = Integer.parseInt(getValue(payload, "quantity", "0"));

        Map<String, Map<String, Serializable>> branches = getBranches(state);

        // Actualizar sucursal de origen y destino en un solo paso
        return branches.put(fromBranchId, updateProductStock(branches.get(fromBranchId).getOrElse(HashMap.empty()), productId, -quantity))
                .put(toBranchId, updateProductStock(branches.get(toBranchId).getOrElse(HashMap.empty()), productId, quantity));
    }


    // Función para tomar decisiones de negocio
//    private Mono<List<Map<String, Object>>> decide(Map<String, Serializable> command, Map<String, Serializable> state) {
//        // Dispatcher por tipo de comando
//        return Mono.justOrEmpty(command.get("type").toString())
//                .flatMap(commandType -> switch (commandType) {
//                    case "CreateFranchise" -> {
//                        // Validación: No se puede crear una franquicia si ya existe
//                        if (state.getOrElse("franchiseExists", false)) {
//                            yield Mono.error(new IllegalStateException("La franquicia ya existe."));
//                        } else {
//                            yield Mono.just(List.of(io.vavr.collection.HashMap.of(
//                                    "eventType", "FranchiseCreated",
//                                    "aggregateId", command.get("franchiseId"),
//                                    "payload", io.vavr.collection.HashMap.of(
//                                            "franchiseName", command.get("franchiseName")
//                                    )
//                            )));
//                        }
//                    }
//
//                    case "UpdateFranchiseName" -> {
//                        // Validación: La franquicia debe existir
//                        if (!state.getOrElse("franchiseExists", false)) {
//                            yield Mono.error(new IllegalStateException("La franquicia no existe."));
//                        } else {
//                            yield Mono.just(List.of(io.vavr.collection.HashMap.of(
//                                    "eventType", "FranchiseNameUpdated",
//                                    "aggregateId", command.get("franchiseId"),
//                                    "payload", io.vavr.collection.HashMap.of(
//                                            "newFranchiseName", command.get("newName"),
//                                            "oldFranchiseName", state.get("franchiseName")
//                                    )
//                            )));
//                        }
//                    }
//
//                    case "AddBranch" -> {
//                        // Validación: La franquicia debe existir
//                        if (!state.getOrElse("franchiseExists", false)) {
//                            yield Mono.error(new IllegalStateException("La franquicia no existe."));
//                        } else {
//                            // Validación: No se puede agregar una sucursal que ya existe
//                            Map<String, Serializable> branches = state.getOrElse("branches", io.vavr.collection.HashMap.empty());
//                            if (branches.containsKey(command.get("branchId").toString())) {
//                                yield Mono.error(new IllegalStateException("La sucursal ya existe en la franquicia."));
//                            } else {
//                                yield Mono.just(List.of(io.vavr.collection.HashMap.of(
//                                        "eventType", "BranchAdded",
//                                        "aggregateId", command.get("franchiseId"),
//                                        "payload", io.vavr.collection.HashMap.of(
//                                                "branchId", command.get("branchId"),
//                                                "branchName", command.get("branchName")
//                                        )
//                                )));
//                            }
//                        }
//                    }
//
//                    case "UpdateBranchName" -> {
//                        // Validación: La sucursal debe existir
//                        Map<String, Serializable> branches = state.getOrElse("branches", io.vavr.collection.HashMap.empty());
//                        if (!branches.containsKey(command.get("branchId").toString())) {
//                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
//                        } else {
//                            String oldBranchName = branches.get(command.get("branchId").toString()).toString();
//                            yield Mono.just(List.of(io.vavr.collection.HashMap.of(
//                                    "eventType", "BranchNameUpdated",
//                                    "aggregateId", command.get("franchiseId"),
//                                    "payload", io.vavr.collection.HashMap.of(
//                                            "branchId", command.get("branchId"),
//                                            "newBranchName", command.get("newName"),
//                                            "oldBranchName", oldBranchName
//                                    )
//                            )));
//                        }
//                    }
//
//                    case "AddProductToBranch" -> {
//                        // Validación: La sucursal debe existir
//                        Map<String, Map<String, Serializable>> branches = state.getOrElse("branches", io.vavr.collection.HashMap.empty());
//                        if (!branches.containsKey(command.get("branchId").toString())) {
//                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
//                        } else {
//                            Map<String, Serializable> products = branches.get(command.get("branchId").toString()).getOrElse(io.vavr.collection.HashMap.empty());
//                            if (products.containsKey(command.get("productId").toString())) {
//                                yield Mono.error(new IllegalStateException("El producto ya existe en la sucursal."));
//                            } else {
//                                yield Mono.just(List.of(io.vavr.collection.HashMap.of(
//                                        "eventType", "ProductAddedToBranch",
//                                        "aggregateId", command.get("franchiseId"),
//                                        "payload", io.vavr.collection.HashMap.of(
//                                                "branchId", command.get("branchId"),
//                                                "productId", command.get("productId"),
//                                                "productName", command.get("productName"),
//                                                "initialStock", command.get("initialStock")
//                                        )
//                                )));
//                            }
//                        }
//                    }
//
//                    case "UpdateProductStock" -> {
//                        // Validación: La sucursal y el producto deben existir
//                        Map<String, Map<String, Serializable>> branches = state.getOrElse("branches", io.vavr.collection.HashMap.empty());
//                        if (!branches.containsKey(command.get("branchId").toString())) {
//                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
//                        } else {
//                            Map<String, Serializable> products = branches.get(command.get("branchId").toString()).getOrElse(io.vavr.collection.HashMap.empty());
//                            if (!products.containsKey(command.get("productId").toString())) {
//                                yield Mono.error(new IllegalStateException("El producto no existe en la sucursal."));
//                            } else {
//                                int currentStock = Integer.parseInt(products.get(command.get("productId").toString()).toString());
//                                int quantityChange = Integer.parseInt(command.get("quantityChange").toString());
//                                int newStock = currentStock + quantityChange;
//
//                                // Validación: El stock resultante no puede ser negativo
//                                if (newStock < 0) {
//                                    yield Mono.error(new IllegalStateException("El stock resultante no puede ser negativo."));
//                                } else {
//                                    yield Mono.just(List.of(io.vavr.collection.HashMap.of(
//                                            "eventType", "ProductStockUpdated",
//                                            "aggregateId", command.get("franchiseId"),
//                                            "payload", io.vavr.collection.HashMap.of(
//                                                    "branchId", command.get("branchId"),
//                                                    "productId", command.get("productId"),
//                                                    "quantityChange", quantityChange
//                                            )
//                                    )));
//                                }
//                            }
//                        }
//                    }
//
//                    case "TransferProductBetweenBranches" -> {
//                        // Validación: Ambas sucursales deben existir
//                        Map<String, Map<String, Serializable>> branches = state.getOrElse("branches", io.vavr.collection.HashMap.empty());
//                        if (!branches.containsKey(command.get("fromBranchId").toString()) || !branches.containsKey(command.get("toBranchId").toString())) {
//                            yield Mono.error(new IllegalStateException("Una o ambas sucursales no existen."));
//                        } else {
//                            Map<String, Serializable> fromProducts = branches.get(command.get("fromBranchId").toString()).getOrElse(io.vavr.collection.HashMap.empty());
//                            Map<String, Serializable> toProducts = branches.get(command.get("toBranchId").toString()).getOrElse(io.vavr.collection.HashMap.empty());
//
//                            if (!fromProducts.containsKey(command.get("productId").toString())) {
//                                yield Mono.error(new IllegalStateException("El producto no existe en la sucursal de origen."));
//                            } else {
//                                int currentStock = Integer.parseInt(fromProducts.get(command.get("productId").toString()).toString());
//                                int quantity = Integer.parseInt(command.get("quantity").toString());
//
//                                if (quantity > currentStock) {
//                                    yield Mono.error(new IllegalStateException("Stock insuficiente en la sucursal de origen."));
//                                } else {
//                                    yield Mono.just(List.of(io.vavr.collection.HashMap.of(
//                                            "eventType", "ProductTransferredBetweenBranches",
//                                            "aggregateId", command.get("franchiseId"),
//                                            "payload", io.vavr.collection.HashMap.of(
//                                                    "fromBranchId", command.get("fromBranchId"),
//                                                    "toBranchId", command.get("toBranchId"),
//                                                    "productId", command.get("productId"),
//                                                    "quantity", quantity
//                                            )
//                                    )));
//                                }
//                            }
//                        }
//                    }
//
//                    case "RemoveProductFromBranch" -> {
//                        // Validación: La sucursal y el producto deben existir
//                        Map<String, Map<String, Serializable>> branches = state.getOrElse("branches", io.vavr.collection.HashMap.empty());
//                        if (!branches.containsKey(command.get("branchId").toString())) {
//                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
//                        } else {
//                            Map<String, Serializable> products = branches.get(command.get("branchId").toString()).getOrElse(io.vavr.collection.HashMap.empty());
//                            if (!products.containsKey(command.get("productId").toString())) {
//                                yield Mono.error(new IllegalStateException("El producto no existe en la sucursal."));
//                            } else {
//                                yield Mono.just(List.of(io.vavr.collection.HashMap.of(
//                                        "eventType", "ProductRemovedFromBranch",
//                                        "aggregateId", command.get("franchiseId"),
//                                        "payload", io.vavr.collection.HashMap.of(
//                                                "branchId", command.get("branchId"),
//                                                "productId", command.get("productId")
//                                        )
//                                )));
//                            }
//                        }
//                    }
//
//                    case "RemoveBranch" -> {
//                        // Validación: La sucursal debe existir y no tener productos asociados
//                        Map<String, Map<String, Serializable>> branches = state.getOrElse("branches", io.vavr.collection.HashMap.empty());
//                        if (!branches.containsKey(command.get("branchId").toString())) {
//                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
//                        } else {
//                            Map<String, Serializable> products = branches.get(command.get("branchId").toString()).getOrElse(io.vavr.collection.HashMap.empty());
//                            if (!products.isEmpty()) {
//                                yield Mono.error(new IllegalStateException("La sucursal tiene productos asociados y no puede ser eliminada."));
//                            } else {
//                                yield Mono.just(List.of(io.vavr.collection.HashMap.of(
//                                        "eventType", "BranchRemoved",
//                                        "aggregateId", command.get("franchiseId"),
//                                        "payload", io.vavr.collection.HashMap.of(
//                                                "branchId", command.get("branchId")
//                                        )
//                                )));
//                            }
//                        }
//                    }
//
//                    case "RemoveFranchise" -> {
//                        // Validación: La franquicia debe existir y no tener sucursales activas
//                        if (!state.getOrElse("franchiseExists", false)) {
//                            yield Mono.error(new IllegalStateException("La franquicia no existe."));
//                        } else {
//                            Map<String, Map<String, Serializable>> branches = state.getOrElse("branches", io.vavr.collection.HashMap.empty());
//                            if (!branches.isEmpty()) {
//                                yield Mono.error(new IllegalStateException("La franquicia tiene sucursales activas y no puede ser eliminada."));
//                            } else {
//                                yield Mono.just(List.of(io.vavr.collection.HashMap.of(
//                                        "eventType", "FranchiseRemoved",
//                                        "aggregateId", command.get("franchiseId"),
//                                        "payload", io.vavr.collection.HashMap.empty()
//                                )));
//                            }
//                        }
//                    }
//
//                    case "AdjustProductStock" -> {
//                        // Validación: La sucursal y el producto deben existir
//                        Map<String, Map<String, Serializable>> branches = state.getOrElse("branches", io.vavr.collection.HashMap.empty());
//                        if (!branches.containsKey(command.get("branchId").toString())) {
//                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
//                        } else {
//                            Map<String, Serializable> products = branches.get(command.get("branchId").toString()).getOrElse(io.vavr.collection.HashMap.empty());
//                            if (!products.containsKey(command.get("productId").toString())) {
//                                yield Mono.error(new IllegalStateException("El producto no existe en la sucursal."));
//                            } else {
//                                int newStock = Integer.parseInt(command.get("newStock").toString());
//                                // Validación: El nuevo stock no puede ser negativo
//                                if (newStock < 0) {
//                                    yield Mono.error(new IllegalStateException("El nuevo stock no puede ser negativo."));
//                                } else {
//                                    yield Mono.just(List.of(io.vavr.collection.HashMap.of(
//                                            "eventType", "ProductStockAdjusted",
//                                            "aggregateId", command.get("franchiseId"),
//                                            "payload", io.vavr.collection.HashMap.of(
//                                                    "branchId", command.get("branchId"),
//                                                    "productId", command.get("productId"),
//                                                    "newStock", newStock
//                                            )
//                                    )));
//                                }
//                            }
//                        }
//                    }
//
//                    case "NotifyStockDepleted" -> {
//                        // Validación: El producto debe existir y su stock debe ser cero
//                        Map<String, Map<String, Serializable>> branches = state.getOrElse("branches", io.vavr.collection.HashMap.empty());
//                        if (!branches.containsKey(command.get("branchId").toString())) {
//                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
//                        } else {
//                            Map<String, Serializable> products = branches.get(command.get("branchId").toString()).getOrElse(io.vavr.collection.HashMap.empty());
//                            if (!products.containsKey(command.get("productId").toString())) {
//                                yield Mono.error(new IllegalStateException("El producto no existe en la sucursal."));
//                            } else {
//                                int currentStock = Integer.parseInt(products.get(command.get("productId").toString()).toString());
//                                if (currentStock > 0) {
//                                    yield Mono.error(new IllegalStateException("El stock del producto aún no está agotado."));
//                                } else {
//                                    yield Mono.just(List.of(io.vavr.collection.HashMap.of(
//                                            "eventType", "NotifyStockDepleted",
//                                            "aggregateId", command.get("franchiseId"),
//                                            "payload", io.vavr.collection.HashMap.of(
//                                                    "branchId", command.get("branchId"),
//                                                    "productId", command.get("productId")
//                                            )
//                                    )));
//                                }
//                            }
//                        }
//                    }
//
//                    default -> Mono.error(new IllegalArgumentException("Comando no reconocido."));
//                });
//    }

    // Función para notificar eventos a sistemas externos
    private Mono<Map<String, Serializable>> notifyEvents(Map<String, Serializable> events) {
        // Notificación de eventos
        return Mono.just(events);  // Simulación de notificación
    }
}

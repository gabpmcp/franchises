package com.nequi.franchises;

import com.nequi.franchises.comands.Command;
import com.nequi.franchises.comands.ValidationResult;
import com.nequi.franchises.comands.Validator;
import com.nequi.franchises.util.Step;
import com.nequi.franchises.util.Utils;
import io.vavr.Function2;
import io.vavr.collection.HashMap;
import io.vavr.collection.List;

import io.vavr.collection.Map;
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
        List.of(HashMap.of("aggregateId", UUID.randomUUID(), "type", "FranchiseCreated", "payload", HashMap.of("franchiseId", franchiseId, "franchiseName", franchiseName, "version", 1)));

    @PostMapping("/command")
    public Mono<ResponseEntity<Map<String, Serializable>>> handleCommand(@RequestBody Map<String, Serializable> commandMap) {
        return createCommandHandler().apply(commandMap) // Directamente invoca createCommandHandler
                .map(ResponseEntity::ok)
                .onErrorResume(e -> Mono.just(ResponseEntity.badRequest().body(HashMap.of("error", e.getMessage()))));
    }

    // Función para crear el handler reactivo que maneja los comandos
    private Function<Map<String, Serializable>, Mono<Map<String, Serializable>>> createCommandHandler() {
        return command -> Mono.just(command)
            .flatMap(this::validateCommand)    // Validación del comando
            .flatMap(depsLoader.get("checkIdempotency").get())
            // Carga de eventos del event store
            .flatMap(depsLoader.get("fetchEvents").get())
            // Proyección del estado a partir de los eventos
            .flatMap(result -> {
                Map<String, Serializable> initialState = HashMap.of("command", getValue(result, "command", HashMap.empty()));
                List<Map<String, Serializable>> events = getValue(result, "events", List.empty());
                return projectState.apply(initialState, events);
            })
            // Toma de decisiones de negocio
            .flatMap(state -> {
                Map<String, Serializable> cmd = getValue(state, "command", HashMap.empty());
                Map<String, Serializable> currentState = state.filter((key, value) -> !key.equals("command"));
                return decide(cmd, currentState).map(events -> HashMap.<String, Serializable>of("command", cmd, "events", events));
            })
            .flatMap(result -> depsLoader.get("saveEvents").get().apply(result));     // Persistencia de los eventos generados
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
                case "AddProductToBranch" -> {
                    var nameValidators = HashMap.ofAll(getValue(command, "products", java.util.Map.of())).toList()
                        .map(entry -> required("products.%s.productName".formatted(entry._1())));
                    var stockValidators = HashMap.ofAll(getValue(command, "products", java.util.Map.of())).toList()
                        .map(entry -> required("products.%s.initialStock".formatted(entry._1())));
                    List<Validator> validators = List.of(required("franchiseId"), isNonEmptyString("franchiseId"),
                        matchesPattern("franchiseId", "[A-Z]*\\d+"),
                        required("branchId"), isNonEmptyString("branchId"), matchesPattern("branchId", "[A-Z]*\\d+"))
                    .appendAll(nameValidators)
                    .appendAll(stockValidators);
                    yield new Command("AddProductToBranch", command).validate(validators.toJavaArray(Validator[]::new));
                }
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

    public static Function2<Map<String, Serializable>, List<Map<String, Serializable>>, Mono<Map<String, Serializable>>> projectState = (initialState, events) ->
        Mono.defer(() -> Mono.just(events.foldLeft(initialState, (state, event) ->
            switch (getValue(event, "type", "")) {
                case "FranchiseCreated" -> state
                        .put("aggregateId", getValue(event, "aggregateId", UUID.randomUUID().toString()))
                        .put("franchiseExists", true)
                        .put("franchiseId", getValue(event, "payload.franchiseId", ""))
                        .put("franchiseName", getValue(event, "payload.franchiseName", ""))
                        .put("branches", HashMap.empty());

                case "FranchiseNameUpdated" -> state.put("franchiseName", getValue(getValue(event, "payload", HashMap.empty()), "newFranchiseName", ""));

                case "BranchAdded" -> state.put("branches", getValue(state, "branches", HashMap.<String, Serializable>empty()).merge(getValue(event, "payload", HashMap.empty())));

                case "BranchNameUpdated" -> state.put("branches", getBranches(state).put(
                        getValue(getValue(event, "payload", HashMap.empty()), "branchId", ""),
                        getValue(getBranches(state), getValue(getValue(event, "payload", HashMap.empty()), "branchId", ""), HashMap.<String, Serializable>empty())
                                .put("branchName", getValue(getValue(event, "payload", HashMap.empty()), "newBranchName", ""))));

                case "ProductAddedToBranch" -> {
                    Map<String, Serializable> result = getValue(state, "branches.products", HashMap.<String, Serializable>empty()).merge(getValue(event, "payload.products", HashMap.empty()), (stateProducts, eventProducts) -> eventProducts);
                    yield result;
                }

                case "ProductStockUpdated" -> {
                    String branchId = getValue(getValue(event, "payload", HashMap.empty()), "branchId", "");
                    String productId = getValue(getValue(event, "payload", HashMap.empty()), "productId", "");
                    int quantityChange = getValue(getValue(event, "payload", HashMap.empty()), "quantity", 0);
                    yield state.put("branches", getBranches(state).put(branchId, updateProductStock(getValue(getBranches(state), branchId, HashMap.empty()), productId, quantityChange)));
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
            })));

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
    private Mono<List<Map<String, Serializable>>> decide(Map<String, Serializable> command, Map<String, Serializable> state) {
        // Dispatcher por tipo de comando
        return Mono.justOrEmpty(getValue(command, "type", ""))
                .flatMap(commandType -> switch (commandType) {
                    case "CreateFranchise" -> {
                        // Validación: No se puede crear una franquicia si ya existe
                        if (state.containsValue(getValue(command, "franchiseId", ""))) {
                            yield Mono.error(new IllegalStateException("La franquicia ya existe."));
                        } else {
                            yield Mono.just(List.of(HashMap.of(
                                    "type", "FranchiseCreated",
                                    "aggregateId", getValue(command, "aggregateId", ""),
                                    "payload", HashMap.of(
                                            "franchiseId", getValue(command, "franchiseId", ""),
                                            "franchiseName", getValue(command, "franchiseName", "")
                                    )
                            )
                            ));
                        }
                    }

                    case "UpdateFranchiseName" -> {
                        // Validación: La franquicia del comando debe coincidir con la de la creación
                        if (!state.containsValue(getValue(command, "franchiseId", ""))) {
                            yield Mono.error(new IllegalStateException("La franquicia no corresponde al orden lógico de los eventos. Tal vez quieras ajustar la franquicia o necesites modelar una interacción nueva."));
                        }
                        // Validación: La franquicia debe existir
                        if (!getValue(state, "franchiseExists", false)) {
                            yield Mono.error(new IllegalStateException("La franquicia no existe."));
                        } else {
                            yield Mono.just(List.of(HashMap.of(
                                    "type", "FranchiseNameUpdated",
                                    "aggregateId", getValue(command, "aggregateId", ""),
                                    "payload", HashMap.of(
                                            "newFranchiseName", getValue(command, "newName", ""),
                                            "oldFranchiseName", getValue(state, "franchiseName", "")
                                    )
                            )));
                        }
                    }

                    case "AddBranch" -> {
                        // Validación: La franquicia del comando debe coincidir con la de la creación
                        if (!state.containsValue(getValue(command, "franchiseId", ""))) {
                            yield Mono.error(new IllegalStateException("La franquicia no corresponde al orden lógico de los eventos. Tal vez quieras ajustar la franquicia o necesites modelar una interacción nueva."));
                        }
                        // Validación: La franquicia debe existir
                        if (!getValue(state, "franchiseExists", false)) {
                            yield Mono.error(new IllegalStateException("La franquicia no existe."));
                        } else {
                            // Validación: No se puede agregar una sucursal que ya existe
                            Map<String, Serializable> branches = getValue(state, "branches", HashMap.empty());
                            if (branches.containsKey(getValue(command, "branchId", ""))) {
                                yield Mono.error(new IllegalStateException("La sucursal ya existe en la franquicia."));
                            } else {
                                yield Mono.just(List.of(HashMap.of(
                                        "type", "BranchAdded",
                                        "aggregateId", getValue(command, "aggregateId", ""),
                                        "payload", HashMap.of(
                                            getValue(command, "branchId", ""),
                                            getValue(command, "branchName", "")
                                        )
                                )));
                            }
                        }
                    }

                    case "UpdateBranchName" -> {
                        // Validación: La sucursal debe existir
                        Map<String, Serializable> branches = getValue(state, "branches", HashMap.empty());
                        if (!branches.containsKey(getValue(command, "branchId", ""))) {
                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
                        } else {
                            String oldBranchName = getValue(command, "branchId", "");
                            yield Mono.just(List.of(HashMap.of(
                                    "type", "BranchNameUpdated",
                                    "aggregateId", getValue(command, "aggregateId", ""),
                                    "payload", HashMap.of(
                                            "branchId", getValue(command, "branchId", ""),
                                            "newBranchName", getValue(command, "newName", ""),
                                            "oldBranchName", oldBranchName
                                    )
                            )));
                        }
                    }

                    case "AddProductToBranch" -> {
                        // Validación: La franquicia del comando debe coincidir con la de la creación
                        if (!state.containsValue(getValue(command, "franchiseId", ""))) {
                            yield Mono.error(new IllegalStateException("La franquicia no corresponde al orden lógico de los eventos. Tal vez quieras ajustar la franquicia o necesites modelar una interacción nueva."));
                        }

                        // Validación: La sucursal debe existir
                        Map<String, Serializable> branches = getValue(state, "branches", HashMap.empty());
//                        Map<String, Serializable> products = getValue(state, "products", HashMap.empty());
                        if (!branches.containsKey(getValue(command, "branchId", ""))) {
                            yield Mono.error(new IllegalStateException("La sucursal no existe o no pertenece a la franquicia."));
                        } else {
                            Map<String, Serializable> existentProducts = getValue(command, "products", HashMap.<String, Serializable>empty()).filterKeys(getValue(state, "products", HashMap.<String, Serializable>empty())::containsKey);
                            if (!existentProducts.isEmpty()) {
                                yield Mono.error(new IllegalStateException("Hay productos que ya existen en la sucursal. %s".formatted(existentProducts)));
                            } else {
                                yield Mono.just(List.of(HashMap.of(
                                        "type", "ProductAddedToBranch",
                                        "aggregateId", getValue(command, "aggregateId", ""),
                                        "payload", command.filterKeys(key -> !List.of("createAggregateFunc", "type", "aggregateId").contains(key))
                                )));
                            }
                        }
                    }

                    case "UpdateProductStock" -> {
                        // Validación: La sucursal y el producto deben existir
                        Map<String, Map<String, Serializable>> branches = getValue(state, "branches", HashMap.empty());
                        if (!branches.containsKey(getValue(command, "branchId", ""))) {
                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
                        } else {
                            Map<String, Serializable> products = getValue(branches, getValue(command, "branchId", ""), HashMap.empty());
                            if (!products.containsKey(getValue(command, "productId", ""))) {
                                yield Mono.error(new IllegalStateException("El producto no existe en la sucursal."));
                            } else {
                                int currentStock = getValue(getValue(products, getValue(command, "productId", ""), HashMap.empty()), "currentStock", 0);
                                int quantityChange = getValue(command, "quantityChange", 0);
                                int newStock = currentStock + quantityChange;

                                // Validación: El stock resultante no puede ser negativo
                                if (newStock < 0) {
                                    yield Mono.error(new IllegalStateException("El stock resultante no puede ser negativo."));
                                } else {
                                    yield Mono.just(List.of(HashMap.of(
                                            "type", "ProductStockUpdated",
                                            "aggregateId", getValue(command, "aggregateId", ""),
                                            "payload", HashMap.of(
                                                    "branchId", getValue(command, "branchId", ""),
                                                    "productId", getValue(command, "productId", ""),
                                                    "quantityChange", quantityChange
                                            )
                                    )));
                                }
                            }
                        }
                    }

                    case "TransferProductBetweenBranches" -> {
                        // Validación: Ambas sucursales deben existir
                        Map<String, Map<String, Serializable>> branches = getValue(state,"branches", HashMap.empty());
                        if (!branches.containsKey(getValue(command, "fromBranchId", "")) || !branches.containsKey(getValue(command, "toBranchId", ""))) {
                            yield Mono.error(new IllegalStateException("Una o ambas sucursales no existen."));
                        } else {
                            Map<String, Serializable> fromProducts = getValue(branches, getValue(command, "fromBranchId", ""), HashMap.empty());
                            Map<String, Serializable> toProducts = getValue(branches, getValue(command, "toBranchId", ""), HashMap.empty());

                            if (!fromProducts.containsKey(getValue(command, "productId", ""))) {
                                yield Mono.error(new IllegalStateException("El producto no existe en la sucursal de origen."));
                            } else {
                                int currentStock = getValue(getValue(fromProducts, getValue(command, "productId", ""), HashMap.empty()), "currentStock", 0);
                                int quantity = getValue(command, "quantity", 0);

                                if (quantity > currentStock) {
                                    yield Mono.error(new IllegalStateException("Stock insuficiente en la sucursal de origen."));
                                } else {
                                    yield Mono.just(List.of(HashMap.of(
                                            "type", "ProductTransferredBetweenBranches",
                                            "aggregateId", getValue(command, "aggregateId", ""),
                                            "payload", HashMap.of(
                                                    "fromBranchId", getValue(command, "fromBranchId", ""),
                                                    "toBranchId", getValue(command, "toBranchId", ""),
                                                    "productId", getValue(command, "productId", ""),
                                                    "quantity", quantity
                                            )
                                    )));
                                }
                            }
                        }
                    }

                    case "RemoveProductFromBranch" -> {
                        // Validación: La sucursal y el producto deben existir
                        Map<String, Map<String, Serializable>> branches = getValue(state, "branches", HashMap.empty());
                        if (!branches.containsKey(getValue(command, "branchId", ""))) {
                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
                        } else {
                            Map<String, Serializable> products = getValue(branches, getValue(command, "branchId", ""), HashMap.empty());
                            if (!products.containsKey(getValue(command, "productId", ""))) {
                                yield Mono.error(new IllegalStateException("El producto no existe en la sucursal."));
                            } else {
                                yield Mono.just(List.of(HashMap.of(
                                        "type", "ProductRemovedFromBranch",
                                        "aggregateId", getValue(command, "aggregateId", ""),
                                        "payload", HashMap.of(
                                                "branchId", getValue(command, "branchId", ""),
                                                "productId", getValue(command, "productId", "")
                                        )
                                )));
                            }
                        }
                    }

                    case "RemoveBranch" -> {
                        // Validación: La sucursal debe existir y no tener productos asociados
                        Map<String, Map<String, Serializable>> branches = getValue(state, "branches", HashMap.empty());
                        if (!branches.containsKey(getValue(command, "branchId", ""))) {
                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
                        } else {
                            Map<String, Serializable> products = getValue(branches, getValue(command, "branchId", ""), HashMap.empty());
                            if (!products.isEmpty()) {
                                yield Mono.error(new IllegalStateException("La sucursal tiene productos asociados y no puede ser eliminada."));
                            } else {
                                yield Mono.just(List.of(HashMap.of(
                                        "type", "BranchRemoved",
                                        "aggregateId", getValue(command, "aggregateId", ""),
                                        "payload", HashMap.of(
                                                "branchId", getValue(command, "branchId", "")
                                        )
                                )));
                            }
                        }
                    }

                    case "RemoveFranchise" -> {
                        // Validación: La franquicia debe existir y no tener sucursales activas
                        if (!getValue(state, "franchiseExists", false)) {
                            yield Mono.error(new IllegalStateException("La franquicia no existe."));
                        } else {
                            Map<String, Map<String, Serializable>> branches = getValue(state, "branches", HashMap.empty());
                            if (!branches.isEmpty()) {
                                yield Mono.error(new IllegalStateException("La franquicia tiene sucursales activas y no puede ser eliminada."));
                            } else {
                                yield Mono.just(List.of(HashMap.of(
                                        "type", "FranchiseRemoved",
                                        "aggregateId", getValue(command, "aggregateId", ""),
                                        "payload", HashMap.empty()
                                )));
                            }
                        }
                    }

                    case "AdjustProductStock" -> {
                        // Validación: La sucursal y el producto deben existir
                        Map<String, Map<String, Serializable>> branches = getValue(state, "branches", HashMap.empty());
                        if (!branches.containsKey(getValue(command, "branchId", ""))) {
                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
                        } else {
                            Map<String, Serializable> products = getValue(branches, getValue(command, "branchId", ""), HashMap.empty());
                            if (!products.containsKey(getValue(command, "productId", ""))) {
                                yield Mono.error(new IllegalStateException("El producto no existe en la sucursal."));
                            } else {
                                int newStock = getValue(command, "newStock", 0);
                                // Validación: El nuevo stock no puede ser negativo
                                if (newStock < 0) {
                                    yield Mono.error(new IllegalStateException("El nuevo stock no puede ser negativo."));
                                } else {
                                    yield Mono.just(List.of(HashMap.of(
                                            "type", "ProductStockAdjusted",
                                            "aggregateId", getValue(command, "aggregateId", ""),
                                            "payload", HashMap.of(
                                                    "branchId", getValue(command, "branchId", ""),
                                                    "productId", getValue(command, "productId", ""),
                                                    "newStock", newStock
                                            )
                                    )));
                                }
                            }
                        }
                    }

                    case "NotifyStockDepleted" -> {
                        // Validación: El producto debe existir y su stock debe ser cero
                        Map<String, Map<String, Serializable>> branches = getValue(state, "branches", HashMap.empty());
                        if (!branches.containsKey(getValue(command, "branchId", ""))) {
                            yield Mono.error(new IllegalStateException("La sucursal no existe."));
                        } else {
                            Map<String, Serializable> products = getValue(branches, getValue(command, "branchId", ""), HashMap.empty());
                            if (!products.containsKey(getValue(command, "productId", ""))) {
                                yield Mono.error(new IllegalStateException("El producto no existe en la sucursal."));
                            } else {
                                int currentStock = getValue(getValue(products, getValue(command, "productId", ""), HashMap.empty()), "currentStock", 0);
                                if (currentStock > 0) {
                                    yield Mono.error(new IllegalStateException("El stock del producto aún no está agotado."));
                                } else {
                                    yield Mono.just(List.of(HashMap.of(
                                            "type", "NotifyStockDepleted",
                                            "aggregateId", getValue(command, "aggregateId", ""),
                                            "payload", HashMap.of(
                                                    "branchId", getValue(command, "branchId", ""),
                                                    "productId", getValue(command, "productId", "")
                                            )
                                    )));
                                }
                            }
                        }
                    }

                    default -> Mono.error(new IllegalArgumentException("Comando no reconocido."));
                });
    }

    // Función para notificar eventos a sistemas externos
    private Mono<Map<String, Serializable>> notifyEvents(Map<String, Serializable> events) {
        // Notificación de eventos
        return Mono.just(events);  // Simulación de notificación
    }
}

package com.nequi.franchises.events;

import io.vavr.collection.HashMap;
import io.vavr.collection.List;

public class EventFactory {

    // Funciones para crear eventos
    public static Event franchiseCreatedEvent(String franchiseId, String franchiseName) {
        return new Event("FranchiseCreated", HashMap.of(
                "franchiseId", franchiseId,
                "franchiseName", franchiseName
        ));
    }

    public static Event franchiseNameUpdatedEvent(String franchiseId, String oldName, String newName) {
        return new Event("FranchiseNameUpdated", HashMap.of(
                "franchiseId", franchiseId,
                "oldName", oldName,
                "newName", newName
        ));
    }

    public static Event branchAddedEvent(String franchiseId, String branchId, String branchName) {
        return new Event("BranchAdded", HashMap.of(
                "franchiseId", franchiseId,
                "branchId", branchId,
                "branchName", branchName
        ));
    }

    public static Event branchNameUpdatedEvent(String branchId, String oldName, String newName) {
        return new Event("BranchNameUpdated", HashMap.of(
                "branchId", branchId,
                "oldName", oldName,
                "newName", newName
        ));
    }

    public static Event productAddedToBranchEvent(String branchId, String productId, String productName, int stock) {
        return new Event("ProductAddedToBranch", HashMap.of(
                "branchId", branchId,
                "productId", productId,
                "productName", productName,
                "stock", stock
        ));
    }

    public static Event productStockUpdatedEvent(String productId, int oldStock, int newStock) {
        return new Event("ProductStockUpdated", HashMap.of(
                "productId", productId,
                "oldStock", oldStock,
                "newStock", newStock
        ));
    }

    public static Event productRemovedFromBranchEvent(String branchId, String productId) {
        return new Event("ProductRemovedFromBranch", HashMap.of(
                "branchId", branchId,
                "productId", productId
        ));
    }

    public static Event branchRemovedEvent(String franchiseId, String branchId) {
        return new Event("BranchRemoved", HashMap.of(
                "franchiseId", franchiseId,
                "branchId", branchId
        ));
    }

    public static Event franchiseRemovedEvent(String franchiseId) {
        return new Event("FranchiseRemoved", HashMap.of(
                "franchiseId", franchiseId
        ));
    }

    public static Event stockDepletedNotificationSentEvent(String productId, String notificationId) {
        return new Event("StockDepletedNotificationSent", HashMap.of(
                "productId", productId,
                "notificationId", notificationId
        ));
    }

    public static Event productTransferredBetweenBranchesEvent(String fromBranchId, String toBranchId, String productId, int quantity) {
        return new Event("ProductTransferredBetweenBranches", HashMap.of(
                "fromBranchId", fromBranchId,
                "toBranchId", toBranchId,
                "productId", productId,
                "quantity", quantity
        ));
    }

    public static Event productStockAdjustedEvent(String productId, int oldStock, int newStock) {
        return new Event("ProductStockAdjusted", HashMap.of(
                "productId", productId,
                "oldStock", oldStock,
                "newStock", newStock
        ));
    }
}

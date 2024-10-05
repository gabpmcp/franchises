package com.nequi.franchises.comands;

import io.vavr.collection.HashMap;

public class CommandFactory {
    // Funciones para crear comandos
    public static Command createFranchiseCommand(String franchiseName) {
        return new Command("CreateFranchise", HashMap.of("franchiseName", franchiseName));
    }

    public static Command updateFranchiseNameCommand(String franchiseId, String newName) {
        return new Command("UpdateFranchiseName", HashMap.of(
                "franchiseId", franchiseId,
                "newName", newName
        )) ;
    }

    public static Command addBranchCommand(String franchiseId, String branchName) {
        return new Command("AddBranch", HashMap.of(
                "franchiseId", franchiseId,
                "branchName", branchName
        ));
    }

    public static Command updateBranchNameCommand(String branchId, String newName) {
        return new Command("UpdateBranchName", HashMap.of(
                "branchId", branchId,
                "newName", newName
        ));
    }

    public static Command addProductToBranchCommand(String branchId, String productName, int initialStock) {
        return new Command("AddProductToBranch", HashMap.of(
                "branchId", branchId,
                "productName", productName,
                "initialStock", initialStock
        ));
    }

    public static Command updateProductStockCommand(String productId, int quantityChange) {
        return new Command("UpdateProductStock", HashMap.of(
                "productId", productId,
                "quantityChange", quantityChange
        ));
    }

    public static Command removeProductFromBranchCommand(String branchId, String productId) {
        return new Command("RemoveProductFromBranch", HashMap.of(
                "branchId", branchId,
                "productId", productId
        ));
    }

    public static Command removeBranchCommand(String franchiseId, String branchId) {
        return new Command("RemoveBranch", HashMap.of(
                "franchiseId", franchiseId,
                "branchId", branchId
        ));
    }

    public static Command removeFranchiseCommand(String franchiseId) {
        return new Command("RemoveFranchise", HashMap.of(
                "franchiseId", franchiseId
        ));
    }

    public static Command notifyStockDepletedCommand(String productId) {
        return new Command("NotifyStockDepleted", HashMap.of(
                "productId", productId
        ));
    }

    public static Command transferProductBetweenBranchesCommand(String fromBranchId, String toBranchId, String productId, int quantity) {
        return new Command("TransferProductBetweenBranches", HashMap.of(
                "fromBranchId", fromBranchId,
                "toBranchId", toBranchId,
                "productId", productId,
                "quantity", quantity
        ));
    }

    public static Command adjustProductStockCommand(String productId, int newStock) {
        return new Command("AdjustProductStock", HashMap.of(
                "productId", productId,
                "newStock", newStock
        ));
    }
}

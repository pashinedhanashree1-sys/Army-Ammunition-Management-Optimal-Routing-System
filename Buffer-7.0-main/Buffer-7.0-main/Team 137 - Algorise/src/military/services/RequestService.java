package military.services;

// ============================================================
// RequestService.java
// Handles all supply request operations:
//   - requestItem()          → raise a new manual supply request
//   - viewPendingRequests()  → show own requests + approvable ones
//   - viewMyRequestsOnly()   → Battalion-only narrow view
//   - findBestSupplier()     → Dijkstra-based closest eligible supplier
//   - getEligibleSuppliers() → warehouses at a higher level than requester
//   - autoDeleteFulfilled()  → clean up request + delivery on completion
//   - viewReplenishmentQueue() / viewMyReplenishmentQueue()
// ============================================================

import military.data.DataStore;
import military.models.*;

import java.util.ArrayList;
import java.util.Scanner;

public class RequestService {

    // ── Raise a new supply request for the logged-in warehouse ────────
    public static void requestItem(Scanner sc) {
        System.out.print("Enter item name required: ");
        String itemName = sc.nextLine().trim().toUpperCase();

        if (UtilityService.getCategory(itemName).equals("UNKNOWN")) {
            System.out.println("Item not included in inventory. Please enter a valid item name.");
            return;
        }

        System.out.print("Enter quantity required: ");
        int quantity;
        try {
            quantity = sc.nextInt();
            sc.nextLine();
        } catch (Exception e) {
            System.out.println("Invalid quantity.");
            sc.nextLine();
            return;
        }

        if (quantity <= 0) {
            System.out.println("Quantity must be positive.");
            return;
        }

        SupplyRequest newRequest = new SupplyRequest(
                DataStore.requestCounter++,
                DataStore.loggedInWarehouse,
                itemName,
                quantity,
                "PENDING"
        );
        DataStore.pendingRequests.add(newRequest);

        System.out.println("\nRequest created successfully!");
        System.out.println("Request ID: " + newRequest.requestId);

        // Show which supplier would fulfil this (informational only)
        SupplierResult best = findBestSupplier(
                DataStore.loggedInWarehouse, itemName, quantity);

        if (best != null) {
            System.out.println("This request will be fulfilled by: "
                    + best.supplierWarehouse
                    + " (Level: " + DataStore.warehouseLevels.get(best.supplierWarehouse) + ")");
            System.out.println("Distance: " + best.distance + " km | Path: " + best.path);
        } else {
            System.out.println("No supplier is currently available for this item/quantity.");
            System.out.println("Request is queued and will be processed when stock is available.");
        }
    }

    // ── Show own requests AND requests this warehouse can approve ──────
    public static void viewPendingRequests() {
        System.out.println("\n==== My Requests ====");
        boolean ownAny = false;

        for (SupplyRequest req : DataStore.pendingRequests) {
            if (req.requester.equals(DataStore.loggedInWarehouse)) {
                ownAny = true;
                String tag = req.isReplenishment ? " (Generated from Replenishment Queue)" : "";
                System.out.println(
                        "Request ID: " + req.requestId +
                        " | Item: "    + req.itemName  +
                        " | Qty: "     + req.quantity  +
                        " | Status: "  + req.status    + tag);
            }
        }
        if (!ownAny) System.out.println("No requests from this warehouse.");

        System.out.println("\n==== Requests You Can Approve ====");
        boolean canApproveAny = false;

        for (SupplyRequest req : DataStore.pendingRequests) {
            if (!"PENDING".equals(req.status)) continue;
            if (req.requester.equals(DataStore.loggedInWarehouse)) continue;

            SupplierResult best = findBestSupplier(req.requester, req.itemName, req.quantity);
            if (best != null && DataStore.loggedInWarehouse.equals(best.supplierWarehouse)) {
                canApproveAny = true;
                String tag = req.isReplenishment ? " (Generated from Replenishment Queue)" : "";
                System.out.println(
                        "Request ID: " + req.requestId +
                        " | From: "    + req.requester +
                        " | Item: "    + req.itemName  +
                        " | Qty: "     + req.quantity  + tag);
            }
        }
        if (!canApproveAny) System.out.println("No requests available for you to approve.");
    }

    // ── Battalion-only: show only this warehouse's own requests ───────
    public static void viewMyRequestsOnly() {
        boolean found = false;
        System.out.println("\n==== My Requests ====");

        for (SupplyRequest req : DataStore.pendingRequests) {
            if (req.requester.equals(DataStore.loggedInWarehouse)) {
                found = true;
                String type = req.isReplenishment ? "[REPLENISHMENT] " : "";
                System.out.println(type +
                        "Request ID: " + req.requestId +
                        " | Item: "    + req.itemName  +
                        " | Qty: "     + req.quantity  +
                        " | Status: "  + req.status);
            }
        }
        if (!found) System.out.println("No requests found.");
    }

    // ── Battalion-only: show only this warehouse's replenishment reqs ──
    public static void viewMyReplenishmentQueue() {
        System.out.println("\n==== My Replenishment Requests ====");
        boolean found = false;

        for (SupplyRequest req : DataStore.pendingRequests) {
            if (req.isReplenishment && req.requester.equals(DataStore.loggedInWarehouse)) {
                found = true;
                System.out.println(
                        "Request ID: " + req.requestId +
                        " | Item: "    + req.itemName  +
                        " | Qty: "     + req.quantity  +
                        " | Status: "  + req.status    +
                        " | Generated Automatically");
            }
        }
        if (!found) System.out.println("No replenishment requests found for this warehouse.");
    }

    // ── View global replenishment queue (Brigade/Corps/Command/AOC) ───
    public static void viewReplenishmentQueue() {
        if (DataStore.replenishmentQueue.isEmpty()) {
            System.out.println("Replenishment queue is empty.");
            return;
        }

        System.out.println("\n==== Replenishment Queue ====");
        for (ReplenishmentRequest r : DataStore.replenishmentQueue) {
            System.out.println("[REPLENISHMENT REQUEST] Warehouse: " + r.warehouseName +
                    " | Item: "       + r.itemName       +
                    " | Qty Needed: " + r.quantityNeeded);
        }
    }

    // ── Find the nearest eligible supplier that has enough stock ──────
    // Returns null if no supplier can fulfil the quantity right now.
    public static SupplierResult findBestSupplier(String requester,
                                                   String itemName,
                                                   int quantity) {
        ArrayList<String> candidates = getEligibleSuppliers(requester);
        SupplierResult    best       = null;

        for (String candidate : candidates) {
            Warehouse warehouse = DataStore.warehouses.get(candidate);
            if (warehouse == null || warehouse.inventory == null) continue;
            if (!warehouse.inventory.containsKey(itemName)) continue;

            ItemStock item = warehouse.inventory.get(itemName);
            if (item == null) continue;

            // Rule 12: use getTotalStock() across all FEFO batches
            if (item.getTotalStock() >= quantity) {
                DijkstraResult route = GraphService.dijkstra(candidate, requester);
                if (route != null) {
                    if (best == null || route.distance < best.distance) {
                        best = new SupplierResult(candidate, route.distance, route.path);
                    }
                }
            }
        }

        return best;
    }

    // ── Get all warehouses at a higher level than the requester ───────
    public static ArrayList<String> getEligibleSuppliers(String requester) {
        ArrayList<String> eligible       = new ArrayList<>();
        String            requesterLevel = DataStore.warehouseLevels.get(requester);
        if (requesterLevel == null) return eligible;

        int requesterPriority = UtilityService.getLevelPriority(requesterLevel);

        for (String warehouseName : DataStore.warehouses.keySet()) {
            String supplierLevel = DataStore.warehouseLevels.get(warehouseName);
            if (supplierLevel == null) continue;
            if (UtilityService.getLevelPriority(supplierLevel) > requesterPriority) {
                eligible.add(warehouseName);
            }
        }

        return eligible;
    }

    // ── Auto-delete: remove a fulfilled request AND its delivery ──────
    // Rule 3: called immediately after delivery completion.
    public static void autoDeleteFulfilledRequest(int requestId) {
        DataStore.pendingRequests.removeIf(req -> req.requestId == requestId);
        DataStore.pendingDeliveries.removeIf(d   -> d.requestId  == requestId);
    }
}

package military.services;

// ============================================================
// DeliveryService.java
// Handles the full delivery lifecycle and consumption:
//   - approveRequest()            → approve a pending request,
//                                   deduct stock FEFO from supplier,
//                                   create DeliveryOrder with transit batches
//   - viewPendingDeliveries()     → list all active deliveries
//   - completeDelivery()          → receiver credits stock (FEFO batches),
//                                   auto-deletes request + delivery (Rule 3)
//   - consumeItem()               → FEFO consumption from own stock,
//                                   threshold warning + optional replenishment
//   - checkAndQueueReplenishment()→ internal trigger post stock change
// ============================================================

import military.data.DataStore;
import military.models.*;

import java.util.*;

public class DeliveryService {

    // ── Approve a pending supply request ──────────────────────────────
    // Rule 9: stock is FEFO-deducted from supplier at approval, NOT at delivery.
    // Transit batches are stored on the DeliveryOrder for FEFO-safe receiver credit.
    public static void approveRequest(Scanner sc) {

        // Check if there's anything pending at all
        boolean anyPending = false;
        for (SupplyRequest req : DataStore.pendingRequests) {
            if ("PENDING".equals(req.status) &&
                !req.requester.equals(DataStore.loggedInWarehouse)) {
                anyPending = true;
                break;
            }
        }
        if (!anyPending) {
            System.out.println("No pending requests available.");
            return;
        }

        // Check if this warehouse can fulfil any of them
        boolean canFulfil = false;
        for (SupplyRequest req : DataStore.pendingRequests) {
            if (!"PENDING".equals(req.status)) continue;
            if (req.requester.equals(DataStore.loggedInWarehouse)) continue;

            SupplierResult best = RequestService.findBestSupplier(
                    req.requester, req.itemName, req.quantity);
            if (best != null && DataStore.loggedInWarehouse.equals(best.supplierWarehouse)) {
                canFulfil = true;
                break;
            }
        }
        if (!canFulfil) {
            System.out.println("Your warehouse has no requests it can currently fulfill.");
            return;
        }

        RequestService.viewPendingRequests();

        System.out.print("Enter Request ID to approve: ");
        int requestId;
        try {
            requestId = sc.nextInt();
            sc.nextLine();
        } catch (Exception e) {
            System.out.println("Invalid input.");
            sc.nextLine();
            return;
        }

        // Locate the matching pending request
        SupplyRequest selected = null;
        for (SupplyRequest req : DataStore.pendingRequests) {
            if (req.requestId == requestId && "PENDING".equals(req.status)) {
                selected = req;
                break;
            }
        }

        if (selected == null) {
            System.out.println("Request not found or already processed.");
            return;
        }
        if (selected.requester.equals(DataStore.loggedInWarehouse)) {
            System.out.println("You cannot approve your own request.");
            return;
        }

        SupplierResult best = RequestService.findBestSupplier(
                selected.requester, selected.itemName, selected.quantity);

        if (best == null || !DataStore.loggedInWarehouse.equals(best.supplierWarehouse)) {
            System.out.println("You are not authorized to approve this request.");
            return;
        }

        System.out.print("Approve? (yes/no): ");
        String confirmation = sc.nextLine();
        if (!confirmation.equalsIgnoreCase("yes")) {
            System.out.println("Approval cancelled.");
            return;
        }

        // ── FEFO Batch deduction from supplier at approval time ────────
        Warehouse supplierWarehouse = DataStore.warehouses.get(best.supplierWarehouse);
        ItemStock supplierItem      = supplierWarehouse.inventory.get(selected.itemName);

        if (supplierItem == null || supplierItem.getTotalStock() < selected.quantity) {
            System.out.println("Insufficient stock at supplier. Cannot approve.");
            return;
        }

        // Poll batches from FEFO queue, splitting the last batch if needed
        List<Batch> transitBatches = new ArrayList<>();
        int remaining = selected.quantity;

        while (remaining > 0 && !supplierItem.batches.isEmpty()) {
            Batch  batch   = supplierItem.batches.poll();
            int    take    = Math.min(batch.quantity, remaining);
            transitBatches.add(new Batch(take, batch.expiryDate)); // preserve expiry

            if (batch.quantity > take) {
                batch.quantity -= take;
                supplierItem.batches.add(batch); // return partially used batch
            }
            remaining -= take;
        }

        System.out.println("Supplier stock deducted (FEFO). "
                + best.supplierWarehouse + " remaining stock of "
                + selected.itemName + ": " + supplierItem.getTotalStock());

        // ── Create the delivery order carrying the transit batches ─────
        DeliveryOrder delivery = new DeliveryOrder(
                DataStore.deliveryCounter++,
                selected.requestId,
                best.supplierWarehouse,
                selected.requester,
                selected.itemName,
                selected.quantity,
                best.distance,
                best.path,
                "PENDING_DELIVERY"
        );
        delivery.transitBatches = transitBatches;

        DataStore.pendingDeliveries.add(delivery);
        selected.status = "APPROVED_FOR_DELIVERY";

        System.out.println("Delivery created. ID: " + delivery.deliveryId);
        System.out.println("Delivery path: " + best.path);

        // Check if supplier now needs replenishment after the deduction
        checkAndQueueReplenishment(best.supplierWarehouse, selected.itemName);
    }

    // ── Show all active delivery orders ───────────────────────────────
    public static void viewPendingDeliveries() {
        if (DataStore.pendingDeliveries == null || DataStore.pendingDeliveries.isEmpty()) {
            System.out.println("No pending deliveries.");
            return;
        }

        System.out.println("\n==== Deliveries ====");
        for (DeliveryOrder delivery : DataStore.pendingDeliveries) {
            if (delivery == null) continue;
            System.out.println(
                    "Delivery ID: " + delivery.deliveryId +
                    " | Supplier: " + delivery.supplier   +
                    " | Receiver: " + delivery.receiver   +
                    " | Item: "     + delivery.itemName   +
                    " | Qty: "      + delivery.quantity   +
                    " | Status: "   + delivery.status);
        }
    }

    // ── Complete a delivery and credit the receiver's inventory ────────
    // Rule 2: AOC cannot call this (removed from AOC menu).
    // Rule 3: auto-delete request + delivery after completion.
    // Rule 9: receiver is credited with the exact FEFO transit batches.
    public static void completeDelivery(Scanner sc) {
        if (DataStore.pendingDeliveries == null || DataStore.pendingDeliveries.isEmpty()) {
            System.out.println("No pending deliveries.");
            return;
        }

        viewPendingDeliveries();

        System.out.print("Enter Delivery ID: ");
        int deliveryId;
        try {
            deliveryId = sc.nextInt();
            sc.nextLine();
        } catch (Exception e) {
            System.out.println("Invalid input.");
            sc.nextLine();
            return;
        }

        DeliveryOrder selected = null;
        for (DeliveryOrder delivery : DataStore.pendingDeliveries) {
            if (delivery != null && delivery.deliveryId == deliveryId &&
                "PENDING_DELIVERY".equals(delivery.status)) {
                selected = delivery;
                break;
            }
        }

        if (selected == null) {
            System.out.println("Delivery not found or already completed.");
            return;
        }

        // Only the designated receiver can mark as complete
        if (!DataStore.loggedInWarehouse.equals(selected.receiver)) {
            System.out.println("Only the receiving warehouse (" + selected.receiver
                    + ") can complete this delivery.");
            return;
        }

        Warehouse receiverWarehouse = DataStore.warehouses.get(selected.receiver);
        if (receiverWarehouse == null) {
            System.out.println("Receiver warehouse data error.");
            return;
        }

        // Create item slot in receiver's inventory if it doesn't exist yet
        if (!receiverWarehouse.inventory.containsKey(selected.itemName)) {
            String expiry = UtilityService.getExpiryDateByItem(selected.itemName);
            receiverWarehouse.inventory.put(selected.itemName,
                new ItemStock(selected.itemName, 0, 15, 40,
                    UtilityService.generateRFID(selected.receiver, selected.itemName),
                    expiry,
                    UtilityService.getCategory(selected.itemName)));
        }

        ItemStock receiverItem = receiverWarehouse.inventory.get(selected.itemName);

        // ── Credit transit batches preserving their FEFO expiry dates ─
        if (selected.transitBatches != null && !selected.transitBatches.isEmpty()) {
            for (Batch batch : selected.transitBatches) {
                // Rule 7: each batch added separately — NEVER merged
                receiverItem.batches.add(new Batch(batch.quantity, batch.expiryDate));
            }
        } else {
            // Fallback: legacy deliveries without transit batch data
            receiverItem.batches.add(
                new Batch(selected.quantity,
                          UtilityService.getExpiryDateByItem(selected.itemName)));
        }

        System.out.println("Delivery completed successfully.");
        System.out.println("Receiver (" + selected.receiver + ") new stock of "
                + selected.itemName + ": " + receiverItem.getTotalStock());

        // Rule 3: auto-delete the fulfilled request and delivery
        int fulfilledRequestId = selected.requestId;
        RequestService.autoDeleteFulfilledRequest(fulfilledRequestId);
        System.out.println("Request ID " + fulfilledRequestId
                + " and its delivery have been removed from the system.");

        // Check if receiver now needs replenishment after the receipt
        checkAndQueueReplenishment(selected.receiver, selected.itemName);
    }

    // ── Consume item from own stock using FEFO order ──────────────────
    // Rule 8: earliest-expiry batch is consumed first.
    // Rule 1: AOC only gets a warning; non-AOC can trigger replenishment.
    public static void consumeItem(Scanner sc) {
        if (DataStore.loggedInWarehouse == null || DataStore.loggedInWarehouse.isEmpty()) {
            System.out.println("No warehouse logged in.");
            return;
        }

        Warehouse warehouse = DataStore.warehouses.get(DataStore.loggedInWarehouse);
        if (warehouse == null) {
            System.out.println("Warehouse not found.");
            return;
        }

        System.out.print("Enter item name to consume: ");
        String itemName = sc.nextLine().trim().toUpperCase();

        if (UtilityService.getCategory(itemName).equals("UNKNOWN")) {
            System.out.println("Item not included in inventory. Please enter a valid item name.");
            return;
        }

        if (!warehouse.inventory.containsKey(itemName)) {
            System.out.println("Item not found in inventory.");
            return;
        }

        System.out.print("Enter quantity to consume: ");
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

        ItemStock item = warehouse.inventory.get(itemName);

        // Rule 13: safety check for null / corrupt data
        if (item == null || item.batches == null) {
            System.out.println("Item data error.");
            return;
        }

        if (item.getTotalStock() < quantity) {
            System.out.println("Not enough stock available. Current stock: "
                    + item.getTotalStock());
            return;
        }

        // ── FEFO Consumption: poll earliest-expiry batch first ─────────
        int demand = quantity;
        while (demand > 0 && !item.batches.isEmpty()) {
            Batch batch   = item.batches.poll();
            int   consume = Math.min(batch.quantity, demand);
            batch.quantity -= consume;
            demand         -= consume;
            if (batch.quantity > 0) {
                item.batches.add(batch); // return the partially consumed batch
            }
            // A fully consumed batch is simply dropped
        }

        System.out.println("Consumption successful. "
                + quantity + " units of " + itemName + " consumed.");
        System.out.println("Remaining stock: " + item.getTotalStock());

        // ── Post-consume threshold check ───────────────────────────────
        if (item.getTotalStock() < item.threshold) {
            int needed = item.targetStock - item.getTotalStock();

            System.out.println("\nALERT: Stock of " + itemName + " has fallen below threshold!");
            System.out.println("Current stock: " + item.getTotalStock()
                    + " | Threshold: " + item.threshold
                    + " | Target stock: " + item.targetStock);
            System.out.println("Units needed to reach target: " + needed);

            if (DataStore.loggedInLevel.equals("AOC")) {
                // Rule 1: AOC only gets a warning — no auto-request, no prompt
                System.out.println("WARNING: Item " + itemName + " is below threshold!");
            } else {
                // Non-AOC: ask the user before creating a replenishment request
                System.out.print("Do you want to create a replenishment request? (yes/no): ");
                String confirm = sc.nextLine().trim();

                if (confirm.equalsIgnoreCase("yes")) {
                    SupplyRequest autoRequest = new SupplyRequest(
                            DataStore.requestCounter++,
                            DataStore.loggedInWarehouse,
                            itemName,
                            needed,
                            "PENDING",
                            true // isReplenishment flag
                    );
                    DataStore.pendingRequests.add(autoRequest);
                    DataStore.replenishmentQueue.add(
                            new ReplenishmentRequest(DataStore.loggedInWarehouse, itemName, needed));

                    System.out.println("Replenishment request created (ID: "
                            + autoRequest.requestId + ").");

                    SupplierResult best = RequestService.findBestSupplier(
                            DataStore.loggedInWarehouse, itemName, needed);
                    if (best != null) {
                        System.out.println("Will be approved by: " + best.supplierWarehouse
                                + " (Level: "
                                + DataStore.warehouseLevels.get(best.supplierWarehouse) + ")");
                    } else {
                        System.out.println("No supplier currently available. Request is queued.");
                    }
                } else {
                    System.out.println("Replenishment request not created.");
                }
            }
        }
    }

    // ── Internal: triggered after stock changes to queue replenishment ─
    // AOC-level: only log a warning (Rule 1). Others: auto-queue it.
    public static void checkAndQueueReplenishment(String warehouseName, String itemName) {
        Warehouse warehouse = DataStore.warehouses.get(warehouseName);
        if (warehouse == null || !warehouse.inventory.containsKey(itemName)) return;

        ItemStock item = warehouse.inventory.get(itemName);
        if (item == null) return;

        // Rule 12: use getTotalStock() for the check
        if (item.getTotalStock() < item.threshold) {
            int quantityNeeded = item.targetStock - item.getTotalStock();
            if (quantityNeeded > 0) {
                String level = DataStore.warehouseLevels.get(warehouseName);

                if ("AOC".equals(level)) {
                    // Rule 1: AOC only gets a warning — never auto-queues
                    System.out.println("WARNING: Item " + itemName + " is below threshold! "
                            + "Current: " + item.getTotalStock()
                            + " | Threshold: " + item.threshold);
                } else {
                    DataStore.replenishmentQueue.add(
                            new ReplenishmentRequest(warehouseName, itemName, quantityNeeded));
                    System.out.println("INFO: " + warehouseName + " stock of " + itemName
                            + " is below threshold. Replenishment queued ("
                            + quantityNeeded + " units needed).");
                }
            }
        }
    }
}

package military.services;

// ============================================================
// InventoryService.java
// Manages warehouse registration and inventory operations:
//   - loadWarehouses()         → create all warehouse nodes
//   - initializeInventory()    → seed each warehouse with stock
//   - viewInventory()          → display grouped by category
//   - checkThresholdAndWarn()  → scan and warn on low items
//   - appendStockAOCOnly()     → AOC-only external stock intake
// ============================================================

import military.data.DataStore;
import military.models.*;

import java.util.*;

public class InventoryService {

    // ── Create all warehouse objects and register them ─────────────────
    // Hierarchy: 1 AOC → 4 Commands → 8 Corps → 16 Brigades → 32 Battalions
    public static void loadWarehouses() {
        String aocName = "AOC_DELHI";
        DataStore.warehouseLevels.put(aocName, "AOC");
        DataStore.managerIDs.put(aocName, "AOCMGR500");
        DataStore.warehouses.put(aocName, new Warehouse(aocName, "AOC"));
        DataStore.graph.put(aocName, new ArrayList<>());

        int battalionCount = 1;
        int brigadeCount   = 1;
        int corpsCount     = 1;

        for (int cmd = 1; cmd <= 4; cmd++) {
            String commandName = "COMMAND_" + cmd;
            DataStore.warehouseLevels.put(commandName, "COMMAND");
            DataStore.managerIDs.put(commandName, "CMGR" + (400 + cmd));
            DataStore.warehouses.put(commandName, new Warehouse(commandName, "COMMAND"));
            DataStore.graph.put(commandName, new ArrayList<>());

            for (int c = 1; c <= 2; c++) {
                String corpsName = "CORPS_" + corpsCount;
                DataStore.warehouseLevels.put(corpsName, "CORPS");
                DataStore.managerIDs.put(corpsName, "CRPMGR" + (300 + corpsCount));
                DataStore.warehouses.put(corpsName, new Warehouse(corpsName, "CORPS"));
                DataStore.graph.put(corpsName, new ArrayList<>());

                for (int b = 1; b <= 2; b++) {
                    String brigadeName = "BRIGADE_" + brigadeCount;
                    DataStore.warehouseLevels.put(brigadeName, "BRIGADE");
                    DataStore.managerIDs.put(brigadeName, "BMGR" + (200 + brigadeCount));
                    DataStore.warehouses.put(brigadeName, new Warehouse(brigadeName, "BRIGADE"));
                    DataStore.graph.put(brigadeName, new ArrayList<>());

                    for (int bt = 1; bt <= 2; bt++) {
                        String battalionName = "BATTALION_" + battalionCount;
                        DataStore.warehouseLevels.put(battalionName, "BATTALION");
                        DataStore.managerIDs.put(battalionName, "BATMGR" + (100 + battalionCount));
                        DataStore.warehouses.put(battalionName, new Warehouse(battalionName, "BATTALION"));
                        DataStore.graph.put(battalionName, new ArrayList<>());
                        battalionCount++;
                    }
                    brigadeCount++;
                }
                corpsCount++;
            }
        }
    }

    // ── Seed inventory for every warehouse with default stock levels ───
    // Stock quantities scale up with hierarchy level.
    public static void initializeInventory() {
        if (DataStore.warehouses == null || DataStore.warehouses.isEmpty()) {
            System.out.println("No warehouses found. Load warehouses first.");
            return;
        }

        String[] items = {
            "RIFLE", "SNIPER", "NIGHT_VISION", "BINOCULAR", "DRONE",
            "GPS_DEVICE", "RADIO_SET",
            "DIESEL", "PETROL", "AVIATION_FUEL", "LUBRICANT_OIL", "BATTERY_PACK",
            "ENGINE_PART", "SPARE_TYRE", "WEAPON_PARTS", "CIRCUIT_BOARD", "TOOLS_KIT",
            "FOOD_PACK", "MED_KIT", "UNIFORM", "WATER_BOTTLE", "TENT", "BOOTS"
        };

        Random random = new Random(10); // Fixed seed for reproducible results

        for (String warehouseName : DataStore.warehouses.keySet()) {
            Warehouse warehouse = DataStore.warehouses.get(warehouseName);
            if (warehouse == null || warehouse.level == null) continue;

            for (String item : items) {
                int stock = 0, threshold = 0, target = 0;

                switch (warehouse.level) {
                    case "BATTALION": stock = 20  + random.nextInt(31);  threshold = 15;  target = 40;  break;
                    case "BRIGADE":   stock = 80  + random.nextInt(41);  threshold = 40;  target = 100; break;
                    case "CORPS":     stock = 180 + random.nextInt(71);  threshold = 100; target = 220; break;
                    case "COMMAND":   stock = 400 + random.nextInt(151); threshold = 250; target = 500; break;
                    case "AOC":       stock = 800 + random.nextInt(201); threshold = 500; target = 900; break;
                    default: continue;
                }

                String category   = UtilityService.getCategory(item);
                String rfid       = UtilityService.generateRFID(warehouseName, item);
                String expiryDate = UtilityService.getExpiryDateByItem(item);
                warehouse.addItem(item, stock, threshold, target, rfid, expiryDate, category);
            }
        }

        System.out.println("Inventory initialized successfully.");
    }

    // ── Display inventory for a warehouse, grouped by category ────────
    // Rule 11: shows TotalStock (sum of all FEFO batches) and earliest expiry.
    public static void viewInventory(String warehouseName) {
        Warehouse warehouse = DataStore.warehouses.get(warehouseName);
        if (warehouse == null) {
            System.out.println("Warehouse not found.");
            return;
        }

        System.out.println("\n==== Inventory of " + warehouseName + " ====\n");
        String[] categories = {"EQUIPMENT", "FUEL", "PARTS", "BASIC_NECESSITY"};

        for (String category : categories) {
            System.out.println("--- Category: " + category + " ---");
            System.out.printf("%-18s %-10s %-10s %-8s %-20s %-15s %-18s%n",
                    "Item", "TotalStock", "Threshold", "Target",
                    "RFID", "NextExpiry", "Category");

            boolean itemsFound = false;
            for (ItemStock item : warehouse.inventory.values()) {
                if (item == null || !category.equals(item.category)) continue;
                itemsFound = true;
                System.out.printf("%-18s %-10d %-10d %-8d %-20s %-15s %-18s%n",
                        item.itemName,
                        item.getTotalStock(),     // Sum of all FEFO batches
                        item.threshold,
                        item.targetStock,
                        item.rfid,
                        item.getEarliestExpiry(), // Top of FEFO priority queue
                        item.category);
            }

            if (!itemsFound) System.out.println("No items in this category.");
            System.out.println();
        }
    }

    // ── Scan inventory and print warnings for below-threshold items ────
    // Rule 14: AOC only gets warnings here — no requests are generated.
    // For non-AOC, replenishment creation is handled in consumeItem().
    public static void checkThresholdAndWarn(String warehouseName) {
        Warehouse warehouse = DataStore.warehouses.get(warehouseName);
        if (warehouse == null) return;

        boolean anyBelowThreshold = false;
        for (ItemStock item : warehouse.inventory.values()) {
            if (item == null) continue;
            // Rule 12: use getTotalStock() (not single batch quantity)
            if (item.getTotalStock() < item.threshold) {
                anyBelowThreshold = true;
                System.out.println("WARNING: Item " + item.itemName
                        + " is below threshold! Current: " + item.getTotalStock()
                        + " | Threshold: " + item.threshold);
            }
        }

        if (!anyBelowThreshold) {
            System.out.println("All items are above threshold.");
        }
    }

    // ── AOC-only: add incoming stock as a brand-new FEFO batch ────────
    // Rule 7: new batches are NEVER merged into existing ones.
    public static void appendStockAOCOnly(Scanner sc) {
        if (!DataStore.loggedInLevel.equals("AOC")) {
            System.out.println("Only AOC can append stock.");
            return;
        }

        checkThresholdAndWarn(DataStore.loggedInWarehouse);

        Warehouse warehouse = DataStore.warehouses.get(DataStore.loggedInWarehouse);

        System.out.print("Enter item name: ");
        String itemName = sc.nextLine().trim().toUpperCase();

        if (UtilityService.getCategory(itemName).equals("UNKNOWN")) {
            System.out.println("Invalid item name.");
            return;
        }

        System.out.print("Enter quantity: ");
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
            System.out.println("Quantity must be greater than 0.");
            return;
        }

        System.out.print("Enter expiry date (YYYY-MM-DD): ");
        String expiryDate = sc.nextLine().trim();
        if (expiryDate.isEmpty()) {
            expiryDate = UtilityService.getExpiryDateByItem(itemName); // fallback
        }

        if (warehouse.inventory.containsKey(itemName)) {
            // Existing item: add new batch to the FEFO queue (never merge)
            warehouse.inventory.get(itemName).batches.add(new Batch(quantity, expiryDate));
        } else {
            // New item: create the full ItemStock entry with first batch
            warehouse.addItem(
                itemName, quantity, 100, 200,
                UtilityService.generateRFID(DataStore.loggedInWarehouse, itemName),
                expiryDate,
                UtilityService.getCategory(itemName)
            );
        }

        System.out.println("Stock appended successfully.");
        System.out.println("Item: "                + itemName);
        System.out.println("Qty Added: "           + quantity);
        System.out.println("Expiry: "              + expiryDate);
        System.out.println("Updated Total Stock: " +
                warehouse.inventory.get(itemName).getTotalStock());
    }
}

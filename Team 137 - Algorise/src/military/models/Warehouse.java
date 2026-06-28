package military.models;

// ============================================================
// Warehouse.java
// Represents a single warehouse/unit node in the hierarchy.
// Each warehouse has a name, level (Battalion/Brigade/etc.),
// and an inventory map of item name → ItemStock.
// ============================================================

import java.util.HashMap;

public class Warehouse {

    public String warehouseName;
    public String level;

    // Maps item name (uppercase) → its stock data
    public HashMap<String, ItemStock> inventory = new HashMap<>();

    public Warehouse(String warehouseName, String level) {
        this.warehouseName = warehouseName;
        this.level         = level;
    }

    // ── Add a new item entry to this warehouse's inventory ──
    public void addItem(String itemName, int stock, int threshold, int targetStock,
                        String rfid, String expiryDate, String category) {

        inventory.put(
            itemName.toUpperCase(),
            new ItemStock(
                itemName.toUpperCase(), stock, threshold,
                targetStock, rfid, expiryDate, category
            )
        );
    }
}

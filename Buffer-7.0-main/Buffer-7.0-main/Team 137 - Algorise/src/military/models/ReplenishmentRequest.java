package military.models;

// ============================================================
// ReplenishmentRequest.java
// A simple record queued in the replenishment queue when a
// warehouse's stock falls below its threshold.
// Used by the ReplenishmentQueue display and internal checks.
// ============================================================

public class ReplenishmentRequest {

    public String warehouseName;
    public String itemName;
    public int    quantityNeeded;

    public ReplenishmentRequest(String warehouseName, String itemName, int quantityNeeded) {
        this.warehouseName  = warehouseName;
        this.itemName       = itemName;
        this.quantityNeeded = quantityNeeded;
    }
}

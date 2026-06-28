package military.models;

// ============================================================
// ItemStock.java
// Holds all stock data for one item inside a warehouse.
// Uses a PriorityQueue of Batch objects, sorted by earliest
// expiry date, to implement FEFO (First Expired, First Out).
// ============================================================

import java.util.PriorityQueue;

public class ItemStock {

    public String itemName;
    public int    threshold;    // Minimum safe level — triggers replenishment warning
    public int    targetStock;  // Ideal level to replenish up to
    public String rfid;
    public String category;

    // FEFO PriorityQueue: batch with earliest expiry date is at the head
    public PriorityQueue<Batch> batches;

    public ItemStock(String itemName, int stock, int threshold, int targetStock,
                     String rfid, String expiryDate, String category) {

        this.itemName    = itemName;
        this.threshold   = threshold;
        this.targetStock = targetStock;
        this.rfid        = rfid;
        this.category    = category;

        // Sort batches by earliest expiry first (FEFO order)
        batches = new PriorityQueue<>((a, b) -> a.expiryDate.compareTo(b.expiryDate));

        // Add the initial stock as the very first batch
        if (stock > 0 && expiryDate != null && !expiryDate.equals("NA")) {
            batches.add(new Batch(stock, expiryDate));
        } else if (stock > 0) {
            batches.add(new Batch(stock, "2032-12-31")); // Default far-future expiry
        }
    }

    // ── Helper: sum of all batch quantities ─────────────────
    public int getTotalStock() {
        int total = 0;
        for (Batch b : batches) {
            total += b.quantity;
        }
        return total;
    }

    // ── Helper: earliest expiry date (top of the FEFO queue) ─
    public String getEarliestExpiry() {
        Batch top = batches.peek();
        return (top != null) ? top.expiryDate : "NA";
    }
}

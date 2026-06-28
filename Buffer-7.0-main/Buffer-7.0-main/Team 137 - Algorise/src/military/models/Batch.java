package military.models;

// ============================================================
// Batch.java
// Represents a single stock batch with a quantity and expiry.
// The FEFO (First Expired, First Out) system works by keeping
// multiple Batch objects per item, sorted by earliest expiry.
// ============================================================

public class Batch {

    public int    quantity;
    public String expiryDate; // format: YYYY-MM-DD

    public Batch(int quantity, String expiryDate) {
        this.quantity   = quantity;
        this.expiryDate = expiryDate;
    }
}

package military.models;

// ============================================================
// DeliveryOrder.java
// Tracks a shipment from supplier to receiver warehouse.
// transitBatches stores FEFO-deducted stock batches that
// will be credited to the receiver upon delivery completion.
// ============================================================

import java.util.ArrayList;
import java.util.List;

public class DeliveryOrder {

    public int         deliveryId;
    public int         requestId;     // Linked SupplyRequest
    public String      supplier;      // Sending warehouse
    public String      receiver;      // Receiving warehouse
    public String      itemName;
    public int         quantity;
    public int         distance;      // Dijkstra distance in km
    public List<String> path;         // Full route from supplier to receiver
    public String      status;        // "PENDING_DELIVERY" → "COMPLETED"

    // Batches taken from supplier (FEFO-aware), to be given to receiver
    public List<Batch> transitBatches = new ArrayList<>();

    public DeliveryOrder(int deliveryId, int requestId, String supplier,
                         String receiver, String itemName, int quantity,
                         int distance, List<String> path, String status) {
        this.deliveryId = deliveryId;
        this.requestId  = requestId;
        this.supplier   = supplier;
        this.receiver   = receiver;
        this.itemName   = itemName;
        this.quantity   = quantity;
        this.distance   = distance;
        this.path       = path;
        this.status     = status;
    }
}

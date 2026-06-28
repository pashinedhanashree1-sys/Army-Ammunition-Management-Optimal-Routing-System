package military.models;

// ============================================================
// SupplierResult.java
// Returned by RequestService.findBestSupplier().
// Bundles together the chosen supplier warehouse, the
// Dijkstra distance, and the full delivery path.
// ============================================================

import java.util.List;

public class SupplierResult {

    public String      supplierWarehouse; // Name of the warehouse that will supply
    public int         distance;          // Dijkstra distance in km
    public List<String> path;             // Ordered route from supplier to requester

    public SupplierResult(String supplierWarehouse, int distance, List<String> path) {
        this.supplierWarehouse = supplierWarehouse;
        this.distance          = distance;
        this.path              = path;
    }
}

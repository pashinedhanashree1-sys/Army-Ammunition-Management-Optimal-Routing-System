package military.models;

// ============================================================
// DijkstraResult.java
// Returned by GraphService.dijkstra() after path computation.
// Contains the total distance and the ordered list of
// warehouse names forming the shortest route.
// ============================================================

import java.util.List;

public class DijkstraResult {

    public int         distance; // Total route distance in km
    public List<String> path;    // Ordered warehouse names from source to destination

    public DijkstraResult(int distance, List<String> path) {
        this.distance = distance;
        this.path     = path;
    }
}

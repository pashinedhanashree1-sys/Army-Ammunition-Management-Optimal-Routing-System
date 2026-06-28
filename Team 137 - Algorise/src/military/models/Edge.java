package military.models;

// ============================================================
// Edge.java
// Represents a directed edge in the warehouse road network.
// Used by GraphService to build the adjacency list and by
// Dijkstra's algorithm to find the shortest delivery path.
// ============================================================

public class Edge {

    public String to;     // Destination warehouse name
    public int    weight; // Distance in km

    public Edge(String to, int weight) {
        this.to     = to;
        this.weight = weight;
    }
}

package military.models;

// ============================================================
// NodeDistance.java
// A priority-queue entry for Dijkstra's algorithm.
// Holds a warehouse node and its current best known distance.
// Implements Comparable so the PriorityQueue always pops the
// node with the smallest distance first.
// ============================================================

public class NodeDistance implements Comparable<NodeDistance> {

    public String node;
    public int    distance;

    public NodeDistance(String node, int distance) {
        this.node     = node;
        this.distance = distance;
    }

    @Override
    public int compareTo(NodeDistance other) {
        return Integer.compare(this.distance, other.distance);
    }
}

package military.services;

// ============================================================
// GraphService.java
// Manages the warehouse road network (adjacency list graph).
// Provides:
//   - addEdge()       → build undirected weighted connections
//   - buildFullGraph()→ wires up the full hierarchy automatically
//   - dijkstra()      → shortest path between two warehouses
//   - viewConnections()→ display sorted higher-level warehouses
// ============================================================

import military.data.DataStore;
import military.models.*;

import java.util.*;

public class GraphService {

    // ── Add an undirected edge between two warehouses ──────────────────
    public static void addEdge(String a, String b, int distance) {
        if (a == null || b == null) return;
        DataStore.graph.putIfAbsent(a, new ArrayList<>());
        DataStore.graph.putIfAbsent(b, new ArrayList<>());
        DataStore.graph.get(a).add(new Edge(b, distance));
        DataStore.graph.get(b).add(new Edge(a, distance));
    }

    // ── Wire up the full hierarchy: Battalion→Brigade→Corps→Command→AOC ─
    // Uses a fixed seed so distances are deterministic every run.
    public static void buildFullGraph() {
        Random random = new Random(20);

        // Battalion → its parent Brigade  (2 battalions per brigade)
        for (int i = 1; i <= 32; i++) {
            String battalion   = "BATTALION_" + i;
            int    brigadeIdx  = ((i - 1) / 2) + 1;
            String brigade     = "BRIGADE_" + brigadeIdx;
            addEdge(battalion, brigade, 5 + random.nextInt(10));
        }

        // Brigade → its parent Corps  (2 brigades per corps)
        for (int i = 1; i <= 16; i++) {
            String brigade  = "BRIGADE_" + i;
            int    corpsIdx = ((i - 1) / 2) + 1;
            String corps    = "CORPS_" + corpsIdx;
            addEdge(brigade, corps, 10 + random.nextInt(20));
        }

        // Corps → its parent Command  (2 corps per command)
        for (int i = 1; i <= 8; i++) {
            String corps      = "CORPS_" + i;
            int    commandIdx = ((i - 1) / 2) + 1;
            String command    = "COMMAND_" + commandIdx;
            addEdge(corps, command, 20 + random.nextInt(30));
        }

        // Command → AOC_DELHI  (4 commands total)
        for (int i = 1; i <= 4; i++) {
            addEdge("AOC_DELHI", "COMMAND_" + i, 40 + random.nextInt(30));
        }
    }

    // ── Dijkstra shortest-path from source to destination ─────────────
    // Returns null if no path exists.
    public static DijkstraResult dijkstra(String source, String destination) {
        if (!DataStore.graph.containsKey(source) ||
            !DataStore.graph.containsKey(destination)) return null;

        HashMap<String, Integer>    dist = new HashMap<>();
        HashMap<String, String>     prev = new HashMap<>();
        PriorityQueue<NodeDistance> pq   = new PriorityQueue<>();

        // Initialise all distances to infinity
        for (String node : DataStore.graph.keySet()) {
            dist.put(node, Integer.MAX_VALUE);
        }

        dist.put(source, 0);
        pq.add(new NodeDistance(source, 0));

        while (!pq.isEmpty()) {
            NodeDistance current = pq.poll();
            if (!DataStore.graph.containsKey(current.node)) continue;

            for (Edge edge : DataStore.graph.get(current.node)) {
                int newDist = dist.get(current.node) + edge.weight;
                if (newDist < dist.getOrDefault(edge.to, Integer.MAX_VALUE)) {
                    dist.put(edge.to, newDist);
                    prev.put(edge.to, current.node);
                    pq.add(new NodeDistance(edge.to, newDist));
                }
            }
        }

        if (!dist.containsKey(destination) ||
            dist.get(destination) == Integer.MAX_VALUE) return null;

        // Reconstruct path from destination back to source
        LinkedList<String> path = new LinkedList<>();
        String at = destination;
        while (at != null) {
            path.addFirst(at);
            at = prev.get(at);
        }

        return new DijkstraResult(dist.get(destination), path);
    }

    // ── Display all higher-level warehouses sorted by distance ────────
    public static void viewConnections(String warehouseName) {
        if (!DataStore.graph.containsKey(warehouseName)) {
            System.out.println("No connections found.");
            return;
        }

        String myLevel    = DataStore.warehouseLevels.get(warehouseName);
        int myPriority = UtilityService.getLevelPriority(myLevel);

        System.out.println("\n==== Higher-Level Warehouses (Sorted by Distance) ====");
        System.out.printf("%-25s %-12s %-10s %s%n", "Warehouse", "Level", "Distance", "Path");
        System.out.println("-".repeat(90));

        ArrayList<SupplierResult> results = new ArrayList<>();

        for (String target : DataStore.warehouses.keySet()) {
            String targetLevel = DataStore.warehouseLevels.get(target);
            if (UtilityService.getLevelPriority(targetLevel) <= myPriority) continue;

            DijkstraResult result = dijkstra(warehouseName, target);
            if (result != null && result.distance < Integer.MAX_VALUE) {
                results.add(new SupplierResult(target, result.distance, result.path));
            }
        }

        // Sort ascending by distance
        results.sort((a, b) -> a.distance - b.distance);

        if (results.isEmpty()) {
            System.out.println("No higher-level warehouses reachable.");
            return;
        }

        for (SupplierResult r : results) {
            System.out.printf("%-25s %-12s %-10d %s%n",
                    r.supplierWarehouse,
                    DataStore.warehouseLevels.get(r.supplierWarehouse),
                    r.distance,
                    r.path);
        }
    }
}

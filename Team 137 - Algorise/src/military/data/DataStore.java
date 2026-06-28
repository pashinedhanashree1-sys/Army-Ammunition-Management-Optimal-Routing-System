package military.data;

// ============================================================
// DataStore.java
// Central storage hub for all global state in the system.
// All services read from and write to this single class,
// avoiding scattered static fields across multiple files.
// ============================================================

import military.models.*;

import java.util.*;

public class DataStore {

    // ── Authentication ──────────────────────────────────────
    // Encrypted passwords loaded from file (or hard-coded defaults)
    public static HashMap<String, String> passwords = new HashMap<>();

    // Failed login attempt counters per user/warehouse
    public static HashMap<String, Integer> loginAttempts = new HashMap<>();

    // Timestamp until which an account is locked (epoch ms)
    public static HashMap<String, Long> lockUntil = new HashMap<>();

    // ── Warehouse Registry ──────────────────────────────────
    // Maps warehouse name → its hierarchy level (BATTALION, BRIGADE, etc.)
    public static HashMap<String, String> warehouseLevels = new HashMap<>();

    // Maps warehouse name → manager ID string
    public static HashMap<String, String> managerIDs = new HashMap<>();

    // Maps warehouse name → full Warehouse object (with inventory inside)
    public static HashMap<String, Warehouse> warehouses = new HashMap<>();

    // ── Graph (for Dijkstra shortest-path routing) ──────────
    // Adjacency list: warehouse name → list of outgoing edges
    public static HashMap<String, ArrayList<Edge>> graph = new HashMap<>();

    // ── Requests and Deliveries ─────────────────────────────
    public static ArrayList<SupplyRequest>      pendingRequests   = new ArrayList<>();
    public static ArrayList<DeliveryOrder>      pendingDeliveries = new ArrayList<>();
    public static Queue<ReplenishmentRequest>   replenishmentQueue = new LinkedList<>();

    // ── Session (currently logged-in user) ──────────────────
    public static String loggedInWarehouse = "";
    public static String loggedInLevel     = "";

    // ── Auto-increment counters ──────────────────────────────
    public static int requestCounter  = 1;
    public static int deliveryCounter = 1;

    // ── Encryption key ──────────────────────────────────────
    // Simple XOR key used by UtilityService for password obfuscation
    public static final int ENCRYPTION_KEY = 12345;
}

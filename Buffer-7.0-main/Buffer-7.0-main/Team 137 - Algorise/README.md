
# Military Warehouse Management System

**Problem Statement (PS):**
Military inventory and supply systems are often manual, causing delays, poor record management, slow approvals, and inefficient tracking of stock and deliveries.

## **Video Link**

**[Watch Project Video](https://drive.google.com/drive/folders/1vEtKXEkjbZMWABuNH6q2jSydEYurgGqi)**

##**System Hierarchy photo**

<img width="1024" height="1536" alt="WhatsApp Image 2026-04-22 at 8 31 30 PM" src="https://github.com/user-attachments/assets/f9c86856-ed0d-44d0-9ea4-3150fb39aba6" />


## Data Structures Used

|  Data Structure   | Usage Area           |           Purpose                |
|------------------ |----------------------|----------------------------------|
| HashMap           | Storage & Mapping    | Fast key-value lookup (O(1))     |
| ArrayList         | Requests & Lists     | Dynamic storage & traversal      |
| Queue/LinkedList  | Replenishment System | FIFO processing                  |
| PriorityQueue     | FEFO & Pathfinding   | Priority-based processing        |
| LinkedList        | Path Tracking        | Efficient insertions             |
| StringBuilder     | Encryption           | Efficient string manipulation    |
| Arrays            | Fixed Data           | Static memory-efficient storage  |


### Design Justification
We selected data structures based on efficiency:
- **HashMap** ensures constant-time access  
- **Queue** maintains FIFO order for fair processing  
- **PriorityQueue** handles priority-based operations like FEFO and shortest path



## Folder Structure

```
MilitaryWarehouse/
├── passwords.txt                  ← Default login credentials
├── compile_and_run.sh             ← One-shot build & run script
└── src/
    └── military/
        ├── main/
        │   └── Main.java          ← Entry point + menu loop
        ├── data/
        │   └── DataStore.java     ← All global state (HashMaps, counters, session)
        ├── models/
        │   ├── Batch.java         ← Single FEFO stock batch
        │   ├── ItemStock.java     ← Item with FEFO PriorityQueue of batches
        │   ├── Warehouse.java     ← Warehouse node with inventory map
        │   ├── SupplyRequest.java ← Manual or auto-replenishment request
        │   ├── DeliveryOrder.java ← Active shipment between warehouses
        │   ├── ReplenishmentRequest.java ← Queued replenishment record
        │   ├── Edge.java          ← Graph edge (road connection)
        │   ├── NodeDistance.java  ← Dijkstra priority-queue entry
        │   ├── DijkstraResult.java← Shortest path result
        │   └── SupplierResult.java← Best supplier + route
        └── services/
            ├── AuthService.java       ← Login, lockout, switchLevel
            ├── InventoryService.java  ← Warehouse setup, view, threshold check, append
            ├── RequestService.java    ← Request creation, viewing, supplier search
            ├── DeliveryService.java   ← Approve, complete deliveries, consume, replenishment
            ├── GraphService.java      ← Build graph, Dijkstra, viewConnections
            └── UtilityService.java    ← Encrypt/decrypt, category, RFID, expiry date
```

---

## How to Compile and Run

### Option A — Shell Script (Linux / Mac / Git Bash)
```bash
cd MilitaryWarehouse
bash compile_and_run.sh
```

### Option B — Manual Terminal Commands
```bash
cd MilitaryWarehouse
mkdir -p out
find src -name "*.java" > sources.txt
javac -d out -sourcepath src @sources.txt
java -cp out military.main.Main
```

### Option C — VS Code
1. Install the **Extension Pack for Java** from the VS Code marketplace.
2. Open the `MilitaryWarehouse/` folder.
3. VS Code auto-detects source roots. Press **Run** on `Main.java`.

### Option D — IntelliJ IDEA
1. File → Open → select `MilitaryWarehouse/`
2. Right-click `src/` → Mark Directory as → Sources Root
3. Right-click `Main.java` → Run

---

## Default Login Credentials

| Step | Value |
|------|-------|
| System password | `start123` |
| Battalion password | `bat123` |
| Brigade password | `brig123` |
| Corps password | `corp123` |
| Command password | `cmd123` |
| AOC password | `aoc123` |

**Manager IDs** follow the pattern set in `InventoryService.loadWarehouses()`:
- AOC: `AOCMGR500`
- COMMAND_1: `CMGR401`, COMMAND_2: `CMGR402`, etc.
- CORPS_1: `CRPMGR301`, CORPS_2: `CRPMGR302`, etc.
- BRIGADE_1: `BMGR201`, BRIGADE_2: `BMGR202`, etc.
- BATTALION_1: `BATMGR101`, BATTALION_2: `BATMGR102`, etc.

---

## Key System Features

| Feature | Where Implemented |
|---------|------------------|
| XOR + mirror encryption | `UtilityService.encrypt/decrypt` |
| Account lockout (3 attempts → 1 hr) | `AuthService.verifyEncryptedCredential` |
| FEFO batch inventory | `ItemStock` (PriorityQueue by expiry) |
| Dijkstra shortest path | `GraphService.dijkstra` |
| Hierarchy-aware supplier selection | `RequestService.findBestSupplier` |
| FEFO stock deduction at approval | `DeliveryService.approveRequest` |
| FEFO batch credit at delivery | `DeliveryService.completeDelivery` |
| Auto-replenishment after consume | `DeliveryService.consumeItem` |
| AOC-only stock append | `InventoryService.appendStockAOCOnly` |
| Auto-delete fulfilled requests | `RequestService.autoDeleteFulfilledRequest` |

---

## Method Migration Reference (from original buffer3.java)

| Original Method | Moved To |
|----------------|----------|
| `encrypt / decrypt / mirror` | `UtilityService` |
| `getCategory` | `UtilityService` |
| `generateRFID` | `UtilityService` |
| `getExpiryDateByItem` | `UtilityService` |
| `getLevelPriority` | `UtilityService` |
| `loadPasswordsFromFile` | `AuthService` |
| `isLocked / verifyEncryptedCredential` | `AuthService` |
| `authenticate` | `AuthService` |
| `switchLevel` | `AuthService` |
| `loadWarehouses` | `InventoryService` |
| `initializeInventory` | `InventoryService` |
| `viewInventory` | `InventoryService` |
| `checkThresholdAndWarn` | `InventoryService` |
| `appendStockAOCOnly` | `InventoryService` |
| `addEdge / buildFullGraph` | `GraphService` |
| `dijkstra` | `GraphService` |
| `viewConnections` | `GraphService` |
| `requestItem` | `RequestService` |
| `viewPendingRequests / viewMyRequestsOnly` | `RequestService` |
| `viewMyReplenishmentQueue / viewReplenishmentQueue` | `RequestService` |
| `findBestSupplier / getEligibleSuppliers` | `RequestService` |
| `autoDeleteFulfilledRequest` | `RequestService` |
| `approveRequest` | `DeliveryService` |
| `viewPendingDeliveries` | `DeliveryService` |
| `completeDelivery` | `DeliveryService` |
| `consumeItem` | `DeliveryService` |
| `checkAndQueueReplenishment` | `DeliveryService` |
| All static HashMaps / ArrayLists | `DataStore` |
| `inventoryMenu` | `Main.runMenuLoop` |
| `main` | `Main.main` |

package military.main;

// ============================================================
// Main.java
// Application entry point.
// Orchestrates startup sequence:
//   1. Load passwords
//   2. Create warehouse nodes
//   3. Build road network graph
//   4. Seed initial inventory
//   5. Authenticate user
//   6. Launch interactive menu loop
// ============================================================

import military.data.DataStore;
import military.services.*;

import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        try {
            // ── Startup sequence ────────────────────────────────────────
            AuthService.loadPasswordsFromFile("passwords.txt");
            InventoryService.loadWarehouses();
            GraphService.buildFullGraph();
            InventoryService.initializeInventory();

            // ── Authentication ──────────────────────────────────────────
            boolean loginSuccess = AuthService.authenticate(sc);

            if (loginSuccess) {
                runMenuLoop(sc);
            } else {
                System.out.println("Authentication failed. Exiting system.");
            }

        } catch (Exception e) {
            System.out.println("System error occurred: " + e.getMessage());
            e.printStackTrace();
        } finally {
            sc.close();
        }
    }

    // ── Main interactive menu loop ─────────────────────────────────────
    // Three different menu layouts depending on the logged-in level.
    private static void runMenuLoop(Scanner sc) {
        boolean running = true;

        while (running) {
            printMenu();

            int choice;
            try {
                choice = sc.nextInt();
                sc.nextLine();
            } catch (Exception e) {
                sc.nextLine();
                continue;
            }

            // Route the choice to the correct service method
            if (DataStore.loggedInLevel.equals("AOC")) {
                running = handleAOCMenu(sc, choice);
            } else if (DataStore.loggedInLevel.equals("BATTALION")) {
                running = handleBattalionMenu(sc, choice);
            } else {
                running = handleMiddleMenu(sc, choice);
            }
        }
    }

    // ── Print the appropriate menu for the logged-in level ────────────
    private static void printMenu() {
        System.out.println("\n==== Inventory Menu ====");
        System.out.println("Logged in as: "
                + DataStore.loggedInWarehouse
                + " (" + DataStore.loggedInLevel + ")");

        if (DataStore.loggedInLevel.equals("AOC")) {
            // Rule 2: AOC does NOT have Complete Delivery option
            System.out.println("1.  View My Inventory");
            System.out.println("2.  View Pending Requests");
            System.out.println("3.  Approve Request and Create Delivery");
            System.out.println("4.  View Pending Deliveries");
            System.out.println("5.  View Replenishment Queue");
            System.out.println("6.  View Connected Distances");
            System.out.println("7.  Append Stock");
            System.out.println("8.  Consume Item");
            System.out.println("9.  Switch Level");
            System.out.println("10. Exit");

        } else if (DataStore.loggedInLevel.equals("BATTALION")) {
            // Battalion cannot approve requests — it only raises them
            System.out.println("1. View My Inventory");
            System.out.println("2. Request Item");
            System.out.println("3. View My Requests");
            System.out.println("4. Complete Delivery and Update Stock");
            System.out.println("5. View Replenishment Queue");
            System.out.println("6. View Connected Distances");
            System.out.println("7. Consume Item");
            System.out.println("8. Switch Level");
            System.out.println("9. Exit");

        } else {
            // Brigade / Corps / Command: full two-way menu
            System.out.println("1.  View My Inventory");
            System.out.println("2.  Request Item");
            System.out.println("3.  View Pending Requests");
            System.out.println("4.  Approve Request and Create Delivery");
            System.out.println("5.  View Pending Deliveries");
            System.out.println("6.  Complete Delivery and Update Stock");
            System.out.println("7.  View Replenishment Queue");
            System.out.println("8.  View Connected Distances");
            System.out.println("9.  Consume Item");
            System.out.println("10. Switch Level");
            System.out.println("11. Exit");
        }

        System.out.print("Enter choice: ");
    }

    // ── AOC menu handler ───────────────────────────────────────────────
    private static boolean handleAOCMenu(Scanner sc, int choice) {
        switch (choice) {
            case 1:
                InventoryService.viewInventory(DataStore.loggedInWarehouse);
                // Rule 1: show threshold warning after viewing inventory
                InventoryService.checkThresholdAndWarn(DataStore.loggedInWarehouse);
                break;
            case 2:  RequestService.viewPendingRequests();          break;
            case 3:  DeliveryService.approveRequest(sc);            break;
            case 4:  DeliveryService.viewPendingDeliveries();       break;
            case 5:  RequestService.viewReplenishmentQueue();       break;
            case 6:  GraphService.viewConnections(DataStore.loggedInWarehouse); break;
            case 7:  InventoryService.appendStockAOCOnly(sc);       break;
            case 8:
                // Rule 1: consumeItem calls checkThresholdAndWarn for AOC internally
                DeliveryService.consumeItem(sc);
                break;
            case 9:  AuthService.switchLevel(sc);                   break;
            case 10:
                System.out.println("Exiting system...");
                return false; // stop loop
            default: System.out.println("Invalid choice.");
        }
        return true;
    }

    // ── Battalion menu handler ─────────────────────────────────────────
    private static boolean handleBattalionMenu(Scanner sc, int choice) {
        switch (choice) {
            case 1:
                InventoryService.viewInventory(DataStore.loggedInWarehouse);
                InventoryService.checkThresholdAndWarn(DataStore.loggedInWarehouse);
                break;
            case 2:  RequestService.requestItem(sc);                break;
            case 3:  RequestService.viewMyRequestsOnly();           break;
            case 4:  DeliveryService.completeDelivery(sc);          break;
            case 5:  RequestService.viewMyReplenishmentQueue();     break;
            case 6:  GraphService.viewConnections(DataStore.loggedInWarehouse); break;
            case 7:  DeliveryService.consumeItem(sc);               break;
            case 8:  AuthService.switchLevel(sc);                   break;
            case 9:
                System.out.println("Exiting system...");
                return false;
            default: System.out.println("Invalid choice.");
        }
        return true;
    }

    // ── Brigade / Corps / Command menu handler ─────────────────────────
    private static boolean handleMiddleMenu(Scanner sc, int choice) {
        switch (choice) {
            case 1:
                InventoryService.viewInventory(DataStore.loggedInWarehouse);
                InventoryService.checkThresholdAndWarn(DataStore.loggedInWarehouse);
                break;
            case 2:  RequestService.requestItem(sc);                break;
            case 3:  RequestService.viewPendingRequests();          break;
            case 4:  DeliveryService.approveRequest(sc);            break;
            case 5:  DeliveryService.viewPendingDeliveries();       break;
            case 6:  DeliveryService.completeDelivery(sc);          break;
            case 7:  RequestService.viewReplenishmentQueue();       break;
            case 8:  GraphService.viewConnections(DataStore.loggedInWarehouse); break;
            case 9:  DeliveryService.consumeItem(sc);               break;
            case 10: AuthService.switchLevel(sc);                   break;
            case 11:
                System.out.println("Exiting system...");
                return false;
            default: System.out.println("Invalid choice.");
        }
        return true;
    }
}

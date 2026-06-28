package military.services;

// ============================================================
// AuthService.java
// Handles all authentication concerns:
//   - Loading passwords from file (or using defaults)
//   - Account lockout after 3 failed attempts
//   - Two-factor login: level password + Manager ID
//   - Level switching during a session
// ============================================================

import military.data.DataStore;

import java.io.File;
import java.util.Scanner;

public class AuthService {

    // ── Load passwords from a flat file ───────────────────────────────
    // Expected format: KEY=plain_password  OR  KEY=ENC:already_encrypted
    public static void loadPasswordsFromFile(String filePath) {
        try {
            Scanner fileScanner = new Scanner(new File(filePath));
            while (fileScanner.hasNextLine()) {
                String line = fileScanner.nextLine().trim();
                if (!line.contains("=")) continue;

                String[] parts = line.split("=", 2);
                String   key   = parts[0].trim().toUpperCase();
                String   value = parts[1].trim();

                if (value.startsWith("ENC:")) {
                    // Already encrypted — store as-is (strip the prefix tag)
                    DataStore.passwords.put(key, value.substring(4));
                } else {
                    // Plain text — encrypt before storing
                    DataStore.passwords.put(key, UtilityService.encrypt(value));
                }
            }
            fileScanner.close();

        } catch (Exception e) {
            System.out.println("Passwords file not found. Using default credentials.");
            DataStore.passwords.put("START",     UtilityService.encrypt("start123"));
            DataStore.passwords.put("BATTALION", UtilityService.encrypt("bat123"));
            DataStore.passwords.put("BRIGADE",   UtilityService.encrypt("brig123"));
            DataStore.passwords.put("CORPS",     UtilityService.encrypt("corp123"));
            DataStore.passwords.put("COMMAND",   UtilityService.encrypt("cmd123"));
            DataStore.passwords.put("AOC",       UtilityService.encrypt("aoc123"));
        }
    }

    // ── Check if an account is currently locked ───────────────────────
    public static boolean isLocked(String user) {
        if (!DataStore.lockUntil.containsKey(user)) return false;
        return System.currentTimeMillis() < DataStore.lockUntil.get(user);
    }

    // ── Verify encrypted input against stored encrypted password ──────
    // Tracks failed attempts and locks account for 1 hour after 3 failures.
    public static boolean verifyEncryptedCredential(String user, String stored, String input) {
        if (stored == null) {
            System.out.println("No credential found for user.");
            return false;
        }

        if (isLocked(user)) {
            long remaining = DataStore.lockUntil.get(user) - System.currentTimeMillis();
            long minutes   = Math.max(remaining / (60 * 1000), 1);
            System.out.println("Account locked. Try again after " + minutes + " minute(s).");
            return false;
        }

        String encrypted = UtilityService.encrypt(input);

        if (encrypted.equals(stored)) {
            // Successful login — reset failure counters
            DataStore.loginAttempts.put(user, 0);
            DataStore.lockUntil.remove(user);
            return true;
        }

        // Failed attempt
        int failCount = DataStore.loginAttempts.getOrDefault(user, 0) + 1;
        DataStore.loginAttempts.put(user, failCount);

        if (failCount >= 3) {
            // Lock for 1 hour
            DataStore.lockUntil.put(user, System.currentTimeMillis() + 3_600_000L);
            DataStore.loginAttempts.put(user, 0);
            System.out.println("Too many wrong attempts. Account locked for 1 hour.");
        } else {
            System.out.println("Wrong credential. Attempts left: " + (3 - failCount));
        }

        return false;
    }

    // ── Full login flow (called once at startup) ───────────────────────
    // Step 1: System start password
    // Step 2: Level selection + level password
    // Step 3: Warehouse name selection
    // Step 4: Manager ID (second-factor verification)
    public static boolean authenticate(Scanner sc) {
        System.out.println("==== Military Warehouse Authentication ====");

        // Step 1: System start password
        boolean startPassed = false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Enter system starting password: ");
            String input = sc.nextLine().trim();
            if (verifyEncryptedCredential("START",
                    DataStore.passwords.getOrDefault("START", ""), input)) {
                startPassed = true;
                break;
            }
            if (attempt == 3) {
                System.out.println("Maximum attempts reached. Exiting system.");
                return false;
            }
        }
        if (!startPassed) return false;

        // Step 2: Choose level
        System.out.println("\nSelect your level:");
        System.out.println("1. Battalion  2. Brigade  3. Corps  4. Command  5. AOC");
        System.out.print("Enter choice: ");

        int levelChoice;
        try {
            levelChoice = sc.nextInt();
            sc.nextLine();
        } catch (Exception e) {
            System.out.println("Invalid input.");
            sc.nextLine();
            return false;
        }

        String level = parseLevelChoice(levelChoice);
        if (level.isEmpty()) {
            System.out.println("Invalid level choice.");
            return false;
        }

        // Verify level password
        boolean levelPassed = false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Enter password for " + level + " level: ");
            String input = sc.nextLine().trim();
            if (verifyEncryptedCredential(level,
                    DataStore.passwords.getOrDefault(level, ""), input)) {
                levelPassed = true;
                break;
            }
            if (attempt == 3) {
                System.out.println("Maximum attempts reached. Exiting system.");
                return false;
            }
        }
        if (!levelPassed) return false;

        // Step 3: Select warehouse
        System.out.print("Enter warehouse/unit name: ");
        String warehouseName = sc.nextLine().toUpperCase();

        if (!DataStore.warehouseLevels.containsKey(warehouseName)) {
            System.out.println("Warehouse not found.");
            return false;
        }
        if (!DataStore.warehouseLevels.get(warehouseName).equals(level)) {
            System.out.println("This warehouse does not belong to the selected level.");
            return false;
        }

        // Step 4: Manager ID (second factor)
        String managerID = DataStore.managerIDs.get(warehouseName);
        if (managerID == null) {
            System.out.println("Manager ID not found.");
            return false;
        }
        String encryptedManagerID = UtilityService.encrypt(managerID.toUpperCase());

        boolean managerPassed = false;
        for (int attempt = 1; attempt <= 3; attempt++) {
            System.out.print("Enter Manager ID for second verification: ");
            String input = sc.nextLine().toUpperCase();
            if (verifyEncryptedCredential(warehouseName, encryptedManagerID, input)) {
                managerPassed = true;
                break;
            }
            if (attempt == 3) {
                System.out.println("Maximum attempts reached. Exiting system.");
                return false;
            }
        }
        if (!managerPassed) return false;

        // Successful login
        DataStore.loggedInWarehouse = warehouseName;
        DataStore.loggedInLevel     = level;
        System.out.println("\nLogin successful!");
        System.out.println("Access granted to " + level + " level for " + warehouseName + ".");
        return true;
    }

    // ── Switch level mid-session ───────────────────────────────────────
    // Re-authenticates with new level password + warehouse + Manager ID.
    public static void switchLevel(Scanner sc) {
        System.out.println("\n==== Switch Level ====");
        System.out.println("Select new level:");
        System.out.println("1. Battalion  2. Brigade  3. Corps  4. Command  5. AOC");
        System.out.print("Enter choice: ");

        int choice;
        try {
            choice = sc.nextInt();
            sc.nextLine();
        } catch (Exception e) {
            System.out.println("Invalid input.");
            sc.nextLine();
            return;
        }

        String level = parseLevelChoice(choice);
        if (level.isEmpty()) {
            System.out.println("Invalid level.");
            return;
        }

        System.out.print("Enter password for " + level + ": ");
        String pw = sc.nextLine();
        if (!verifyEncryptedCredential(level,
                DataStore.passwords.getOrDefault(level, ""), pw)) {
            System.out.println("Wrong password.");
            return;
        }

        System.out.print("Enter warehouse name: ");
        String warehouseName = sc.nextLine().toUpperCase();

        if (!DataStore.warehouseLevels.containsKey(warehouseName) ||
            !DataStore.warehouseLevels.get(warehouseName).equals(level)) {
            System.out.println("Invalid warehouse for this level.");
            return;
        }

        System.out.print("Enter Manager ID: ");
        String managerInput = sc.nextLine().toUpperCase();

        String managerID = DataStore.managerIDs.get(warehouseName);
        if (managerID == null) {
            System.out.println("Manager not found.");
            return;
        }
        String encryptedManagerID = UtilityService.encrypt(managerID);

        if (!verifyEncryptedCredential(warehouseName, encryptedManagerID, managerInput)) {
            System.out.println("Invalid Manager ID.");
            return;
        }

        DataStore.loggedInLevel     = level;
        DataStore.loggedInWarehouse = warehouseName;
        System.out.println("Switched successfully to " + level + " (" + warehouseName + ")");
    }

    // ── Internal: parse level number to level name ────────────────────
    private static String parseLevelChoice(int choice) {
        return switch (choice) {
            case 1 -> "BATTALION";
            case 2 -> "BRIGADE";
            case 3 -> "CORPS";
            case 4 -> "COMMAND";
            case 5 -> "AOC";
            default -> "";
        };
    }
}

package military.services;

// ============================================================
// UtilityService.java
// Stateless helper methods used across the whole application.
// Covers:
//   - XOR + mirror character encryption / decryption
//   - Item category lookup
//   - RFID tag generation
//   - Default expiry-date rules by item type
// ============================================================

import military.data.DataStore;

public class UtilityService {

    // ── Mirror character (reverses alphabet, digits, printable ASCII) ──
    // Part of the two-step encryption: mirror first, then XOR.
    public static char mirror(char c) {
        if (c >= 'a' && c <= 'z') return (char) ('z' - (c - 'a'));
        if (c >= 'A' && c <= 'Z') return (char) ('Z' - (c - 'A'));
        if (c >= '0' && c <= '9') return (char) ('9' - (c - '0'));
        if (c >= 32  && c <= 126) return (char) (126 - (c - 32));
        return c;
    }

    // ── Encrypt a plain-text string → numeric XOR token string ────────
    // Each character is mirrored then XOR-ed with the key, separated by "#"
    public static String encrypt(String s) {
        if (s == null) return "";
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = mirror(s.charAt(i));
            int  x  = ch ^ DataStore.ENCRYPTION_KEY;
            result.append(x).append("#");
        }
        return result.toString();
    }

    // ── Decrypt a token string back to plain text ──────────────────────
    public static String decrypt(String s) {
        if (s == null || s.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        String[]      parts  = s.split("#");
        for (String part : parts) {
            if (part.isEmpty()) continue;
            int  num = Integer.parseInt(part);
            char ch  = (char) (num ^ DataStore.ENCRYPTION_KEY);
            result.append(mirror(ch));
        }
        return result.toString();
    }

    // ── Determine the category of an item by name ──────────────────────
    public static String getCategory(String itemName) {
        if (itemName == null) return "UNKNOWN";
        itemName = itemName.toUpperCase();

        if (itemName.equals("RIFLE")       || itemName.equals("SNIPER")      ||
            itemName.equals("NIGHT_VISION")|| itemName.equals("BINOCULAR")   ||
            itemName.equals("DRONE")       || itemName.equals("GPS_DEVICE")  ||
            itemName.equals("RADIO_SET"))
            return "EQUIPMENT";

        if (itemName.equals("DIESEL")      || itemName.equals("PETROL")      ||
            itemName.equals("AVIATION_FUEL")|| itemName.equals("LUBRICANT_OIL") ||
            itemName.equals("BATTERY_PACK"))
            return "FUEL";

        if (itemName.equals("ENGINE_PART") || itemName.equals("SPARE_TYRE")  ||
            itemName.equals("WEAPON_PARTS")|| itemName.equals("CIRCUIT_BOARD")||
            itemName.equals("TOOLS_KIT"))
            return "PARTS";

        if (itemName.equals("FOOD_PACK")   || itemName.equals("MED_KIT")     ||
            itemName.equals("UNIFORM")     || itemName.equals("WATER_BOTTLE")||
            itemName.equals("TENT")        || itemName.equals("BOOTS"))
            return "BASIC_NECESSITY";

        return "UNKNOWN";
    }

    // ── Generate a unique RFID tag for a warehouse-item pair ──────────
    // Format: RFID-[FIRST3_OF_WAREHOUSE]-[FIRST3_OF_ITEM]-[HASH]
    public static String generateRFID(String warehouseName, String itemName) {
        if (warehouseName == null || itemName == null) return "RFID-INVALID";
        String warehousePrefix = warehouseName.length() >= 3
                ? warehouseName.substring(0, 3) : warehouseName;
        String itemPrefix = itemName.length() >= 3
                ? itemName.substring(0, 3) : itemName;
        int hashNum = Math.abs((warehouseName + itemName).hashCode() % 100000);
        return "RFID-" + warehousePrefix.toUpperCase()
                       + "-" + itemPrefix.toUpperCase()
                       + "-" + hashNum;
    }

    // ── Return the default expiry date for a given item type ──────────
    // Rule 10: perishables and fuels get shorter expiry; durable items get 2032.
    public static String getExpiryDateByItem(String itemName) {
        if (itemName == null) return "2032-12-31";
        itemName = itemName.toUpperCase();

        if (itemName.equals("FOOD_PACK"))                            return "2027-06-30";
        if (itemName.equals("MED_KIT"))                              return "2027-12-31";
        if (itemName.equals("WATER_BOTTLE"))                         return "2028-01-01";

        if (itemName.equals("DIESEL")        || itemName.equals("PETROL") ||
            itemName.equals("AVIATION_FUEL") || itemName.equals("LUBRICANT_OIL"))
            return "2028-03-31";

        if (itemName.equals("BATTERY_PACK")  || itemName.equals("CIRCUIT_BOARD"))
            return "2028-09-30";

        return "2032-12-31"; // Default: long-lived items
    }

    // ── Map level name to a numeric priority (higher = more authority) ─
    public static int getLevelPriority(String level) {
        switch (level) {
            case "BATTALION": return 1;
            case "BRIGADE":   return 2;
            case "CORPS":     return 3;
            case "COMMAND":   return 4;
            case "AOC":       return 5;
            default:          return 0;
        }
    }
}

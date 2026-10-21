package com.ytdd9527.networksexpansion.utils.databases;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Preflight for old Drawer databases before combining historical duplicate amount rows.
 * This runs once during SQLite startup, inside the existing createTable transaction.
 */
final class DrawerLegacyMigrationGuard {

    private DrawerLegacyMigrationGuard() {
    }

    static void validate(Connection connection) throws SQLException {
        // ItemStored.Amount is a SQLite CHAR column, so strict digit checking is
        // necessary before CAST. SQLite would otherwise silently accept e.g. '12x'
        // as 12, and a Java int cannot represent a historical 3-billion count.
        final String invalidRow = "SELECT ContainerID, ItemID, Amount FROM " + DataTables.ITEM_STORED
            + " WHERE LENGTH(TRIM(Amount)) = 0"
            + " OR TRIM(Amount) GLOB '*[^0-9]*'"
            + " OR CAST(TRIM(Amount) AS INTEGER) > 2147483647 LIMIT 1;";
        try (Statement query = connection.createStatement();
             ResultSet result = query.executeQuery(invalidRow)) {
            if (result.next()) {
                throw new SQLException("Invalid historical Networks drawer amount for ContainerID="
                    + result.getString(1) + ", ItemID=" + result.getString(2)
                    + "; preserve the original database and startup backup for reconciliation");
            }
        }

        // SQLite SUM can produce a legitimate 64-bit amount that cannot be loaded
        // into ItemContainer's int. Reject without rewriting the historical rows.
        final String excessiveGroup = "SELECT ContainerID, ItemID, SUM(CAST(Amount AS INTEGER)) "
            + "FROM " + DataTables.ITEM_STORED
            + " GROUP BY ContainerID, ItemID"
            + " HAVING SUM(CAST(Amount AS INTEGER)) > 2147483647 LIMIT 1;";
        try (Statement query = connection.createStatement();
             ResultSet result = query.executeQuery(excessiveGroup)) {
            if (result.next()) {
                throw new SQLException("Historical Networks drawer duplicates exceed Java int capacity for "
                    + "ContainerID=" + result.getString(1) + ", ItemID=" + result.getString(2)
                    + "; preserve the original database and startup backup for reconciliation");
            }
        }
    }
}

package com.ytdd9527.networksexpansion.utils.databases;

import org.junit.jupiter.api.Test;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

/** Actual in-memory SQLite JDBC; no MockBukkit or live database. */
class DrawerLegacyMigrationGuardTest {
    private Connection db() throws Exception {
        Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
        try (Statement statement = connection.createStatement()) {
            statement.execute(DataTables.ITEM_STORED_CREATION);
        }
        return connection;
    }

    private static void add(Connection connection, int containerId, int itemId, String value) throws SQLException {
        try (var statement = connection.prepareStatement(
            "INSERT INTO ItemStored(ContainerID,ItemID,Amount) VALUES(?,?,?)")) {
            statement.setInt(1, containerId);
            statement.setInt(2, itemId);
            statement.setString(3, value);
            statement.executeUpdate();
        }
    }

    private static String snapshot(Connection c) throws SQLException {
        try (var s = c.createStatement(); var r = s.executeQuery(
            "SELECT ContainerID,ItemID,Amount FROM ItemStored ORDER BY rowid")) {
            StringBuilder out = new StringBuilder();
            while (r.next()) out.append(r.getString(1)).append(',')
                .append(r.getString(2)).append(',').append(r.getString(3)).append(';');
            return out.toString();
        }
    }

    @Test void rejectsDuplicateCountAboveIntWithoutChangingAnyRow() throws Exception {
        try (Connection c = db()) {
            add(c,1,5,"2000000000"); add(c,1,5,"2000000000");
            String before = snapshot(c);
            SQLException error = assertThrows(SQLException.class, () -> DrawerLegacyMigrationGuard.validate(c));
            assertTrue(error.getMessage().contains("duplicates exceed"));
            assertEquals(before, snapshot(c));
        }
    }

    @Test void rejectsOversizedSingleRowEvenWhenUniqueIndexAlreadyExists() throws Exception {
        try (Connection c = db()) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE UNIQUE INDEX idx_networks_item_stored_unique ON ItemStored(ContainerID,ItemID)");
            }
            add(c,1,5,"3000000000");
            String before = snapshot(c);
            assertThrows(SQLException.class, () -> DrawerLegacyMigrationGuard.validate(c));
            assertEquals(before, snapshot(c));
        }
    }

    @Test void retainsExactValidZeroAndIntMax() throws Exception {
        try (Connection c = db()) {
            add(c,1,5,"0"); add(c,1,7,"2147483646"); add(c,1,7,"1");
            String before = snapshot(c);
            assertDoesNotThrow(() -> DrawerLegacyMigrationGuard.validate(c));
            assertEquals(before, snapshot(c));
        }
    }

    @Test void rejectsMalformedAndNegativeRowsWithoutMutation() throws Exception {
        for (String bad : new String[] {"-1", "-2", "12x", "", "+7", "1.5", "1e2", "90000000000000000000"}) {
            try (Connection c = db()) {
                add(c,1,5,bad);
                String before = snapshot(c);
                assertThrows(SQLException.class, () -> DrawerLegacyMigrationGuard.validate(c), bad);
                assertEquals(before, snapshot(c), bad);
            }
        }
    }

    @Test void randomizedIndependentGroupsKeepExactAmountsOrRefuseUnsafeRows() throws Exception {
        Random random = new Random(20261021L);
        for (int trial = 0; trial < 300; trial++) {
            try (Connection c = db()) {
                long sum = 0;
                int count = 1 + random.nextInt(5);
                for (int i = 0; i < count; i++) {
                    int n = random.nextInt(9) == 0 ? Integer.MAX_VALUE : random.nextInt(400_000_000);
                    sum += n;
                    add(c,5,13,Integer.toString(n));
                }
                String before = snapshot(c);
                if (sum <= Integer.MAX_VALUE) {
                    assertDoesNotThrow(() -> DrawerLegacyMigrationGuard.validate(c));
                } else {
                    assertThrows(SQLException.class, () -> DrawerLegacyMigrationGuard.validate(c));
                }
                assertEquals(before, snapshot(c));
            }
        }
    }
}

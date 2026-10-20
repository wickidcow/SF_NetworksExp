package com.ytdd9527.networksexpansion.utils.databases;

import com.ytdd9527.networksexpansion.core.services.LocalizationService;
import io.github.sefiraat.networks.Networks;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Production cache/queue/journal/JDBC integration; only the Bukkit plugin host is mocked.
 * Synthetic existing IDs isolate amount persistence; this is not item-template or gameplay acceptance.
 */
@Timeout(20)
class DrawerShutdownReceiptTest {
    @TempDir
    Path directory;

    private final Map<String, Object> originalGlobals = new LinkedHashMap<>();
    private QueryQueue queue;
    private DataSource source;
    private YamlConfiguration config;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        for (String name : new String[] {"instance", "dataSource", "queryQueue"}) {
            originalGlobals.put(name, field(name).get(null));
        }
        Networks plugin = mock(Networks.class);
        config = new YamlConfiguration();
        config.set("database.recovery-journal", true);
        config.set("database.startup-backups.enabled", false);
        Logger logger = Logger.getLogger("DrawerShutdownReceiptTest");
        logger.setLevel(Level.OFF);
        when(plugin.getName()).thenReturn("Networks");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(logger);
        LocalizationService localization = mock(LocalizationService.class);
        when(localization.getString(anyString())).thenAnswer(call -> call.getArgument(0));
        field("localizationService").set(plugin, localization);
        field("instance").set(null, plugin);
        DataStorage.clearRuntimeCache();
        openRuntime();
    }

    @AfterEach
    void tearDown() throws Exception {
        try {
            if (queue != null) {
                queue.shutdown(5_000);
                assertFalse(queue.isWorkerRunning(), "A test must not leak the production worker");
            }
            if (source != null) source.close();
            DataStorage.clearRuntimeCache();
        } finally {
            for (var entry : originalGlobals.entrySet()) field(entry.getKey()).set(null, entry.getValue());
            MockBukkit.unmock();
        }
    }

    @Test
    void unstartedSaveKeepsDetachedAbsoluteAmountAfterRestart() throws Exception {
        seed(3, 10);
        try (PausedWorker ignored = pauseWorker()) {
            DataStorage.setStoredAmount(7, 3, 7);
            DataStorage.saveAmountChange();
            assertEquals(0, DataStorage.getPendingAmountChangeCount(), "The save owns the detached map");
            cancelAndCheckpoint();
            assertEquals(1, queue.snapshot().cancelled());
            assertEquals(Map.of("7:3", 10), rows(), "Cancelled SQL must not execute");
        }
        reopenAndReplay();
        assertEquals(Map.of("7:3", 7), rows(), "Cancelled receipt must survive a closed/reopened SQLite file");
    }

    @Test
    void cancelledDeleteRemainsTombstoneAfterRestart() throws Exception {
        seed(3, 10);
        try (PausedWorker ignored = pauseWorker()) {
            DataStorage.deleteStoredItem(7, 3);
            DataStorage.saveAmountChange();
            cancelAndCheckpoint();
            assertEquals(2, queue.snapshot().cancelled(), "Direct DELETE and save were both unstarted");
        }
        reopenAndReplay();
        assertEquals(Map.of(), rows(), "An old balance must not reappear after the final withdrawal");
    }

    @Test
    void newerPendingWinsWithoutDiscardingOtherDetachedKeys() throws Exception {
        seed(3, 10);
        seed(4, 9);
        try (PausedWorker ignored = pauseWorker()) {
            DataStorage.setStoredAmount(7, 3, 7);
            DataStorage.setStoredAmount(7, 4, 5);
            DataStorage.saveAmountChange();
            DataStorage.setStoredAmount(7, 3, 2);
            cancelAndCheckpoint();
        }
        reopenAndReplay();
        assertEquals(Map.of("7:3", 2, "7:4", 5), rows(), "Newer intent wins per key, not per whole snapshot");
    }

    @Test
    void cancelledDeleteAndReinsertRetainsNewAbsoluteAmount() throws Exception {
        seed(3, 10);
        try (PausedWorker ignored = pauseWorker()) {
            DataStorage.deleteStoredItem(7, 3);
            DataStorage.addStoredItem(7, 3, 4);
            DataStorage.saveAmountChange();
            cancelAndCheckpoint();
            assertEquals(3, queue.snapshot().cancelled());
        }
        reopenAndReplay();
        assertEquals(Map.of("7:3", 4), rows(), "Reinsert recovery is absolute, not old amount plus new amount");
    }

    @Test
    void completedSaveCannotBeCheckpointedAgain() throws Exception {
        DataStorage.setStoredAmount(7, 3, 7);
        DataStorage.saveAmountChange();
        assertTrue(queue.awaitDrained(5_000));
        assertFalse(DataStorage.isSaveInFlight());
        assertFalse(Files.exists(source.getRecoveryJournalFile()));
        DataStorage.checkpointPendingChangesForShutdown();
        assertFalse(Files.exists(source.getRecoveryJournalFile()), "Successful receipt must be released");
        assertEquals(Map.of("7:3", 7), rows());
        reopenAndReplay();
        assertEquals(Map.of("7:3", 7), rows());
    }

    @Test
    void queueRejectionMergesBackBeforeNewerUpdates() throws Exception {
        seed(3, 10);
        assertTrue(queue.shutdown(5_000));
        DataStorage.setStoredAmount(7, 3, 7);
        DataStorage.saveAmountChange();
        assertFalse(DataStorage.isSaveInFlight());
        assertEquals("queue-rejected", DataStorage.getLastSaveStatus());
        DataStorage.setStoredAmount(7, 3, 2);
        DataStorage.checkpointPendingChangesForShutdown();
        reopenAndReplay();
        assertEquals(Map.of("7:3", 2), rows());
    }

    @Test
    void workerJournalFailurePreventsSqlAndRetainsRetry() throws Exception {
        seed(3, 10);
        Path journal = source.getRecoveryJournalFile();
        Files.createDirectory(journal);
        DataStorage.setStoredAmount(7, 3, 42);
        DataStorage.saveAmountChange();
        assertTrue(queue.awaitDrained(5_000));
        assertEquals("save-failed", DataStorage.getLastSaveStatus());
        assertFalse(DataStorage.isSaveInFlight());
        assertEquals(Map.of("7:3", 10), rows(), "Journal failure must precede every SQL mutation");
        Files.delete(journal);
        DataStorage.saveAmountChange();
        assertTrue(queue.awaitDrained(5_000));
        assertEquals(Map.of("7:3", 42), rows());
    }

    @Test
    void actualSqliteRollbackRetainsAmountsForRetry() throws Exception {
        seed(3, 10);
        sql("CREATE TRIGGER reject_test_update BEFORE UPDATE ON ItemStored "
            + "WHEN NEW.ItemID = '3' BEGIN SELECT RAISE(ABORT, 'injected-test-write-failure'); END;");
        DataStorage.setStoredAmount(7, 3, 17);
        DataStorage.setStoredAmount(7, 4, 8);
        DataStorage.saveAmountChange();
        assertTrue(queue.awaitDrained(5_000));
        assertEquals("save-failed", DataStorage.getLastSaveStatus());
        assertEquals(Map.of("7:3", 10), rows(), "The real JDBC transaction must roll back all writes");
        assertEquals(2, DataStorage.getPendingAmountChangeCount());
        sql("DROP TRIGGER reject_test_update;");
        DataStorage.saveAmountChange();
        assertTrue(queue.awaitDrained(5_000));
        assertEquals(Map.of("7:3", 17, "7:4", 8), rows());
    }

    @Test
    void finalCheckpointAfterWorkerStopsOverridesOlderWorkerJournal() throws Exception {
        seed(3, 10);
        try (PausedWorker paused = pauseWorker()) {
            DataStorage.setStoredAmount(7, 3, 7);
            DataStorage.saveAmountChange();
            DataStorage.setStoredAmount(7, 3, 2);
            DataStorage.checkpointPendingChangesForShutdown();
            assertEquals(2, DrawerRecoveryJournal.read(source.getRecoveryJournalFile())
                .get(new DrawerRecoveryJournal.Key(7, 3)));
            paused.release.countDown();
            assertTrue(queue.awaitDrained(5_000));
            assertEquals(Map.of("7:3", 7), rows(), "Older worker is intentionally allowed to finish");
            assertFalse(Files.exists(source.getRecoveryJournalFile()), "Old worker cleaned its own journal value");
            assertTrue(queue.shutdown(5_000));
            DataStorage.checkpointPendingChangesForShutdown();
        }
        reopenAndReplay();
        assertEquals(Map.of("7:3", 2), rows());
    }

    @Test
    void shutdownSourceCheckpointsAfterStoppedWorkerAndBeforeClosingDatabase() throws Exception {
        String code = Files.readString(Path.of("src/main/java/io/github/sefiraat/networks/Networks.java"));
        int shutdown = code.indexOf("boolean drained = queryQueue.shutdown(timeoutMillis);");
        int stoppedGuard = code.indexOf("if (dataSource != null && databaseWorkerStopped)", shutdown);
        int close = code.indexOf("dataSource.close();", stoppedGuard);
        assertTrue(shutdown >= 0 && stoppedGuard > shutdown && close > stoppedGuard);
        assertTrue(code.substring(stoppedGuard, close).contains("DataStorage.checkpointPendingChangesForShutdown();"),
            "The lifecycle must use the final checkpoint; the helper alone cannot repair the race");
    }

    @Test
    void configuredDisabledJournalDoesNotPreventNormalSqlOrCreateAFile() throws Exception {
        config.set("database.recovery-journal", false);
        DataStorage.setStoredAmount(7, 3, 4);
        DataStorage.saveAmountChange();
        assertTrue(queue.awaitDrained(5_000));
        DataStorage.checkpointPendingChangesForShutdown();
        assertFalse(Files.exists(source.getRecoveryJournalFile()));
        assertEquals(Map.of("7:3", 4), rows());
    }

    @Test
    void idleSavesDoNotScheduleDatabaseWorkOrCreateJournal() {
        long scheduled = queue.snapshot().scheduled();
        for (int i = 0; i < 64; i++) DataStorage.saveAmountChange();
        assertEquals(scheduled, queue.snapshot().scheduled());
        assertFalse(DataStorage.isSaveInFlight());
        assertFalse(Files.exists(source.getRecoveryJournalFile()));
    }

    private void cancelAndCheckpoint() {
        DataStorage.checkpointPendingChangesForShutdown();
        assertFalse(queue.shutdown(0), "The paused worker forces deadline cancellation");
        assertFalse(queue.isWorkerRunning());
        assertEquals(0, queue.getTaskAmount());
        DataStorage.checkpointPendingChangesForShutdown();
    }

    private void openRuntime() throws Exception {
        queue = new QueryQueue();
        field("queryQueue").set(null, queue);
        source = new DataSource();
        field("dataSource").set(null, source);
        queue.startThread();
        assertTrue(queue.awaitDrained(5_000), "Initial counter writes complete before the scenario");
    }

    private void reopenAndReplay() throws Exception {
        if (queue.isWorkerRunning()) assertTrue(queue.shutdown(5_000));
        source.close();
        DataStorage.clearRuntimeCache();
        openRuntime();
        DataStorage.replayRecoveryJournal();
        assertTrue(queue.awaitDrained(5_000));
        assertFalse(DataStorage.isSaveInFlight());
        assertFalse(Files.exists(source.getRecoveryJournalFile()), "Successful replay removes committed entries");
        Map<String, Integer> firstReplay = rows();
        DataStorage.replayRecoveryJournal();
        assertTrue(queue.awaitDrained(5_000));
        assertEquals(firstReplay, rows(), "Replay is idempotent");
    }

    private PausedWorker pauseWorker() throws Exception {
        PausedWorker paused = new PausedWorker();
        queue.scheduleQuery(() -> {
            paused.entered.countDown();
            try {
                paused.release.await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return false;
        });
        assertTrue(paused.entered.await(5, TimeUnit.SECONDS), "Real worker reached the controlled pause");
        return paused;
    }

    private void seed(int itemId, int amount) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("INSERT INTO ItemStored (ContainerID, ItemID, Amount) VALUES (7, ?, ?)")) {
            statement.setInt(1, itemId);
            statement.setInt(2, amount);
            statement.executeUpdate();
        }
    }

    private Map<String, Integer> rows() throws Exception {
        Map<String, Integer> rows = new LinkedHashMap<>();
        try (Connection connection = connection();
             var statement = connection.createStatement();
             var results = statement.executeQuery("SELECT ContainerID, ItemID, Amount FROM ItemStored ORDER BY ContainerID, ItemID")) {
            while (results.next()) rows.put(results.getInt(1) + ":" + results.getInt(2), results.getInt(3));
        }
        return rows;
    }

    private void sql(String sql) throws Exception {
        try (Connection connection = connection(); var statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    private Connection connection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("CargoStorageUnits.db"));
    }

    private static Field field(String name) throws Exception {
        Field field = Networks.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static final class PausedWorker implements AutoCloseable {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        @Override
        public void close() { release.countDown(); }
    }
}

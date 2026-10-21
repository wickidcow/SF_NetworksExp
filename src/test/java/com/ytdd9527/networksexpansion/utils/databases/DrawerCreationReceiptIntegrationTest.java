package com.ytdd9527.networksexpansion.utils.databases;

import com.balugaq.netex.api.data.StorageUnitData;
import com.balugaq.netex.api.enums.StorageUnitType;
import com.ytdd9527.networksexpansion.core.services.LocalizationService;
import io.github.sefiraat.networks.Networks;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.Field;
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

/**
 * Test-first creation acceptance requirements. DataStorage, DataSource, QueryQueue,
 * StorageUnitData and SQLite-JDBC are production implementations; only the plugin
 * host/world are MockBukkit fixtures. No alternate creation algorithm is tested.
 *
 * Four requirements are expected to fail on f4ba6a5d until production creation is
 * acknowledged and reservations are restart-safe. Do not disable those tests or
 * invert the assertions to obtain green CI. Reopening is a clean connection/runtime
 * restart, not a process crash or native Paper/world-placement acceptance test.
 */
@Timeout(20)
class DrawerCreationReceiptIntegrationTest {
    @TempDir Path directory;
    private final Map<String, Object> originalGlobals = new LinkedHashMap<>();
    private QueryQueue queue;
    private DataSource source;
    private OfflinePlayer owner;
    private Location location;
    private StorageUnitType type;

    @BeforeAll
    static void initializePluginConstants() throws Exception {
        DrawerShutdownReceiptTest.initializePluginConstants();
    }

    @BeforeEach
    void setUp() throws Exception {
        var server = MockBukkit.mock();
        owner = server.addPlayer();
        location = new Location(server.addSimpleWorld("drawer_creation"), 12, 64, -7);
        type = StorageUnitType.values()[0];
        for (String name : new String[] {"instance", "dataSource", "queryQueue"}) {
            originalGlobals.put(name, field(name).get(null));
        }
        Networks plugin = mock(Networks.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.recovery-journal", true);
        config.set("database.startup-backups.enabled", false);
        Logger logger = Logger.getLogger("DrawerCreationReceiptIntegrationTest");
        logger.setLevel(Level.OFF);
        when(plugin.getName()).thenReturn("Networks");
        when(plugin.namespace()).thenReturn("networks");
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
                assertFalse(queue.isWorkerRunning(), "No database worker may escape the fixture");
            }
            if (source != null) source.close();
            DataStorage.clearRuntimeCache();
        } finally {
            for (var entry : originalGlobals.entrySet()) {
                field(entry.getKey()).set(null, entry.getValue());
            }
            MockBukkit.unmock();
        }
    }

    @Test
    void pendingCreationIsNotPublishedBeforeTheActualInsert() throws Exception {
        try (PausedWorker ignored = pauseWorker()) {
            StorageUnitData created = create();
            assertEquals(0, containerRows(created.getId()), "The actual INSERT is still queued");
            assertAll("Unverified creation must not be published as usable storage",
                () -> assertFalse(DataStorage.isContainerLoaded(created.getId()),
                    "LOADED cannot precede the database receipt"),
                () -> assertTrue(DataStorage.getCachedStorageData(created.getId()).isEmpty(),
                    "Public cache lookup must not expose unverified creation"));
        }
    }

    @Test
    void sqliteRejectedInsertCannotLeaveAUsableCachedDrawer() throws Exception {
        sql("CREATE TRIGGER reject_new_drawer BEFORE INSERT ON Container "
            + "BEGIN SELECT RAISE(ABORT, 'injected creation rejection'); END");
        StorageUnitData created = create();
        assertTrue(queue.awaitDrained(5_000));
        assertEquals(0, containerRows(created.getId()), "SQLite actually rejected the INSERT");
        assertAll("A rejected creation is not a loaded Drawer",
            () -> assertFalse(DataStorage.isContainerLoaded(created.getId())),
            () -> assertTrue(DataStorage.getCachedStorageData(created.getId()).isEmpty()));
    }

    @Test
    void cancelledCreationCannotRemainLoadedWhenItsInsertNeverRan() throws Exception {
        try (PausedWorker ignored = pauseWorker()) {
            StorageUnitData created = create();
            cancelPausedWorker();
            assertTrue(queue.snapshot().cancelled() >= 2,
                "Both counter persistence and container INSERT were unstarted");
            assertEquals(0, containerRows(created.getId()));
            assertAll("Cancellation must not authorize an absent container",
                () -> assertFalse(DataStorage.isContainerLoaded(created.getId())),
                () -> assertTrue(DataStorage.getCachedStorageData(created.getId()).isEmpty()));
        }
    }

    @Test
    void cancelledExposedIdMustNotBeReallocatedAfterCleanReopen() throws Exception {
        int exposedId;
        try (PausedWorker ignored = pauseWorker()) {
            exposedId = create().getId();
            cancelPausedWorker();
            assertEquals(0, containerRows(exposedId));
        }
        reopenRuntime();
        StorageUnitData next = create();
        assertTrue(queue.awaitDrained(5_000));
        assertEquals(1, containerRows(next.getId()), "The second creation completed normally");
        assertNotEquals(exposedId, next.getId(),
            "An ID already returned to placement callers must not be reassigned after cancellation");
    }

    @Test
    void healthyCreationPreservesItsExactRecordAndReloads() throws Exception {
        StorageUnitData created = create();
        int id = created.getId();
        assertTrue(queue.awaitDrained(5_000));
        assertRecord(id);
        assertTrue(DataStorage.isContainerLoaded(id));
        assertSame(created, DataStorage.getCachedStorageData(id).orElseThrow());
        reopenRuntime();
        DataStorage.requestStorageData(id);
        assertTrue(queue.awaitDrained(5_000));
        StorageUnitData restored = DataStorage.getCachedStorageData(id).orElseThrow();
        assertAll("Clean connection/runtime reopen preserves the container identity",
            () -> assertEquals(id, restored.getId()),
            () -> assertEquals(owner.getUniqueId(), restored.getOwner().getUniqueId()),
            () -> assertEquals(type, restored.getSizeType()),
            () -> assertTrue(restored.isPlaced()),
            () -> assertEquals(DataStorage.formatLocation(location),
                DataStorage.formatLocation(restored.getLastLocation())));
        assertRecord(id);
    }

    @Test
    void submissionToAStoppedQueueMustNotReturnACreatedDrawer() throws Exception {
        assertTrue(queue.shutdown(5_000));
        assertFalse(queue.isWorkerRunning());
        assertThrows(RuntimeException.class, this::create);
        assertEquals(0, allContainerRows());
    }

    private StorageUnitData create() {
        return DataStorage.createStorageUnitData(owner, type, location);
    }

    private void openRuntime() throws Exception {
        queue = new QueryQueue();
        field("queryQueue").set(null, queue);
        source = new DataSource();
        field("dataSource").set(null, source);
        queue.startThread();
        assertTrue(queue.awaitDrained(5_000), "Startup counters drain before the controlled scenario");
    }

    private void reopenRuntime() throws Exception {
        if (queue.isWorkerRunning()) assertTrue(queue.shutdown(5_000));
        assertFalse(queue.isWorkerRunning());
        source.close();
        DataStorage.clearRuntimeCache();
        openRuntime();
    }

    private void cancelPausedWorker() {
        assertFalse(queue.shutdown(0), "The held worker forces cancellation rather than normal drain");
        assertFalse(queue.isWorkerRunning());
        assertEquals(0, queue.getTaskAmount());
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
        assertTrue(paused.entered.await(5, TimeUnit.SECONDS));
        return paused;
    }

    private void assertRecord(int id) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("SELECT * FROM Container WHERE ContainerID = ?")) {
            statement.setInt(1, id);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(id, result.getInt("ContainerID"));
                assertEquals(owner.getUniqueId().toString(), result.getString("PlayerUUID"));
                assertEquals(type.ordinal(), result.getInt("SizeType"));
                assertEquals(1, result.getInt("IsPlaced"));
                assertEquals(DataStorage.formatLocation(location), result.getString("LastLocation"));
                assertFalse(result.next());
            }
        }
    }

    private int containerRows(int id) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("SELECT COUNT(*) FROM Container WHERE ContainerID = ?")) {
            statement.setInt(1, id);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                return result.getInt(1);
            }
        }
    }

    private int allContainerRows() throws Exception {
        try (Connection connection = connection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM Container")) {
            assertTrue(result.next());
            return result.getInt(1);
        }
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
        @Override public void close() { release.countDown(); }
    }
}

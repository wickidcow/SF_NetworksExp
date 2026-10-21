package com.ytdd9527.networksexpansion.utils.databases;

import com.balugaq.netex.api.data.ItemContainer;
import com.balugaq.netex.api.data.StorageUnitData;
import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.api.enums.StorageUnitType;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import com.ytdd9527.networksexpansion.core.services.LocalizationService;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.DriverManager;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Real DataSource registration, Bukkit object serialization and SQLite.
 * The plugin host and unrelated component matcher are isolated; serialization,
 * identity metadata and SQL remain real. Injected failures are not a live exploit.
 */
@Timeout(20)
class DrawerTemplateRegistrationTest {
    private static final NamespacedKey OWNER = new NamespacedKey("registration_test", "owner");
    private static final NamespacedKey SERIAL = new NamespacedKey("registration_test", "serial");
    @TempDir Path directory;
    private final Map<String, Object> originalGlobals = new LinkedHashMap<>();
    private QueryQueue queue;
    private DataSource source;
    private MockedStatic<StackUtils> comparison;

    @BeforeAll
    static void initializeConstants() throws Exception {
        DrawerShutdownReceiptTest.initializePluginConstants();
        try (var networks = mockStatic(Networks.class)) {
            Networks plugin = mock(Networks.class);
            ConfigManager config = mock(ConfigManager.class);
            networks.when(Networks::getInstance).thenReturn(plugin);
            networks.when(Networks::getConfigManager).thenReturn(config);
            when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
            Class.forName("io.github.sefiraat.networks.utils.StackUtils");
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        for (String name : new String[] {"instance", "dataSource", "queryQueue"}) {
            originalGlobals.put(name, field(Networks.class, name).get(null));
        }
        Networks plugin = mock(Networks.class);
        YamlConfiguration config = new YamlConfiguration();
        config.set("database.recovery-journal", true);
        config.set("database.startup-backups.enabled", false);
        Logger logger = Logger.getLogger("DrawerTemplateRegistrationTest");
        logger.setLevel(Level.OFF);
        when(plugin.getName()).thenReturn("Networks");
        when(plugin.namespace()).thenReturn("networks");
        when(plugin.getConfig()).thenReturn(config);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(logger);
        ConfigManager manager = mock(ConfigManager.class);
        when(manager.useBukkitItemComparison()).thenReturn(false);
        field(Networks.class, "configManager").set(plugin, manager);
        LocalizationService localization = mock(LocalizationService.class);
        when(localization.getString(anyString())).thenAnswer(call -> call.getArgument(0));
        field(Networks.class, "localizationService").set(plugin, localization);
        field(Networks.class, "instance").set(null, plugin);
        DataStorage.clearRuntimeCache();
        // MockBukkit does not implement matchesWithoutData; ItemStackWrapper's
        // inherited native comparison also lacks its cached metadata. Isolate
        // registration from that unrelated matcher, using real Bukkit metadata
        // on a materialized stack. Never stub registration, serialization or SQL.
        comparison = mockStatic(StackUtils.class, CALLS_REAL_METHODS);
        comparison.when(() -> StackUtils.itemsMatch(any(ItemStack.class), any(ItemStack.class)))
            .thenAnswer(call -> {
                ItemStack first = call.getArgument(0);
                ItemStack wrapped = call.getArgument(1);
                ItemStack materialized = new ItemStack(wrapped.getType());
                materialized.setItemMeta(wrapped.getItemMeta());
                return first.isSimilar(materialized);
            });
        queue = new QueryQueue();
        field(Networks.class, "queryQueue").set(null, queue);
        source = new DataSource();
        field(Networks.class, "dataSource").set(null, source);
        queue.startThread();
        assertTrue(queue.awaitDrained(5_000));
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
            if (comparison != null) comparison.close();
            for (var entry : originalGlobals.entrySet()) {
                field(Networks.class, entry.getKey()).set(null, entry.getValue());
            }
            MockBukkit.unmock();
        }
    }

    @Test
    void healthyRegistrationPreservesSerializedIdentityAndQuantity() throws Exception {
        ItemStack original = token("A", 13);
        ItemStack before = original.clone();
        int id = source.getItemId(original);
        assertTrue(queue.awaitDrained(5_000));
        assertEquals(before, payload(id));
        assertEquals(before, original);
        assertEquals("A", payload(id).getItemMeta().getPersistentDataContainer().get(OWNER, PersistentDataType.STRING));
        assertEquals(Long.valueOf(123456789L), payload(id).getItemMeta().getPersistentDataContainer().get(SERIAL, PersistentDataType.LONG));
        assertEquals(1, templateRows());
    }

    @Test
    void runtimeSerializationFailurePublishesNothing() throws Exception { assertRejectedBeforePublication(Fault.RUNTIME); }

    @Test
    void checkedSerializationFailurePublishesNothing() throws Exception { assertRejectedBeforePublication(Fault.IO); }

    @Test
    void runtimeSerializationFailureCannotConsumePhysicalDeposit() throws Exception { assertDepositNotConsumed(Fault.RUNTIME); }

    @Test
    void checkedSerializationFailureCannotConsumePhysicalDeposit() throws Exception { assertDepositNotConsumed(Fault.IO); }

    @Test
    void failedRegistrationCanRetryWithoutPhantomIdentity() throws Exception {
        int next = nextId();
        ItemStack healthy = token("A", 11);
        assertThrows(RuntimeException.class, () -> source.getItemId(new ObservedStack(healthy, Fault.RUNTIME)));
        assertEquals(next, source.getItemId(healthy));
        assertTrue(queue.awaitDrained(5_000));
        assertEquals(1, templates().size());
        assertEquals(1, templateRows());
        assertEquals(healthy, payload(next));
    }

    @Test
    void existingTypeDoesNotSerializeOrScheduleAgain() throws Exception {
        ItemStack original = token("A", 7);
        int id = source.getItemId(original);
        assertTrue(queue.awaitDrained(5_000));
        long scheduled = queue.snapshot().scheduled();
        ObservedStack sameType = new ObservedStack(original, Fault.RUNTIME);
        assertEquals(id, source.getItemId(sameType));
        assertEquals(0, sameType.serializations.get());
        assertEquals(scheduled, queue.snapshot().scheduled(), "No per-deposit serialization or database task for known identity");
    }

    @Test
    void acceptedRegistrationOwnsSnapshotEvenBeforeWorkerRuns() throws Exception {
        ItemStack original = token("A", 17);
        ItemStack expected = original.clone();
        int id;
        try (PausedWorker paused = pauseWorker()) {
            id = source.getItemId(original);
            original.setAmount(1);
            original.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "B"));
            paused.release.countDown();
            assertTrue(queue.awaitDrained(5_000));
        }
        assertEquals(expected, payload(id));
        assertEquals(expected, templates().get(id));
    }

    @Test
    void rejectedQueueDoesNotPublishAUsableTemplate() throws Exception {
        assertTrue(queue.shutdown(5_000));
        ItemStack original = token("A", 9);
        ItemStack before = original.clone();
        assertThrows(IllegalStateException.class, () -> source.getItemId(original));
        assertEquals(Map.of(), templates(), "An unqueued identity must not be discoverable as a completed registration");
        assertEquals(0, templateRows());
        assertEquals(before, original);
    }

    @Test
    void newTemplateIsSerializedOnceOnRegisteringThreadBeforePublication() throws Exception {
        ObservedStack observed = new ObservedStack(token("A", 5), Fault.NONE);
        int id;
        String caller = Thread.currentThread().getName();
        try (PausedWorker paused = pauseWorker()) {
            id = source.getItemId(observed);
            assertEquals(1, observed.serializations.get(), "Preparation cannot be deferred until after registration returns");
            assertEquals(List.of(caller), observed.threads);
            paused.release.countDown();
            assertTrue(queue.awaitDrained(5_000));
        }
        assertEquals(1, observed.serializations.get(), "The database worker must reuse the prepared payload");
        assertEquals(token("A", 5), payload(id));
    }

    @Test
    void differentTypedOwnerPdcCannotShareItemId() throws Exception {
        ItemStack a = token("A", 5);
        ItemStack b = token("B", 5);
        int aid = source.getItemId(a);
        int bid = source.getItemId(b);
        assertNotEquals(aid, bid);
        assertTrue(queue.awaitDrained(5_000));
        assertEquals(a, payload(aid));
        assertEquals(b, payload(bid));
        assertEquals(2, templateRows());
    }

    @Test
    void cloneFailureRemainsBeforeAnyRegistrationSideEffect() throws Exception {
        ItemStack failing = mock(ItemStack.class);
        when(failing.clone()).thenThrow(new IllegalStateException("injected clone failure"));
        int before = nextId();
        long scheduled = queue.snapshot().scheduled();
        assertThrows(IllegalStateException.class, () -> source.getItemId(failing));
        assertEquals(before, nextId());
        assertEquals(Map.of(), templates());
        assertEquals(scheduled, queue.snapshot().scheduled());
    }

    @Test
    void faultHarnessRetainsItsSerializerAcrossTheProductionClone() {
        ObservedStack observed = new ObservedStack(token("A", 5), Fault.RUNTIME);
        ObservedStack cloned = observed.clone();
        assertNotSame(observed, cloned);
        assertEquals(observed.getItemMeta(), cloned.getItemMeta());
        assertThrows(IllegalStateException.class, cloned::serialize);
        assertEquals(1, observed.serializations.get(), "Injected callback must actually be reached");
    }

    private void assertRejectedBeforePublication(Fault fault) throws Exception {
        ObservedStack input = new ObservedStack(token("A", 13), fault);
        int before = nextId();
        long scheduled = queue.snapshot().scheduled();
        RuntimeException failure = null;
        try { source.getItemId(input); } catch (RuntimeException expected) { failure = expected; }
        RuntimeException observedFailure = failure;
        assertAll(
            () -> assertNotNull(observedFailure, "Serialization failure must be visible before a caller can credit stock"),
            () -> assertEquals(1, input.serializations.get(), "The serializer fault must have been reached"),
            () -> assertEquals(before, nextId()),
            () -> assertEquals(Map.of(), templates()),
            () -> assertEquals(scheduled, queue.snapshot().scheduled()),
            () -> assertEquals(13, input.getAmount()));
    }

    private void assertDepositNotConsumed(Fault fault) throws Exception {
        ObservedStack input = new ObservedStack(token("A", 13), fault);
        var items = new ConcurrentHashMap<Integer, ItemContainer>();
        Location location = new Location(MockBukkit.getMock().addSimpleWorld("registration"), 1, 64, 1);
        StorageUnitData drawer = new StorageUnitData(7, mock(OfflinePlayer.class), StorageUnitType.TINY,
            true, location, items);
        RuntimeException failure = null;
        try (var slimefun = mockStatic(SlimefunItem.class)) {
            try { drawer.depositItemStack0(location, input, false); }
            catch (RuntimeException expected) { failure = expected; }
        }
        RuntimeException observedFailure = failure;
        assertAll(
            () -> assertNotNull(observedFailure),
            () -> assertEquals(1, input.serializations.get(), "The serializer fault must have been reached"),
            () -> assertEquals(13, input.getAmount(), "Physical input must remain with its original owner"),
            () -> assertTrue(drawer.getStoredItemsMap().isEmpty(), "No orphan balance may be accepted"),
            () -> assertEquals(Map.of(), templates()));
    }

    private ItemStack payload(int id) throws Exception {
        try (Connection connection = connection();
             var statement = connection.prepareStatement("SELECT Item FROM ItemStack WHERE ItemID = ?")) {
            statement.setInt(1, id);
            try (var result = statement.executeQuery()) {
                assertTrue(result.next(), "The exact registered identity must reach SQLite");
                return DataSource.getItemStack(result.getString(1));
            }
        }
    }

    private int templateRows() throws Exception {
        try (Connection connection = connection(); var statement = connection.createStatement();
             var result = statement.executeQuery("SELECT COUNT(*) FROM ItemStack")) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<Integer, ItemStack> templates() throws Exception {
        return (Map<Integer, ItemStack>) field(DataSource.class, "itemMap").get(source);
    }

    private int nextId() throws Exception { return field(DataSource.class, "nextItemId").getInt(source); }

    private Connection connection() throws Exception {
        return DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("CargoStorageUnits.db"));
    }

    private static Field field(Class<?> type, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static ItemStack token(String owner, int amount) {
        ItemStack item = new ItemStack(Material.DIAMOND, amount);
        item.editMeta(meta -> {
            meta.displayName(Component.text("registration-" + owner));
            meta.lore(List.of(Component.text("preserve lore")));
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, owner);
            meta.getPersistentDataContainer().set(SERIAL, PersistentDataType.LONG, 123456789L);
        });
        return item;
    }

    private PausedWorker pauseWorker() throws Exception {
        PausedWorker paused = new PausedWorker();
        queue.scheduleQuery(() -> {
            paused.entered.countDown();
            try { paused.release.await(); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
            return false;
        });
        assertTrue(paused.entered.await(5, TimeUnit.SECONDS));
        return paused;
    }

    private enum Fault { NONE, RUNTIME, IO }

    @DelegateDeserialization(ItemStack.class)
    public static final class ObservedStack extends ItemStack {
        private final Fault fault;
        final AtomicInteger serializations;
        final List<String> threads;
        ObservedStack(ItemStack item, Fault fault) {
            this(item, fault, new AtomicInteger(), Collections.synchronizedList(new ArrayList<>()));
        }
        private ObservedStack(ItemStack item, Fault fault, AtomicInteger serializations, List<String> threads) {
            super(item);
            this.fault = fault;
            this.serializations = serializations;
            this.threads = threads;
        }
        @Override
        public ObservedStack clone() {
            // Paper delegates clone() to the underlying stack, discarding arbitrary
            // subclasses. Retain only this explicit fault injection across that copy.
            return new ObservedStack(super.clone(), fault, serializations, threads);
        }
        @Override
        public Map<String, Object> serialize() {
            serializations.incrementAndGet();
            threads.add(Thread.currentThread().getName());
            if (fault == Fault.RUNTIME) throw new IllegalStateException("injected template serialization failure");
            Map<String, Object> values = new LinkedHashMap<>(super.serialize());
            if (fault == Fault.IO) values.put("injected-nonserializable-payload", new Object());
            return values;
        }
    }

    private static final class PausedWorker implements AutoCloseable {
        final CountDownLatch entered = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        @Override public void close() { release.countDown(); }
    }
}

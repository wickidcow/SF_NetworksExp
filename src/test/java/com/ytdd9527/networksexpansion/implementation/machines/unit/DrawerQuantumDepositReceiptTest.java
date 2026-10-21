package com.ytdd9527.networksexpansion.implementation.machines.unit;

import com.balugaq.netex.api.data.ItemContainer;
import com.balugaq.netex.api.data.StorageUnitData;
import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.api.enums.QuickTransferMode;
import com.balugaq.netex.api.enums.StorageUnitType;
import com.balugaq.netex.utils.Lang;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import com.ytdd9527.networksexpansion.utils.databases.DataStorage;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentQuantumStorageType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Executes the actual private Quantum GUI handler with real Drawer state and typed item PDC.
 * Menu access and plugin services are substituted; the transfer, codec and quantities are production code.
 */
class DrawerQuantumDepositReceiptTest {
    private static final long STOCK = 3_000_000_000L;
    private static final long LIMIT = 4_000_000_000L;
    private static final int DRAWER_AMOUNT = 212;
    private static final NamespacedKey OWNER = new NamespacedKey("quantum_receipt", "owner");
    private final Location location = new Location(null, 9, 64, 9);
    private MockedStatic<Networks> networks;
    private MockedStatic<SupportedPluginManager> integrations;
    private MockedStatic<Lang> language;
    private MockedStatic<SlimefunItem> slimefun;
    private MockedStatic<Slimefun> core;
    private MockedStatic<DataStorage> persistence;
    private MockedStatic<StorageCacheUtils> storageCache;
    private StorageUnitData drawer;
    private ItemContainer stored;
    private ItemStack template;
    private AtomicReference<ItemStack> source;
    private BlockMenu menu;
    private Player player;
    private MethodHandle quickTransfer;
    private boolean snapshotReads;
    private boolean refuseWrites;
    private boolean throwOnWrite;
    private Runnable duringWrite;
    private final AtomicInteger writes = new AtomicInteger();

    @BeforeEach
    void prepare() throws Exception {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        integrations = mockStatic(SupportedPluginManager.class);
        Networks plugin = mock(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        SupportedPluginManager supported = mock(SupportedPluginManager.class);
        networks.when(Networks::getInstance).thenReturn(plugin);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getSupportedPluginManager).thenReturn(supported);
        integrations.when(SupportedPluginManager::getInstance).thenReturn(supported);
        when(plugin.getName()).thenReturn("Networks");
        when(plugin.namespace()).thenReturn("networks");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("quantum-receipt"));
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        language = mockStatic(Lang.class, call -> switch (call.getMethod().getName()) {
            case "getString" -> (String) call.getArgument(0);
            case "getStringArray" -> new String[0];
            case "getIcon" -> new ItemStack((Material) call.getArgument(1));
            default -> RETURNS_DEFAULTS.answer(call);
        });
        Class.forName("io.github.sefiraat.networks.utils.StackUtils");
        Class.forName("io.github.sefiraat.networks.network.NetworkRoot");
        Class.forName("com.ytdd9527.networksexpansion.implementation.machines.unit.NetworksDrawer");
        core = mockStatic(Slimefun.class, RETURNS_DEEP_STUBS);
        NetworkQuantumStorage type = mock(NetworkQuantumStorage.class);
        when(type.getMaxAmount()).thenReturn(LIMIT);
        var baseQuantum = new io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack(
            "QUANTUM_TEST", Material.CHEST, "test");
        when(type.getItem()).thenReturn(baseQuantum);
        slimefun = mockStatic(SlimefunItem.class);
        slimefun.when(() -> SlimefunItem.getByItem(any(ItemStack.class))).thenAnswer(call ->
            ((ItemStack) call.getArgument(0)).getType() == Material.CHEST ? type : null);
        persistence = mockStatic(DataStorage.class);
        template = new ItemStack(Material.DIAMOND);
        template.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "A"));
        stored = new ItemContainer(3, template, DRAWER_AMOUNT);
        var entries = new ConcurrentHashMap<Integer, ItemContainer>();
        entries.put(3, stored);
        drawer = new StorageUnitData(7, mock(OfflinePlayer.class), StorageUnitType.TINY, true, location, entries);
        map("storages").put(location, drawer);
        map("quickTransferModes").put(location, QuickTransferMode.FROM_QUANTUM);
        ItemStack quantum = new ItemStack(Material.CHEST);
        ItemMeta meta = quantum.getItemMeta();
        meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "container-owner");
        QuantumCache cache = new QuantumCache(template.clone(), STOCK, LIMIT, true, true);
        DataTypeMethods.setCustom(meta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE, cache);
        quantum.setItemMeta(meta);
        source = new AtomicReference<>(quantum);
        menu = mock(BlockMenu.class);
        storageCache = mockStatic(StorageCacheUtils.class);
        storageCache.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(menu);
        when(menu.getLocation()).thenReturn(location);
        when(menu.getItemInSlot(9)).thenAnswer(call -> snapshotReads ? source.get().clone() : source.get());
        when(menu.getItemInSlot(27)).thenAnswer(call -> template.clone());
        doAnswer(call -> {
            writes.incrementAndGet();
            if (duringWrite != null) duringWrite.run();
            if (!refuseWrites) source.set(((ItemStack) call.getArgument(1)).clone());
            if (throwOnWrite) throw new IllegalStateException("Controlled authoritative setter failure");
            return null;
        }).when(menu).replaceExistingItem(eq(9), any(ItemStack.class));
        player = mock(Player.class);
        quickTransfer = MethodHandles.privateLookupIn(NetworksDrawer.class, MethodHandles.lookup())
            .findStatic(NetworksDrawer.class, "quickTransfer", MethodType.methodType(void.class,
                BlockMenu.class, Location.class, Player.class));
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            map("storages").remove(location);
            map("quickTransferModes").remove(location);
        } finally {
            if (storageCache != null) storageCache.close();
            if (persistence != null) persistence.close();
            if (slimefun != null) slimefun.close();
            if (core != null) core.close();
            if (language != null) language.close();
            if (integrations != null) integrations.close();
            if (networks != null) networks.close();
            MockBukkit.unmock();
        }
    }

    @Test
    void realLongAndPdcLedgerIsConservedForTheNormalLiveSlot() throws Throwable {
        transfer();
        assertEquals(256, stored.getAmount());
        assertEquals(STOCK - 44, remaining());
        assertLedger(0);
        QuantumCache after = Keys.getQuantumCache(source.get().getItemMeta());
        assertEquals(LIMIT, after.getLimitLong());
        assertTrue(after.isVoidExcess());
        assertTrue(after.supportsCustomMaxAmount());
        assertTrue(template.isSimilar(after.getItemStack()));
        assertEquals("container-owner", source.get().getItemMeta().getPersistentDataContainer()
            .get(OWNER, PersistentDataType.STRING));
    }

    @Test
    void clonedMenuReadCannotCreditDrawerWithoutPersistedQuantumDebit() throws Throwable {
        snapshotReads = true;
        transfer();
        assertLedger(0);
        assertEquals(256, stored.getAmount());
        assertEquals(STOCK - 44, remaining());
    }

    @Test
    void refusedAuthoritativeSourceWriteCannotCreditTheDrawer() throws Throwable {
        snapshotReads = true;
        refuseWrites = true;
        transfer();
        assertEquals(DRAWER_AMOUNT, stored.getAmount());
        assertEquals(STOCK, remaining());
        assertLedger(0);
    }

    @ParameterizedTest
    @ValueSource(ints = {37, 44})
    void preparationCapacityChangeCannotLoseDebitedQuantumStock(int independentlyAdded) throws Throwable {
        AtomicInteger callbacks = new AtomicInteger();
        source.set(new CallbackStack(source.get(), () -> {
            if (callbacks.incrementAndGet() == 1) stored.addAmount(independentlyAdded);
        }));
        transfer();
        assertTrue(callbacks.get() >= 1, "The controlled preparation callback must actually execute");
        assertLedger(independentlyAdded);
    }

    @Test
    void authoritativeSetterFailureBeforeMutationLeavesAllStockAtSource() throws Throwable {
        snapshotReads = true;
        refuseWrites = true;
        throwOnWrite = true;
        transfer();
        assertEquals(1, writes.get(), "The failing authoritative setter must be reached");
        assertEquals(DRAWER_AMOUNT, stored.getAmount());
        assertEquals(STOCK, remaining());
        assertFalse(drawer.hasUnverifiedSourceTransfer());
        assertLedger(0);
    }

    @Test
    void authoritativeSetterFailureAfterMutationStillUsesItsExactLiveReceipt() throws Throwable {
        snapshotReads = true;
        throwOnWrite = true;
        transfer();
        assertEquals(1, writes.get(), "The failing authoritative setter must be reached");
        assertEquals(256, stored.getAmount());
        assertEquals(STOCK - 44, remaining());
        assertFalse(drawer.hasUnverifiedSourceTransfer());
        assertLedger(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void authoritativeSetterCannotRewriteTheExpectedContainerIdentity(boolean toQuantum) throws Throwable {
        if (toQuantum) map("quickTransferModes").put(location, QuickTransferMode.TO_QUANTUM);
        doAnswer(call -> {
            ItemStack supplied = call.getArgument(1);
            ItemMeta meta = supplied.getItemMeta();
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "setter-changed-owner");
            supplied.setItemMeta(meta);
            source.set(supplied);
            return null;
        }).when(menu).replaceExistingItem(eq(9), any(ItemStack.class));
        try { transfer(); } catch (IllegalStateException expected) { }
        assertEquals(DRAWER_AMOUNT, stored.getAmount(),
            "The prepared balance alone cannot acknowledge a changed physical container identity");
        assertTrue(drawer.hasUnverifiedSourceTransfer(), "An unknown identity write must hold the Drawer");
    }

    @Test
    void sourceWriterCannotReenterDestinationMutations() throws Throwable {
        AtomicInteger callbacks = new AtomicInteger();
        duringWrite = () -> {
            callbacks.incrementAndGet();
            assertNull(drawer.requestItem0(location, new ItemRequest(template.clone(), 17), false));
            ItemStack offered = template.clone();
            offered.setAmount(7);
            drawer.depositItemStack0(location, offered, true);
            assertEquals(7, offered.getAmount(), "Refused reentrant input must remain with its owner");
            drawer.setItemAmount(3, 0);
            drawer.removeAmount(3, 5);
            drawer.removeItem(3);
            assertSame(stored, drawer.getStoredItemsMap().get(3));
            assertEquals(DRAWER_AMOUNT, stored.getAmount());
        };
        transfer();
        assertEquals(1, callbacks.get());
        assertEquals(256, stored.getAmount());
        assertLedger(0);
    }

    @Test
    void staleMenuCannotDebitQuantumOrCreditItsFormerDrawer() throws Throwable {
        BlockMenu replacement = mock(BlockMenu.class);
        storageCache.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(replacement);
        transfer();
        assertEquals(0, writes.get());
        assertEquals(DRAWER_AMOUNT, stored.getAmount());
        assertEquals(STOCK, remaining());
        assertLedger(0);
    }

    @Test
    void completeDrainCreditsExactSmallBalanceAndPreservesContainerIdentity() throws Throwable {
        ItemMeta meta = source.get().getItemMeta();
        QuantumCache before = Keys.getQuantumCache(meta);
        DataTypeMethods.setCustom(meta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE,
            DrawerQuantumTransferMath.rebalanced(before, 13));
        source.get().setItemMeta(meta);
        snapshotReads = true;
        transfer();
        assertEquals(1, writes.get());
        assertEquals(DRAWER_AMOUNT + 13, stored.getAmount());
        assertEquals(0, remaining());
        QuantumCache empty = Keys.getQuantumCache(source.get().getItemMeta());
        assertNotNull(empty);
        assertEquals(0, empty.getAmountLong());
        assertEquals(LIMIT, empty.getLimitLong());
        assertTrue(empty.isVoidExcess());
        assertTrue(empty.supportsCustomMaxAmount());
        assertEquals(template, empty.getItemStack());
        assertEquals("container-owner", source.get().getItemMeta().getPersistentDataContainer()
            .get(OWNER, PersistentDataType.STRING));
    }

    @Test
    void toQuantumLiveSlotHealthyControlPreservesTheLongLedger() throws Throwable {
        map("quickTransferModes").put(location, QuickTransferMode.TO_QUANTUM);
        transfer();
        assertEquals(0, stored.getAmount());
        assertEquals(STOCK + DRAWER_AMOUNT, remaining());
        assertLedger(0);
    }

    @Test
    void toQuantumSnapshotReadMustNotLoseWithdrawnDrawerStock() throws Throwable {
        map("quickTransferModes").put(location, QuickTransferMode.TO_QUANTUM);
        snapshotReads = true;
        transfer();
        assertLedger(0);
        assertEquals(0, stored.getAmount());
        assertEquals(STOCK + DRAWER_AMOUNT, remaining());
    }

    @Test
    void toQuantumRejectedMetadataMustLeaveDrawerStockUntouched() throws Throwable {
        map("quickTransferModes").put(location, QuickTransferMode.TO_QUANTUM);
        AtomicInteger attempts = new AtomicInteger();
        source.set(new RefusingMetaStack(source.get(), attempts));
        transfer();
        assertTrue(attempts.get() > 0, "The refusing metadata writer must actually execute");
        assertEquals(DRAWER_AMOUNT, stored.getAmount());
        assertEquals(STOCK, remaining());
        assertLedger(0);
    }

    @Test
    void toQuantumRefusedAuthoritativeWriteMustLeaveDrawerStockUntouched() throws Throwable {
        map("quickTransferModes").put(location, QuickTransferMode.TO_QUANTUM);
        snapshotReads = true;
        refuseWrites = true;
        transfer();
        assertEquals(DRAWER_AMOUNT, stored.getAmount());
        assertEquals(STOCK, remaining());
        assertLedger(0);
    }

    @Test
    void toQuantumFirstAssignmentPreservesContainerPdcAndUsesTheExactDrawerTemplate() throws Throwable {
        map("quickTransferModes").put(location, QuickTransferMode.TO_QUANTUM);
        ItemMeta meta = source.get().getItemMeta();
        meta.getPersistentDataContainer().remove(Keys.QUANTUM_STORAGE_INSTANCE);
        source.get().setItemMeta(meta);
        assertNull(Keys.getQuantumCache(source.get().getItemMeta()));
        snapshotReads = true;
        transfer();
        assertEquals(0, stored.getAmount());
        assertFalse(drawer.getStoredItemsMap().containsKey(3), "First assignment retains existing zero-entry removal");
        QuantumCache assigned = Keys.getQuantumCache(source.get().getItemMeta());
        assertNotNull(assigned);
        assertEquals(DRAWER_AMOUNT, assigned.getAmountLong());
        assertEquals(LIMIT, assigned.getLimitLong());
        assertEquals(template, assigned.getItemStack());
        assertEquals("container-owner", source.get().getItemMeta().getPersistentDataContainer()
            .get(OWNER, PersistentDataType.STRING));
    }

    @Test
    void toQuantumSetterFailureAfterMutationStillDebitsExactlyTheVerifiedCredit() throws Throwable {
        map("quickTransferModes").put(location, QuickTransferMode.TO_QUANTUM);
        snapshotReads = true;
        throwOnWrite = true;
        transfer();
        assertEquals(1, writes.get());
        assertEquals(0, stored.getAmount());
        assertEquals(STOCK + DRAWER_AMOUNT, remaining());
        assertFalse(drawer.hasUnverifiedSourceTransfer());
        assertLedger(0);
    }

    @Test
    void toQuantumSetterFailureBeforeMutationKeepsEveryItemInTheDrawer() throws Throwable {
        map("quickTransferModes").put(location, QuickTransferMode.TO_QUANTUM);
        snapshotReads = true;
        refuseWrites = true;
        throwOnWrite = true;
        transfer();
        assertEquals(1, writes.get());
        assertEquals(DRAWER_AMOUNT, stored.getAmount());
        assertEquals(STOCK, remaining());
        assertFalse(drawer.hasUnverifiedSourceTransfer());
        assertLedger(0);
    }

    @Test
    void toQuantumNearLongMaximumTransfersOnlyItsSevenRemainingPlaces() throws Throwable {
        map("quickTransferModes").put(location, QuickTransferMode.TO_QUANTUM);
        ItemMeta meta = source.get().getItemMeta();
        DataTypeMethods.setCustom(meta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE,
            new QuantumCache(template.clone(), Long.MAX_VALUE - 7, Long.MAX_VALUE, true, true));
        source.get().setItemMeta(meta);
        snapshotReads = true;
        transfer();
        assertEquals(Long.MAX_VALUE, remaining());
        assertEquals(DRAWER_AMOUNT - 7, stored.getAmount());
        QuantumCache after = Keys.getQuantumCache(source.get().getItemMeta());
        assertEquals(Long.MAX_VALUE, after.getLimitLong());
        assertTrue(after.isVoidExcess());
        assertTrue(after.supportsCustomMaxAmount());
        assertEquals(template, after.getItemStack());
    }

    private void transfer() throws Throwable { quickTransfer.invokeExact(menu, location, player); }

    private long remaining() {
        QuantumCache cache = Keys.getQuantumCache(source.get().getItemMeta());
        return cache == null ? 0 : cache.getAmountLong();
    }

    private void assertLedger(int independentlyAdded) {
        assertEquals(STOCK + DRAWER_AMOUNT + independentlyAdded, remaining() + stored.getAmount(),
            "Every item must stay in Quantum or the Drawer; unrelated supplied items are counted separately");
    }

    @SuppressWarnings("unchecked")
    private static Map<Location, Object> map(String name) throws Exception {
        Field field = NetworksDrawer.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Map<Location, Object>) field.get(null);
    }

    static final class RefusingMetaStack extends ItemStack {
        private final AtomicInteger attempts;
        RefusingMetaStack(ItemStack original, AtomicInteger attempts) { super(original); this.attempts = attempts; }
        @Override public RefusingMetaStack clone() { return new RefusingMetaStack(super.clone(), attempts); }
        @Override public boolean setItemMeta(ItemMeta meta) {
            if (attempts != null) attempts.incrementAndGet();
            return false;
        }
    }

    static final class CallbackStack extends ItemStack {
        private final Runnable callback;
        CallbackStack(ItemStack original, Runnable callback) { super(original); this.callback = callback; }
        @Override public CallbackStack clone() { return new CallbackStack(super.clone(), callback); }
        @Override public boolean setItemMeta(ItemMeta meta) {
            if (callback != null) callback.run();
            return super.setItemMeta(meta);
        }
    }
}

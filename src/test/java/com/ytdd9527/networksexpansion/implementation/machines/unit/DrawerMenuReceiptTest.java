package com.ytdd9527.networksexpansion.implementation.machines.unit;

import com.balugaq.netex.api.data.ItemContainer;
import com.balugaq.netex.api.data.StorageUnitData;
import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.api.enums.StorageUnitType;
import com.balugaq.netex.api.helpers.Icon;
import com.balugaq.netex.utils.Lang;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import com.ytdd9527.networksexpansion.utils.databases.DataStorage;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.utils.NetworkUtils;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** The installed production menu handler, real drawer quantities and Bukkit PDC.
 * Only host/menu/inventory boundaries are controlled to emulate snapshots and failed writes.
 */
class DrawerMenuReceiptTest {
    private static final NamespacedKey OWNER = new NamespacedKey("drawer_menu", "owner");
    private final Location location = new Location(null, 12, 64, 12);
    private final List<MockedStatic<?>> mocks = new ArrayList<>();
    private final AtomicReference<ChestMenu.MenuClickHandler> handler = new AtomicReference<>();
    private final AtomicReference<ItemStack> cursor = new AtomicReference<>();
    private ItemStack[] inventory;
    private ItemStack template;
    private StorageUnitData drawer;
    private Player player;
    private PlayerInventory playerInventory;
    private BlockMenu menu;
    private MockedStatic<StorageCacheUtils> storage;
    private boolean refuseWrites;
    private boolean throwAfterWrite;

    @BeforeEach
    void setup() throws Exception {
        MockBukkit.mock();
        var networks = keep(mockStatic(Networks.class));
        var integrations = keep(mockStatic(SupportedPluginManager.class));
        Networks plugin = mock(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        SupportedPluginManager supported = mock(SupportedPluginManager.class);
        networks.when(Networks::getInstance).thenReturn(plugin);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getSupportedPluginManager).thenReturn(supported);
        integrations.when(SupportedPluginManager::getInstance).thenReturn(supported);
        when(plugin.getName()).thenReturn("Networks");
        when(plugin.namespace()).thenReturn("networks");
        when(plugin.getLogger()).thenReturn(Logger.getLogger("drawer-menu-receipt"));
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        keep(mockStatic(Lang.class, call -> switch (call.getMethod().getName()) {
            case "getString" -> (String) call.getArgument(0);
            case "getStringArray" -> new String[0];
            case "getIcon" -> new ItemStack((Material) call.getArgument(1));
            default -> RETURNS_DEFAULTS.answer(call);
        }));
        Class.forName("io.github.sefiraat.networks.utils.StackUtils");
        Class.forName("io.github.sefiraat.networks.network.NetworkRoot");
        Class.forName(NetworksDrawer.class.getName());
        keep(mockStatic(Slimefun.class, RETURNS_DEEP_STUBS));
        keep(mockStatic(SlimefunItem.class));
        keep(mockStatic(NetworkUtils.class));
        var persistence = keep(mockStatic(DataStorage.class));
        persistence.when(() -> DataStorage.getItemId(any(ItemStack.class))).thenReturn(3);
        template = new ItemStack(Material.DIAMOND);
        template.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "A"));
        var entries = new ConcurrentHashMap<Integer, ItemContainer>();
        entries.put(3, new ItemContainer(3, template, 13));
        drawer = new StorageUnitData(7, mock(OfflinePlayer.class), StorageUnitType.TINY, true, location, entries);
        map().put(location, drawer);
        menu = mock(BlockMenu.class);
        when(menu.getLocation()).thenReturn(location);
        doAnswer(call -> { handler.set(call.getArgument(1)); return null; })
            .when(menu).addMenuClickHandler(eq(10), any(ChestMenu.MenuClickHandler.class));
        storage = keep(mockStatic(StorageCacheUtils.class));
        storage.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(menu);
        inventory = new ItemStack[36];
        player = mock(Player.class);
        playerInventory = mock(PlayerInventory.class);
        when(player.getInventory()).thenReturn(playerInventory);
        when(playerInventory.getSize()).thenReturn(41);
        when(playerInventory.getMaxStackSize()).thenReturn(64);
        when(playerInventory.getStorageContents()).thenAnswer(call -> copy(inventory));
        when(playerInventory.getItem(anyInt())).thenAnswer(call -> cloneItem(inventory[(int) call.getArgument(0)]));
        doAnswer(call -> {
            if (!refuseWrites) inventory = copy(call.getArgument(0));
            if (throwAfterWrite) throw new IllegalStateException("write completed before failure");
            return null;
        }).when(playerInventory).setStorageContents(any(ItemStack[].class));
        doAnswer(call -> {
            if (!refuseWrites) inventory[(int) call.getArgument(0)] = cloneItem(call.getArgument(1));
            if (throwAfterWrite) throw new IllegalStateException("write completed before failure");
            return null;
        }).when(playerInventory).setItem(anyInt(), nullable(ItemStack.class));
        when(player.getItemOnCursor()).thenAnswer(call -> cursor.get() == null
            ? new ItemStack(Material.AIR) : cursor.get().clone());
        doAnswer(call -> {
            if (!refuseWrites) cursor.set(cloneItem(call.getArgument(0)));
            if (throwAfterWrite) throw new IllegalStateException("cursor write completed before failure");
            return null;
        }).when(player).setItemOnCursor(nullable(ItemStack.class));
        Method install = NetworksDrawer.class.getDeclaredMethod("addClickHandler", Location.class);
        install.setAccessible(true);
        install.invoke(null, location);
        assertNotNull(handler.get());
    }

    @AfterEach
    void cleanup() throws Exception {
        try {
            map().remove(location);
            set("locked").remove(location);
            set("voidExcesses").remove(location);
        } finally {
            for (int i = mocks.size() - 1; i >= 0; i--) mocks.get(i).close();
            MockBukkit.unmock();
        }
    }

    @Test void normalWithdrawalPreservesAllThirteenItemsAndPdc() {
        click(false, true, template);
        assertEquals(0, drawer.getTotalAmount());
        assertEquals(13, playerItems());
        assertTrue(template.isSimilar(inventory[0]));
    }

    @Test void fullInventoryCannotLoseFinalWithdrawalFromLockedDrawer() throws Exception {
        fillInventory();
        set("locked").add(location);
        click(false, true, template);
        assertEquals(13, drawer.getTotalAmount());
        assertEquals(0, playerItems());
    }

    @Test void partialWriteThenThrowDebitsOnlyFivePhysicallyAcceptedItems() {
        fillInventory();
        inventory[0] = template.asQuantity(59);
        throwAfterWrite = true;
        assertDoesNotThrow(() -> click(false, true, template));
        assertEquals(8, drawer.getTotalAmount());
        assertEquals(64, playerItems());
        assertEquals(72, drawer.getTotalAmount() + playerItems());
    }

    @Test void refusedDestinationWriteCannotRemoveDrawerStock() {
        refuseWrites = true;
        click(false, true, template);
        assertEquals(13, drawer.getTotalAmount());
        assertEquals(0, playerItems());
    }

    @Test void shiftDepositMustDebitAuthoritativeInventoryInsteadOfSnapshotReference() {
        inventory[9] = template.asQuantity(11);
        click(true, true, template);
        assertEquals(24, drawer.getTotalAmount());
        assertEquals(0, playerItems());
        assertEquals(24, drawer.getTotalAmount() + playerItems());
    }

    @Test void cursorDepositMustDebitTheActualPlayerCursor() {
        cursor.set(template.asQuantity(11));
        click(false, false, Icon.ERROR_BORDER);
        assertEquals(24, drawer.getTotalAmount());
        assertEquals(0, amount(cursor.get()));
    }

    @Test void rejectedCursorWriteCannotCreditDrawer() {
        cursor.set(template.asQuantity(11));
        refuseWrites = true;
        click(false, false, Icon.ERROR_BORDER);
        assertEquals(13, drawer.getTotalAmount());
        assertEquals(11, amount(cursor.get()));
    }

    @Test void replacedMenuCannotUseItsOldClickHandler() {
        storage.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(mock(BlockMenu.class));
        click(false, true, template);
        assertEquals(13, drawer.getTotalAmount());
        assertEquals(0, playerItems());
    }

    @Test void sourceCannotBeWithdrawnAgainDuringDestinationWrite() {
        AtomicReference<ItemStack> recursive = new AtomicReference<>();
        doAnswer(call -> {
            recursive.set(drawer.requestItem0(location, new ItemRequest(template, 13), false));
            assertEquals(13, drawer.getTotalAmount(), "The guarded source remains reserved until readback");
            inventory[(int) call.getArgument(0)] = cloneItem(call.getArgument(1));
            return null;
        }).when(playerInventory).setItem(anyInt(), nullable(ItemStack.class));
        click(false, true, template);
        assertNull(recursive.get());
        assertEquals(0, drawer.getTotalAmount());
        assertEquals(13, playerItems());
    }

    @Test void intentionalInputVoidExcessStillConsumesOnlyTheOfferedSource() throws Exception {
        drawer.getStoredItemsMap().get(3).setAmount(256);
        set("voidExcesses").add(location);
        cursor.set(template.asQuantity(11));
        click(false, false, Icon.ERROR_BORDER);
        assertEquals(256, drawer.getTotalAmount());
        assertEquals(0, amount(cursor.get()));
    }

    @Test void newTypeIsPublishedOnlyAfterVerifiedCursorDebit() {
        drawer.getStoredItemsMap().clear();
        cursor.set(template.asQuantity(11));
        click(false, false, Icon.ERROR_BORDER);
        assertEquals(11, drawer.getTotalAmount());
        assertEquals(0, amount(cursor.get()));
        assertTrue(template.isSimilar(drawer.getStoredItemsMap().get(3).getSample()));
    }

    @Test void rejectedNewTypeCursorWriteLeavesItsPhysicalSourceAndNoEmptyType() {
        drawer.getStoredItemsMap().clear();
        cursor.set(template.asQuantity(11));
        refuseWrites = true;
        click(false, false, Icon.ERROR_BORDER);
        assertEquals(0, drawer.getStoredTypeCount());
        assertEquals(11, amount(cursor.get()));
    }

    private void click(boolean shift, boolean right, ItemStack clicked) {
        ClickAction action = mock(ClickAction.class);
        when(action.isShiftClicked()).thenReturn(shift);
        when(action.isRightClicked()).thenReturn(right);
        assertFalse(handler.get().onClick(player, 10, clicked, action));
    }

    private void fillInventory() {
        for (int i = 0; i < inventory.length; i++) inventory[i] = new ItemStack(Material.STONE, 64);
    }

    private int playerItems() {
        int result = 0;
        for (ItemStack item : inventory) if (item != null && template.isSimilar(item)) result += item.getAmount();
        return result;
    }

    private static int amount(ItemStack item) { return item == null || item.getType().isAir() ? 0 : item.getAmount(); }
    private static ItemStack cloneItem(ItemStack item) { return item == null ? null : item.clone(); }
    private static ItemStack[] copy(ItemStack[] items) {
        ItemStack[] result = new ItemStack[items.length];
        for (int i = 0; i < items.length; i++) result[i] = cloneItem(items[i]);
        return result;
    }
    private <T> MockedStatic<T> keep(MockedStatic<T> value) { mocks.add(value); return value; }
    @SuppressWarnings("unchecked") private static Map<Location, StorageUnitData> map() throws Exception {
        Field field = NetworksDrawer.class.getDeclaredField("storages"); field.setAccessible(true);
        return (Map<Location, StorageUnitData>) field.get(null);
    }
    @SuppressWarnings("unchecked") private static Set<Location> set(String name) throws Exception {
        Field field = NetworksDrawer.class.getDeclaredField(name); field.setAccessible(true);
        return (Set<Location>) field.get(null);
    }
}

package io.github.sefiraat.networks.slimefun.network.pusher;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.Networks;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A historical player stack in the control slot must survive break before its first viewer. */
class RecipeAwareBreakGuardTest {
    private MockedStatic<Networks> networks;
    private MockedStatic<NetworkStorage> nodes;
    private MockedStatic<StorageCacheUtils> storage;
    private MockedStatic<Slimefun> slimefun;
    private AbstractNetworkPusher pusher;
    private BlockMenu menu;
    private Location location;
    private BlockBreakEvent event;
    private Map<Location, Integer> batches;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws ReflectiveOperationException {
        var server = MockBukkit.mock();
        networks = mockStatic(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        Networks plugin = mock(Networks.class);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getInstance).thenReturn(plugin);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        location = new Location(server.addSimpleWorld("recipe-break"), 4, 64, 4);
        menu = mock(BlockMenu.class);
        when(menu.getLocation()).thenReturn(location);
        when(menu.getSize()).thenReturn(45);
        when(menu.hasViewer()).thenReturn(false);
        pusher = mock(AbstractNetworkPusher.class, CALLS_REAL_METHODS);
        doReturn(new int[] {16}).when(pusher).getOtherBackgroundSlots();
        doReturn(null).when(pusher).getOtherBackgroundStack();
        doReturn(List.of(0)).when(pusher).getSlotsToDrop();
        ItemSetting<Integer> setting = mock(ItemSetting.class);
        when(setting.getValue()).thenReturn(8);
        Field settingField = AbstractNetworkPusher.class.getDeclaredField("recipeBufferBatches");
        settingField.setAccessible(true);
        settingField.set(pusher, setting);
        Field batchField = AbstractNetworkPusher.class.getDeclaredField("RECIPE_BUFFER_BATCHES");
        batchField.setAccessible(true);
        batches = (Map<Location, Integer>) batchField.get(null);
        batches.put(location, 5);
        storage = mockStatic(StorageCacheUtils.class);
        storage.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(menu);
        nodes = mockStatic(NetworkStorage.class);
        slimefun = mockStatic(Slimefun.class, RETURNS_DEEP_STUBS);
        event = new BlockBreakEvent(location.getBlock(), mock(Player.class));
    }

    @AfterEach
    void tearDown() {
        try {
            if (slimefun != null) slimefun.close();
            if (nodes != null) nodes.close();
            if (storage != null) storage.close();
            if (networks != null) networks.close();
        } finally {
            AbstractNetworkPusher.resetRuntimeState();
            MockBukkit.unmock();
        }
    }

    @Test
    void initialForeignStackCancelsBreakBeforeAnyCleanup() {
        ItemStack foreign = new ItemStack(Material.DIAMOND, 13);
        foreign.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("otheraddon", "owner"), PersistentDataType.STRING, "preserve"));
        assertRetained(foreign);
    }

    @Test
    void controlLookingStackWithForeignOwnerIsStillProtected() throws ReflectiveOperationException {
        ItemStack foreign = control(false);
        foreign.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("otheraddon", "owner"), PersistentDataType.STRING, "preserve"));
        assertRetained(foreign);
    }

    @Test
    void controlLookingStackWithWrongPhysicalAmountIsStillProtected() throws ReflectiveOperationException {
        ItemStack foreign = control(true);
        foreign.setAmount(13);
        assertRetained(foreign);
    }

    @Test
    void stackedHistoricalBackgroundIsNotDiscardedAsAnIcon() {
        doReturn(new ItemStack(Material.PURPLE_STAINED_GLASS_PANE, 1)).when(pusher).getOtherBackgroundStack();
        assertRetained(new ItemStack(Material.PURPLE_STAINED_GLASS_PANE, 13));
    }

    @Test
    void alreadyCancelledBreakNeverCleansThePusher() {
        event.setCancelled(true);
        pusher.onBreak(event);
        assertNoCleanup();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recognizedControlsRetainOrdinaryBreakBehavior(boolean enabled) throws ReflectiveOperationException {
        when(menu.getItemInSlot(16)).thenReturn(control(enabled));
        assertOrdinaryCleanup();
    }

    @Test
    void exactHistoricalBackgroundRetainsOrdinaryBreakBehavior() {
        ItemStack background = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE, 1);
        doReturn(background).when(pusher).getOtherBackgroundStack();
        when(menu.getItemInSlot(16)).thenReturn(background.clone());
        assertOrdinaryCleanup();
    }

    @Test
    void emptyControlRetainsOrdinaryBreakBehavior() {
        assertOrdinaryCleanup();
    }

    private void assertRetained(ItemStack foreign) {
        ItemStack original = foreign.clone();
        when(menu.getItemInSlot(16)).thenReturn(foreign);
        pusher.onBreak(event);
        assertTrue(event.isCancelled(), "Break must stop while a genuine control-slot item has no ordinary drop slot");
        assertEquals(original, foreign);
        verify(menu, never()).replaceExistingItem(anyInt(), any(ItemStack.class));
        assertNoCleanup();
    }

    private void assertNoCleanup() {
        assertEquals(5, batches.get(location));
        verify(menu, never()).dropItems(location, 0);
        nodes.verify(() -> NetworkStorage.removeNode(location), never());
        slimefun.verify(Slimefun::getDatabaseManager, never());
        storage.verify(() -> StorageCacheUtils.setData(any(Location.class), anyString(), anyString()), never());
    }

    private void assertOrdinaryCleanup() {
        pusher.onBreak(event);
        assertFalse(event.isCancelled());
        assertFalse(batches.containsKey(location));
        verify(menu).dropItems(location, 0);
        nodes.verify(() -> NetworkStorage.removeNode(location));
        slimefun.verify(Slimefun::getDatabaseManager);
    }

    private ItemStack control(boolean enabled) throws ReflectiveOperationException {
        Method method = AbstractNetworkPusher.class.getDeclaredMethod("createRecipeAwareModeStack", Location.class, boolean.class);
        method.setAccessible(true);
        return (ItemStack) method.invoke(pusher, location, enabled);
    }
}

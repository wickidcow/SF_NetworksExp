package io.github.sefiraat.networks.network.barrel;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.integrations.infinityexpansion2.InfinityExpansion2Integration;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
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

/** A network receipt requires a physical IE2 output-slot debit, not a changed detached copy. */
class InfinityExpansion2WithdrawalReceiptTest {
    private static final int FIRST = 16;
    private static final int SECOND = 17;
    private final Location location = new Location(null, 101, 64, 103);
    private final Location accessor = new Location(null, 102, 64, 103);
    private final Location otherAccessor = new Location(null, 103, 64, 103);
    private final Map<Integer, ItemStack> physical = new HashMap<>();
    private MockedStatic<Networks> networks;
    private MockedStatic<StorageCacheUtils> storage;
    private SupportedPluginManager manager;
    private InfinityExpansion2Integration integration;
    private SlimefunItemHolder unit;
    private InfinityExpansion2Barrel adapter;
    private BlockMenu menu;
    private NetworkRoot root;

    @BeforeEach
    void setUp() throws ReflectiveOperationException {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        Networks plugin = mock(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        manager = mock(SupportedPluginManager.class);
        networks.when(Networks::getInstance).thenReturn(plugin);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getSupportedPluginManager).thenReturn(manager);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        when(plugin.getLogger()).thenReturn(logger);

        integration = mock(InfinityExpansion2Integration.class);
        unit = new SlimefunItemHolder();
        when(integration.getOutputSlots(unit.item)).thenReturn(new int[]{FIRST});
        menu = mock(BlockMenu.class);
        when(menu.getSize()).thenReturn(54);
        when(menu.getLocation()).thenReturn(location);
        // Some integrations return detached items. Only the setter changes actual storage.
        when(menu.getItemInSlot(anyInt())).thenAnswer(call -> copy(physical.get(call.getArgument(0))));
        doAnswer(call -> {
            physical.put(call.getArgument(0), copy(call.getArgument(1)));
            return null;
        }).when(menu).replaceExistingItem(anyInt(), nullable(ItemStack.class));
        physical.put(FIRST, diamond(8));
        storage = mockStatic(StorageCacheUtils.class);
        storage.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(menu);
        adapter = new InfinityExpansion2Barrel(integration, unit.item, location, diamond(1), 8, 64);
        root = spy(new NetworkRoot(new Location(null, 100, 64, 103), NodeType.CONTROLLER, 100));
        doReturn(Set.of(adapter)).when(root).getOutputAbleBarrels();
        doReturn(true).when(root).allowAccessOutput(any(Location.class));
        NetworkTransferUtils.resetRuntimeSafety();
        NetworkRoot.clearAllAccessHistory();
    }

    @AfterEach
    void tearDown() {
        try {
            NetworkTransferUtils.resetRuntimeSafety();
            NetworkRoot.clearAllAccessHistory();
            if (storage != null) storage.close();
            if (networks != null) networks.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 4, 8})
    void detachedGetterRequiresRealSourceDebitBeforeCredit(int requested) {
        ItemStack result = adapter.requestItem(new ItemRequest(diamond(1), requested));
        assertNotNull(result);
        assertEquals(requested, result.getAmount());
        assertEquals(8, total() + result.getAmount(),
            "Original eight items must equal authoritative storage plus the delivered receipt");
        assertEquals(8 - requested, total());
        assertTrue(diamond(1).isSimilar(result));
    }

    @Test
    void silentlyIgnoredSetterCannotAuthorizeRootWithdrawal() {
        doNothing().when(menu).replaceExistingItem(anyInt(), nullable(ItemStack.class));
        assertThrows(IllegalStateException.class,
            () -> root.getItemStack0(accessor, new ItemRequest(diamond(1), 4)));
        assertEquals(8, total());
        assertTrue(NetworkTransferUtils.isNetworkRootHeld(root, otherAccessor));
        assertNull(root.getItemStack0(otherAccessor, new ItemRequest(diamond(1), 4)));
    }

    @Test
    void dirtyCallbackRestoringSourceCannotAuthorizeAReceipt() {
        doAnswer(call -> {
            physical.put(FIRST, diamond(8));
            return null;
        }).when(menu).markDirty();
        assertThrows(IllegalStateException.class,
            () -> adapter.requestItem(new ItemRequest(diamond(1), 4)));
        assertEquals(8, total());
        verify(manager).disableOptionalIntegration(eq("InfinityExpansion2"), any(Throwable.class));
    }

    @Test
    void secondSlotCallbackCannotRestoreAnAlreadyVerifiedFirstDebit() throws ReflectiveOperationException {
        when(integration.getOutputSlots(unit.item)).thenReturn(new int[]{FIRST, SECOND});
        physical.put(FIRST, diamond(4));
        physical.put(SECOND, diamond(4));
        doAnswer(call -> {
            int slot = call.getArgument(0);
            physical.put(slot, copy(call.getArgument(1)));
            if (slot == SECOND) physical.put(FIRST, diamond(4));
            return null;
        }).when(menu).replaceExistingItem(anyInt(), nullable(ItemStack.class));
        assertThrows(IllegalStateException.class,
            () -> adapter.requestItem(new ItemRequest(diamond(1), 8)));
        assertEquals(4, total());
    }

    private int total() {
        return physical.values().stream().filter(item -> item != null && !item.getType().isAir())
            .mapToInt(ItemStack::getAmount).sum();
    }

    private static ItemStack diamond(int amount) {
        return new ItemStack(Material.DIAMOND, amount);
    }

    private static ItemStack copy(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static final class SlimefunItemHolder {
        final io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem item =
            mock(io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem.class);
    }
}

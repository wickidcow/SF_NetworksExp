package io.github.sefiraat.networks.network;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.barrel.InfinityBarrel;
import io.github.sefiraat.networks.network.barrel.LegacyInfinityExpansionBarrel;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
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
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression: copied Infinity output must be debited in its real menu before crediting withdrawals. */
@SuppressWarnings("removal")
class NetworkRootInfinityOutputDebitTest {
    private final Location controller = new Location(null, 701, 65, 901);
    private final Location accessor = new Location(null, 702, 65, 901);
    private final Location storageLocation = new Location(null, 703, 65, 901);
    private final AtomicReference<ItemStack> physicalOutput = new AtomicReference<>();
    private MockedStatic<Networks> networks;
    private MockedStatic<StorageCacheUtils> storage;
    private NetworkRoot root;
    private BlockMenu menu;

    @BeforeEach
    void setUp() throws ClassNotFoundException {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        Networks plugin = mock(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        networks.when(Networks::getInstance).thenReturn(plugin);
        networks.when(Networks::getConfigManager).thenReturn(config);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        when(plugin.getLogger()).thenReturn(logger);
        Class.forName(NetworkRoot.class.getName());
        root = spy(new NetworkRoot(controller, NodeType.CONTROLLER, 100));
        doReturn(true).when(root).allowAccessOutput(any(Location.class));
        physicalOutput.set(new ItemStack(Material.DIAMOND, 8));
        menu = mock(BlockMenu.class);
        when(menu.getItemInSlot(16)).thenAnswer(invocation -> {
            ItemStack held = physicalOutput.get();
            return held == null ? null : held.clone();
        });
        doAnswer(invocation -> {
            physicalOutput.set(invocation.getArgument(1));
            return null;
        }).when(menu).replaceExistingItem(eq(16), nullable(ItemStack.class));
        storage = mockStatic(StorageCacheUtils.class);
        storage.when(() -> StorageCacheUtils.getMenu(storageLocation)).thenReturn(menu);
        NetworkTransferUtils.resetRuntimeSafety();
        NetworkRoot.clearAllAccessHistory();
    }

    @AfterEach
    void tearDown() {
        try {
            if (storage != null) storage.close();
            NetworkTransferUtils.resetRuntimeSafety();
            NetworkRoot.clearAllAccessHistory();
            if (networks != null) networks.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @ParameterizedTest
    @CsvSource({"false,false,4,4", "false,true,4,4", "true,false,4,4", "true,true,4,4",
        "false,false,8,0", "false,true,8,1"})
    void originalAndLegacyInfinityOutputMustLoseRealItems(
        boolean deprecatedApi, boolean originalInfinity, int requested, int remaining) {
        final BarrelIdentity barrel = configureStorage(originalInfinity);
        ItemRequest request = new ItemRequest(new ItemStack(Material.DIAMOND, 1), requested);
        ItemStack delivered = deprecatedApi ? root.getItemStack(request) : root.getItemStack0(accessor, request);
        assertNotNull(delivered);
        assertEquals(8 - remaining, delivered.getAmount());
        assertEquals(remaining, physicalOutput.get() == null ? 0 : physicalOutput.get().getAmount());
        assertEquals(8, remaining + delivered.getAmount());
        verify(menu).replaceExistingItem(eq(16), nullable(ItemStack.class));
        verify(barrel, atLeastOnce()).requestItem(any(ItemRequest.class));
    }

    @Test
    void rejectedInfinityOutputSetterQuarantinesControllerWithoutCredit() {
        configureStorage(false);
        doNothing().when(menu).replaceExistingItem(eq(16), nullable(ItemStack.class));

        assertThrows(IllegalStateException.class, () -> root.getItemStack0(accessor,
            new ItemRequest(new ItemStack(Material.DIAMOND, 1), 4)));
        assertEquals(8, physicalOutput.get().getAmount());
        assertTrue(NetworkTransferUtils.isNetworkRootHeld(root, accessor));
        assertNull(root.getItemStack0(accessor,
            new ItemRequest(new ItemStack(Material.DIAMOND, 1), 4)));
    }

    private BarrelIdentity configureStorage(boolean originalInfinity) {
        final BarrelIdentity barrel = originalInfinity
            ? mock(InfinityBarrel.class)
            : mock(LegacyInfinityExpansionBarrel.class);
        when(barrel.getLocation()).thenReturn(storageLocation);
        when(barrel.getItemStack()).thenReturn(new ItemStack(Material.DIAMOND, 1));
        when(barrel.getOutputSlot()).thenReturn(new int[]{16});
        when(barrel.requestItem(any(ItemRequest.class))).thenAnswer(invocation -> {
            ItemStack stack = physicalOutput.get();
            return stack == null ? null : stack.clone();
        });
        doReturn(new LinkedHashSet<>(List.of(barrel))).when(root).getOutputAbleBarrels();
        return barrel;
    }
}

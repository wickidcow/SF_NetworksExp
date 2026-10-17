package io.github.sefiraat.networks.network;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A returned detached item must never be counted without a verified source-slot debit. */
@SuppressWarnings("removal")
class NetworkRootMenuDebitTest {
    private final Location controller = new Location(null, 901, 64, 902);
    private final Location sourceLocation = new Location(null, 902, 64, 902);
    private final Location accessor = new Location(null, 903, 64, 902);
    private final AtomicReference<ItemStack> stored = new AtomicReference<>();
    private MockedStatic<Networks> networks;
    private MockedStatic<StorageCacheUtils> storage;
    private MockedStatic<BlockMenuUtil> slots;
    private NetworkRoot root;
    private BlockMenu menu;

    @BeforeEach
    void setUp() {
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

        root = spy(new NetworkRoot(controller, NodeType.CONTROLLER, 100));
        root.getAdvancedGreedyBlocks().add(sourceLocation);
        doReturn(true).when(root).allowAccessOutput(accessor);
        stored.set(new ItemStack(Material.DIAMOND, 8));
        menu = mock(BlockMenu.class);
        when(menu.getItemInSlot(0)).thenAnswer(invocation -> stored.get().clone());
        doAnswer(invocation -> {
            stored.set(invocation.getArgument(1));
            return null;
        }).when(menu).replaceExistingItem(eq(0), nullable(ItemStack.class));

        storage = mockStatic(StorageCacheUtils.class);
        storage.when(() -> StorageCacheUtils.getMenu(sourceLocation)).thenReturn(menu);
        slots = mockStatic(BlockMenuUtil.class);
        slots.when(() -> BlockMenuUtil.getSafeTransportSlots(menu, ItemTransportFlow.WITHDRAW))
            .thenReturn(new int[]{0});
        NetworkTransferUtils.resetRuntimeSafety();
    }

    @AfterEach
    void tearDown() {
        try {
            if (slots != null) slots.close();
            if (storage != null) storage.close();
            NetworkTransferUtils.resetRuntimeSafety();
            NetworkRoot.clearAllAccessHistory();
            if (networks != null) networks.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void clonedMenuGetterCannotDuplicateAnUnchangedSource(boolean legacy) {
        ItemRequest request = new ItemRequest(new ItemStack(Material.DIAMOND, 1), 4);
        ItemStack received = legacy ? root.getItemStack(request) : root.getItemStack0(accessor, request);
        assertNotNull(received);
        assertEquals(4, received.getAmount());
        assertNotNull(stored.get());
        assertEquals(4, stored.get().getAmount(), "The authoritative source must lose exactly four items");
        verify(menu).replaceExistingItem(eq(0), any(ItemStack.class));
        assertEquals(8, stored.get().getAmount() + received.getAmount());
        assertEquals(0, NetworkTransferUtils.getHeldWithdrawalSourceCount());
    }
}

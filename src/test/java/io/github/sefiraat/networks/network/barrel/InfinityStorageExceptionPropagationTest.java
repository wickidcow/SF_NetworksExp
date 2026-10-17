package io.github.sefiraat.networks.network.barrel;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.integrations.infinityexpansion2.InfinityExpansion2Integration;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.lang.reflect.Method;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A storage adapter must never swallow a write-then-throw partial commit. */
class InfinityStorageExceptionPropagationTest {
    private final Location location = new Location(null, 31, 65, 72);
    private MockedStatic<Networks> networks;
    private SupportedPluginManager manager;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        manager = mock(SupportedPluginManager.class);
        Networks plugin = mock(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        networks.when(Networks::getInstance).thenReturn(plugin);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getSupportedPluginManager).thenReturn(manager);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        try {
            if (networks != null) networks.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void legacyReflectedPartialDepositMustRethrow() throws ReflectiveOperationException {
        FailingLegacyStorage storage = new FailingLegacyStorage();
        Method deposit = FailingLegacyStorage.class.getMethod("depositAll", ItemStack[].class, boolean.class);
        LegacyInfinityExpansionBarrel adapter =
            new LegacyInfinityExpansionBarrel(location, null, 0, 64, storage, deposit);
        ItemStack offered = new ItemStack(Material.DIAMOND, 8);
        assertThrows(IllegalStateException.class, () -> adapter.depositItemStack(new ItemStack[]{offered}));
        assertEquals(4, offered.getAmount(), "The adapter's partial mutation remains visible to the root");
        verify(manager).disableOptionalIntegration(eq("InfinityExpansion"), any(Throwable.class));
    }

    @Test
    void ie2PartialDepositMustRethrowInsteadOfReportingANormalReturn() throws ReflectiveOperationException {
        InfinityExpansion2Integration integration = mock(InfinityExpansion2Integration.class);
        SlimefunItem storageItem = mock(SlimefunItem.class);
        BlockMenu menu = mock(BlockMenu.class);
        ItemStack offered = new ItemStack(Material.DIAMOND, 8);
        when(integration.getInputSlots(storageItem)).thenReturn(new int[]{10});
        when(integration.isStorageUnitItem(offered)).thenReturn(false);
        when(integration.getLiveTemplate(location, storageItem)).thenReturn(null);
        InfinityExpansion2Barrel adapter =
            new InfinityExpansion2Barrel(integration, storageItem, location, offered, 0, 64);

        try (MockedStatic<StorageCacheUtils> storage = mockStatic(StorageCacheUtils.class);
             MockedStatic<BlockMenuUtil> insertion = mockStatic(BlockMenuUtil.class)) {
            storage.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(menu);
            insertion.when(() -> BlockMenuUtil.pushItem(
                eq(menu), eq(offered), any(int[].class))).thenAnswer(invocation -> {
                    offered.setAmount(4);
                    throw new IllegalStateException("Wrote a partial stack then failed");
                });

            assertThrows(IllegalStateException.class, () -> adapter.depositItemStack(new ItemStack[]{offered}));
            assertEquals(4, offered.getAmount());
            verify(manager).disableOptionalIntegration(eq("InfinityExpansion2"), any(Throwable.class));
        }
    }

    public static final class FailingLegacyStorage {
        public void depositAll(ItemStack[] items, boolean update) {
            items[0].setAmount(4);
            throw new IllegalStateException("Partial reflected commit");
        }
    }
}

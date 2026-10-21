package io.github.sefiraat.networks.slimefun.network.pusher;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.interfaces.InventoryBlock;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Dynamic-routing machines still have an authoritative physical input declaration. */
class RecipeAwarePhysicalInputDeclarationTest {
    private MockedStatic<StorageCacheUtils> storage;
    private MockedStatic<Networks> networks;
    private BlockMenu menu;
    private BlockMenuPreset preset;
    private Location location;

    @BeforeEach
    void setUp() {
        var server = MockBukkit.mock();
        networks = mockStatic(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        Networks plugin = mock(Networks.class);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getInstance).thenReturn(plugin);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        location = new Location(server.addSimpleWorld("recipe-physical-inputs"), 4, 64, 4);
        menu = mock(BlockMenu.class);
        preset = mock(BlockMenuPreset.class);
        when(menu.getPreset()).thenReturn(preset);
        when(menu.getLocation()).thenReturn(location);
        storage = mockStatic(StorageCacheUtils.class);
        storage.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(menu);
    }

    @AfterEach
    void tearDown() {
        try {
            if (storage != null) storage.close();
            if (networks != null) networks.close();
        }
        finally { MockBukkit.unmock(); }
    }

    @Test
    void ordinaryPresetDeclarationKeepsItsFastPath() {
        when(preset.getSlotsAccessedByItemTransport(ItemTransportFlow.INSERT)).thenReturn(new int[] {0, 1});
        assertArrayEquals(new int[] {0, 1}, AbstractNetworkPusher.getRecipeInputSlots(menu));
        storage.verifyNoInteractions();
    }

    @Test
    void itemAwareMachineUsesItsActualPhysicalInputSlots() {
        when(preset.getSlotsAccessedByItemTransport(ItemTransportFlow.INSERT)).thenReturn(new int[0]);
        SlimefunItem machine = mock(SlimefunItem.class, withSettings().extraInterfaces(InventoryBlock.class));
        when(((InventoryBlock) machine).getInputSlots()).thenReturn(new int[] {10, 11, 19, 20, 28, 29});
        storage.when(() -> StorageCacheUtils.getSlimefunItem(location)).thenReturn(machine);

        assertArrayEquals(new int[] {10, 11, 19, 20, 28, 29}, AbstractNetworkPusher.getRecipeInputSlots(menu));
    }

    @Test
    void staleMenuDoesNotBorrowAReplacementMachinesDeclaration() {
        when(preset.getSlotsAccessedByItemTransport(ItemTransportFlow.INSERT)).thenReturn(new int[0]);
        SlimefunItem machine = mock(SlimefunItem.class, withSettings().extraInterfaces(InventoryBlock.class));
        when(((InventoryBlock) machine).getInputSlots()).thenReturn(new int[] {10, 11});
        storage.when(() -> StorageCacheUtils.getSlimefunItem(location)).thenReturn(machine);
        storage.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(mock(BlockMenu.class));

        assertArrayEquals(new int[0], AbstractNetworkPusher.getRecipeInputSlots(menu));
        verify((InventoryBlock) machine, never()).getInputSlots();
    }

    @Test
    void unsupportedTargetDoesNotInventPhysicalInputs() {
        when(preset.getSlotsAccessedByItemTransport(ItemTransportFlow.INSERT)).thenReturn(new int[0]);
        storage.when(() -> StorageCacheUtils.getSlimefunItem(location)).thenReturn(mock(SlimefunItem.class));
        assertArrayEquals(new int[0], AbstractNetworkPusher.getRecipeInputSlots(menu));
    }

    @Test
    void missingLocationDoesNotQueryAStoredBlock() {
        when(preset.getSlotsAccessedByItemTransport(ItemTransportFlow.INSERT)).thenReturn(new int[0]);
        when(menu.getLocation()).thenReturn(null);
        assertArrayEquals(new int[0], AbstractNetworkPusher.getRecipeInputSlots(menu));
        storage.verifyNoInteractions();
    }
}

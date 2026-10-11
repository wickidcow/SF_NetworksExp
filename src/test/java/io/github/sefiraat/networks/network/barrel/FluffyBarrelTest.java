package io.github.sefiraat.networks.network.barrel;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.utils.StackUtils;
import io.ncbpfluffybear.fluffymachines.items.Barrel;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real item metadata, discovery and withdrawal; only the server/plugin storage boundary is substituted. */
class FluffyBarrelTest {
    private final Location location = new Location(null, 17, 64, 29);
    private MockedStatic<Networks> networks;
    private MockedStatic<StorageCacheUtils> storage;
    private BlockMenu menu;
    private BlockMenuPreset preset;
    private Block block;
    private Barrel barrel;
    private ItemStack[] slots;

    @BeforeEach
    void setUp() throws ClassNotFoundException {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        Networks plugin = mock(Networks.class);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getInstance).thenReturn(plugin);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        Class.forName(StackUtils.class.getName());
        Class.forName(NetworkRoot.class.getName());

        storage = mockStatic(StorageCacheUtils.class);
        menu = mock(BlockMenu.class);
        preset = mock(BlockMenuPreset.class);
        block = mock(Block.class);
        barrel = mock(Barrel.class);
        slots = new ItemStack[45];
        when(menu.getLocation()).thenReturn(location);
        when(menu.getBlock()).thenReturn(block);
        when(menu.getPreset()).thenReturn(preset);
        when(menu.getSize()).thenReturn(slots.length);
        when(menu.getItemInSlot(anyInt())).thenAnswer(call -> slots[(int) call.getArgument(0)]);
        when(preset.getSlotsAccessedByItemTransport(menu, ItemTransportFlow.WITHDRAW, null))
            .thenReturn(new int[]{24, 25});
        when(preset.getSlotsAccessedByItemTransport(menu, ItemTransportFlow.INSERT, null))
            .thenReturn(new int[]{19, 20});
        when(barrel.getCapacity(block)).thenReturn(1000);
        storage.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(menu);
    }

    @AfterEach
    void tearDown() {
        try {
            if (storage != null) storage.close();
        } finally {
            try {
                if (networks != null) networks.close();
            } finally {
                MockBukkit.unmock();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void outputOnlyStorageRemainsDiscoverable(boolean includeEmpty) {
        when(barrel.getStoredItem(block)).thenReturn(new ItemStack(Material.DIAMOND));
        slots[24] = new ItemStack(Material.DIAMOND, 64);

        FluffyBarrel found = NetworkRoot.getFluffyBarrel(menu, barrel, includeEmpty);

        assertNotNull(found);
        assertEquals(64L, found.getAmount());
        assertEquals(new ItemStack(Material.DIAMOND), found.getItemStack());
        assertEquals(64, slots[24].getAmount());
        assertReadOnlyDiscovery();
    }

    @Test
    void countsTheReserveAndBothOutputStacksOnce() {
        when(barrel.getStored(block)).thenReturn(64);
        when(barrel.getStoredItem(block)).thenReturn(new ItemStack(Material.DIAMOND));
        slots[24] = new ItemStack(Material.DIAMOND, 64);
        slots[25] = new ItemStack(Material.DIAMOND, 64);
        slots[19] = new ItemStack(Material.DIAMOND, 64);
        slots[31] = new ItemStack(Material.DIAMOND);

        FluffyBarrel found = FluffyBarrel.fromMenu(menu, barrel, false);

        assertNotNull(found);
        assertEquals(192L, found.getAmount());
        verify(menu, never()).getItemInSlot(19);
        verify(menu, never()).getItemInSlot(31);
        assertReadOnlyDiscovery();
    }

    @Test
    void oldBarrierDisplayDoesNotHideRealOutput() {
        when(barrel.getStoredItem(block)).thenReturn(new ItemStack(Material.BARRIER));
        slots[25] = new ItemStack(Material.DIAMOND, 17);

        FluffyBarrel found = FluffyBarrel.fromMenu(menu, barrel, false);

        assertNotNull(found);
        assertEquals(17L, found.getAmount());
        assertEquals(new ItemStack(Material.DIAMOND), found.getItemStack());
        assertReadOnlyDiscovery();
    }

    @Test
    void missingDisplayInOlderApiCanUseOutputWhenTheReserveIsZero() {
        when(barrel.getStoredItem(block)).thenThrow(new NullPointerException("missing display"));
        slots[24] = new ItemStack(Material.EMERALD, 9);

        FluffyBarrel found = FluffyBarrel.fromMenu(menu, barrel, false);

        assertNotNull(found);
        assertEquals(9L, found.getAmount());
        assertEquals(Material.EMERALD, found.getItemStack().getType());
    }

    @Test
    void registeredPositiveReserveIsVisibleWhileItsOutputIsBeingRefilled() {
        when(barrel.getStored(block)).thenReturn(256);
        when(barrel.getStoredItem(block)).thenReturn(new ItemStack(Material.DIAMOND));

        FluffyBarrel found = FluffyBarrel.fromMenu(menu, barrel, false);

        assertNotNull(found);
        assertEquals(256L, found.getAmount());
        assertNull(found.requestItem(new ItemRequest(found.getItemStack(), 64)));
        verify(barrel, never()).setStored(any(Block.class), anyInt());
    }

    @Test
    void positiveReserveWithoutItsOwnIdentityIsNeverGuessedFromOutput() {
        when(barrel.getStored(block)).thenReturn(100);
        when(barrel.getStoredItem(block)).thenReturn(new ItemStack(Material.BARRIER));
        slots[24] = new ItemStack(Material.DIAMOND, 64);

        assertNull(FluffyBarrel.fromMenu(menu, barrel, false));
        assertEquals(64, slots[24].getAmount());
        assertReadOnlyDiscovery();
    }

    @Test
    void mismatchedOutputCannotBeAttributedToTheRegisteredReserve() {
        when(barrel.getStored(block)).thenReturn(100);
        when(barrel.getStoredItem(block)).thenReturn(new ItemStack(Material.DIAMOND));
        slots[24] = new ItemStack(Material.DIAMOND, 64);
        slots[25] = new ItemStack(Material.EMERALD, 32);

        assertNull(FluffyBarrel.fromMenu(menu, barrel, false));
        assertEquals(64, slots[24].getAmount());
        assertEquals(32, slots[25].getAmount());
        assertReadOnlyDiscovery();
    }

    @Test
    void mixedOutputWithoutAReserveHasNoSingleStorageIdentity() {
        slots[24] = new ItemStack(Material.DIAMOND, 64);
        slots[25] = new ItemStack(Material.EMERALD, 32);

        assertNull(FluffyBarrel.fromMenu(menu, barrel, false));
        assertReadOnlyDiscovery();
    }

    @Test
    void discoveryPreservesCustomMetadataAndClonesItsIdentity() {
        ItemStack output = customDiamond(37, "original");
        ItemStack original = output.clone();
        slots[24] = output;

        FluffyBarrel found = FluffyBarrel.fromMenu(menu, barrel, false);

        assertNotNull(found);
        assertNotSame(output, found.getItemStack());
        assertEquals(1, found.getItemStack().getAmount());
        assertEquals(original.getItemMeta(), found.getItemStack().getItemMeta());
        assertEquals(original, output);
        found.getItemStack().setAmount(12);
        assertEquals(original, output);
        assertReadOnlyDiscovery();
    }

    @Test
    void differentLoreIsNotMergedIntoTheReserveTemplate() {
        when(barrel.getStored(block)).thenReturn(8);
        when(barrel.getStoredItem(block)).thenReturn(customDiamond(1, "original"));
        slots[24] = customDiamond(32, "different");

        assertNull(FluffyBarrel.fromMenu(menu, barrel, false));
        assertEquals(32, slots[24].getAmount());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void completelyEmptyBarrelRetainsItsExistingUnassignedBehavior(boolean includeEmpty) {
        when(barrel.getStoredItem(block)).thenReturn(new ItemStack(Material.BARRIER));

        assertNull(FluffyBarrel.fromMenu(menu, barrel, includeEmpty));
        assertReadOnlyDiscovery();
    }

    @Test
    void negativeOrMalformedReserveIsNotConvertedToInventedInventory() {
        slots[24] = new ItemStack(Material.DIAMOND, 64);
        when(barrel.getStored(block)).thenReturn(-1);
        assertNull(FluffyBarrel.fromMenu(menu, barrel, false));
        when(barrel.getStored(block)).thenThrow(new NumberFormatException("not loaded"));
        assertNull(FluffyBarrel.fromMenu(menu, barrel, false));
        assertEquals(64, slots[24].getAmount());
    }

    @Test
    void reservePlusOutputUsesLongArithmetic() {
        when(barrel.getStored(block)).thenReturn(Integer.MAX_VALUE);
        when(barrel.getStoredItem(block)).thenReturn(new ItemStack(Material.DIAMOND));
        slots[24] = new ItemStack(Material.DIAMOND, 64);
        slots[25] = new ItemStack(Material.DIAMOND, 64);

        FluffyBarrel found = FluffyBarrel.fromMenu(menu, barrel, false);

        assertNotNull(found);
        assertEquals((long) Integer.MAX_VALUE + 128L, found.getAmount());
    }

    @Test
    void invalidAndDuplicateTransportSlotsDoNotInflateTheQuantity() {
        when(preset.getSlotsAccessedByItemTransport(menu, ItemTransportFlow.WITHDRAW, null))
            .thenReturn(new int[]{24, 24, -1, 45, 25, 25});
        slots[24] = new ItemStack(Material.DIAMOND, 64);
        slots[25] = new ItemStack(Material.DIAMOND, 11);

        FluffyBarrel found = FluffyBarrel.fromMenu(menu, barrel, false);

        assertNotNull(found);
        assertEquals(75L, found.getAmount());
    }

    @Test
    void outputOnlyWithdrawalConservesPhysicalItemsAcrossRepeatedRequests() {
        slots[24] = customDiamond(40, "original");
        slots[25] = customDiamond(24, "original");
        ItemStack originalMeta = slots[24].clone();
        FluffyBarrel found = FluffyBarrel.fromMenu(menu, barrel, false);
        assertNotNull(found);

        ItemStack first = found.requestItem(new ItemRequest(found.getItemStack(), 48));
        assertNotNull(first);
        assertEquals(48, first.getAmount());
        assertEquals(0, slots[24].getAmount());
        assertEquals(16, slots[25].getAmount());
        assertEquals(originalMeta.getItemMeta(), first.getItemMeta());
        assertNotSame(slots[25], first);

        ItemStack second = found.requestItem(new ItemRequest(found.getItemStack(), 64));
        assertNotNull(second);
        assertEquals(16, second.getAmount());
        assertEquals(64, first.getAmount() + second.getAmount());
        assertEquals(0, slots[25].getAmount());
        assertNull(found.requestItem(new ItemRequest(found.getItemStack(), 1)));
        assertNull(FluffyBarrel.fromMenu(menu, barrel, false));
        verify(menu, atLeastOnce()).markDirty();
        verify(barrel, never()).setStored(any(Block.class), anyInt());
    }

    @Test
    void largerDisplayedTotalDoesNotAllowWithdrawalFromTheReserveBeforeFluffyRefills() {
        when(barrel.getStored(block)).thenReturn(64);
        when(barrel.getStoredItem(block)).thenReturn(new ItemStack(Material.DIAMOND));
        slots[24] = new ItemStack(Material.DIAMOND, 64);
        slots[25] = new ItemStack(Material.DIAMOND, 64);
        FluffyBarrel found = FluffyBarrel.fromMenu(menu, barrel, false);
        assertNotNull(found);
        assertEquals(192L, found.getAmount());

        ItemStack withdrawn = found.requestItem(new ItemRequest(found.getItemStack(), 256));

        assertNotNull(withdrawn);
        assertEquals(128, withdrawn.getAmount());
        assertNull(found.requestItem(new ItemRequest(found.getItemStack(), 64)));
        assertEquals(64, barrel.getStored(block));
        FluffyBarrel remaining = FluffyBarrel.fromMenu(menu, barrel, false);
        assertNotNull(remaining);
        assertEquals(64L, remaining.getAmount());
        verify(barrel, never()).setStored(any(Block.class), anyInt());
    }

    private void assertReadOnlyDiscovery() {
        verify(menu, never()).replaceExistingItem(anyInt(), any());
        verify(menu, never()).markDirty();
        verify(barrel, never()).updateMenu(any(), any(), anyBoolean(), anyInt());
        verify(barrel, never()).setStored(any(Block.class), anyInt());
    }

    private static ItemStack customDiamond(int amount, String lore) {
        ItemStack item = new ItemStack(Material.DIAMOND, amount);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Owner's diamond"));
        meta.lore(List.of(Component.text(lore)));
        meta.getPersistentDataContainer().set(NamespacedKey.fromString("oldaddon:owner"),
            PersistentDataType.STRING, "retained-owner");
        var model = meta.getCustomModelDataComponent();
        model.setFloats(List.of(712.5F));
        model.setStrings(List.of("retained-model"));
        meta.setCustomModelDataComponent(model);
        item.setItemMeta(meta);
        return item;
    }
}

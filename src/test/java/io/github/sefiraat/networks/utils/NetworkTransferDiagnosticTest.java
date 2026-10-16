package io.github.sefiraat.networks.utils;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class NetworkTransferDiagnosticTest {

    @Test
    void identifiesMachineSlotAndAmountChange() {
        Location location = new Location(null, -5274, -26, -5510);
        ItemStack before = stack(Material.IRON_NUGGET, 8);
        ItemStack after = stack(Material.IRON_NUGGET, 16);
        when(before.isSimilar(after)).thenReturn(true);

        SlimefunItem machine = mock(SlimefunItem.class);
        SlimefunItem dust = mock(SlimefunItem.class);
        when(machine.getId()).thenReturn("IE_DUST_EXTRACTOR");
        when(dust.getId()).thenReturn("IRON_DUST");

        try (MockedStatic<StorageCacheUtils> storage = mockStatic(StorageCacheUtils.class);
             MockedStatic<SlimefunItem> slimefun = mockStatic(SlimefunItem.class)) {
            storage.when(() -> StorageCacheUtils.getSlimefunItem(location)).thenReturn(machine);
            slimefun.when(() -> SlimefunItem.getByItem(before)).thenReturn(dust);
            slimefun.when(() -> SlimefunItem.getByItem(after)).thenReturn(dust);

            assertEquals(
                "[machine=IE_DUST_EXTRACTOR, slot=4, change=amount, "
                    + "before=sf:IRON_DUSTx8, after=sf:IRON_DUSTx16]",
                NetworkTransferUtils.describeMenuSourceChange(location, 4, before, after));
        }
    }

    @Test
    void distinguishesMetadataReplacementAndRemoval() {
        Location location = new Location(null, 1, 2, 3);
        ItemStack before = stack(Material.DIAMOND, 4);
        ItemStack metadataChanged = stack(Material.DIAMOND, 4);
        when(before.isSimilar(metadataChanged)).thenReturn(false);

        try (MockedStatic<StorageCacheUtils> storage = mockStatic(StorageCacheUtils.class);
             MockedStatic<SlimefunItem> slimefun = mockStatic(SlimefunItem.class)) {
            storage.when(() -> StorageCacheUtils.getSlimefunItem(location)).thenReturn(null);
            slimefun.when(() -> SlimefunItem.getByItem(before)).thenReturn(null);
            slimefun.when(() -> SlimefunItem.getByItem(metadataChanged)).thenReturn(null);

            assertEquals(
                "[machine=unknown, slot=2, change=metadata, "
                    + "before=material:DIAMONDx4, after=material:DIAMONDx4]",
                NetworkTransferUtils.describeMenuSourceChange(location, 2, before, metadataChanged));
            assertEquals(
                "[machine=unknown, slot=2, change=removed, "
                    + "before=material:DIAMONDx4, after=empty]",
                NetworkTransferUtils.describeMenuSourceChange(location, 2, before, null));
        }
    }

    @Test
    void brokenAddonStackCannotInterruptConflictDiagnostics() {
        Location location = new Location(null, 9, 8, 7);
        ItemStack before = stack(Material.IRON_INGOT, 8);
        ItemStack broken = mock(ItemStack.class);
        when(broken.getType()).thenThrow(new IllegalStateException("synthetic addon stack failure"));

        try (MockedStatic<StorageCacheUtils> storage = mockStatic(StorageCacheUtils.class)) {
            storage.when(() -> StorageCacheUtils.getSlimefunItem(location))
                .thenThrow(new IllegalStateException("synthetic storage lookup failure"));

            assertEquals(
                "[machine=unknown, slot=6, change=diagnostic-error]",
                NetworkTransferUtils.describeMenuSourceChange(location, 6, before, broken));
        }
    }

    private static ItemStack stack(Material material, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        when(stack.getAmount()).thenReturn(amount);
        return stack;
    }
}

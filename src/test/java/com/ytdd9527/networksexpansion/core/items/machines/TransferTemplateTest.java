package com.ytdd9527.networksexpansion.core.items.machines;

import com.balugaq.netex.api.interfaces.PushTickOnly;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TransferTemplateTest {
    @Test
    void emptyAndAirOnlyMenusHaveNoTemplateBatch() {
        AbstractTransfer transfer = transfer(true, 10, 11, 12, 13, 14, 15);
        BlockMenu menu = mock(BlockMenu.class);
        assertNull(transfer.collectTemplates(menu));
        ItemStack air = item(Material.AIR);
        when(menu.getItemInSlot(11)).thenReturn(air);
        assertNull(transfer.collectTemplates(menu));
    }

    @Test
    void ordinaryTransfersKeepSparseP2PIndexesAndAllTemplates() {
        AbstractTransfer transfer = transfer(false, 10, 11, 12, 13, 14, 15, 16);
        BlockMenu menu = mock(BlockMenu.class);
        ItemStack item = item(Material.STONE);
        for (int slot : new int[]{10, 12, 13, 14, 16}) {
            when(menu.getItemInSlot(slot)).thenReturn(item);
        }
        assertArrayEquals(new ItemStack[]{item, null, item, item, item, null, item}, transfer.collectTemplates(menu));
        verify(menu, never()).getLocation();
    }

    @Test
    void advancedPusherRetainsFourTemplateBudgetAndFairRotation() {
        AbstractTransfer transfer = transfer(true, 10, 11, 12, 13, 14, 15, 16);
        BlockMenu menu = mock(BlockMenu.class);
        when(menu.getLocation()).thenReturn(new Location(null, 712, 68, 713));
        ItemStack item = item(Material.STONE);
        for (int slot : new int[]{10, 12, 13, 14, 16}) {
            when(menu.getItemInSlot(slot)).thenReturn(item);
        }
        assertArrayEquals(new ItemStack[]{item, null, item, item, item, null, null}, transfer.collectTemplates(menu));
        assertArrayEquals(new ItemStack[]{item, null, item, item, null, null, item}, transfer.collectTemplates(menu));
        // Clearing the menu between passes must not emit the previous batch.
        for (int slot = 10; slot <= 16; slot++) {
            when(menu.getItemInSlot(slot)).thenReturn(null);
        }
        assertNull(transfer.collectTemplates(menu));
    }

    @Test
    void fourOrFewerTemplatesNeverRotate() {
        AbstractTransfer transfer = transfer(true, 10, 11, 12, 13, 14, 15);
        BlockMenu menu = mock(BlockMenu.class);
        ItemStack item = item(Material.STONE);
        for (int slot : new int[]{10, 12, 13, 15}) {
            when(menu.getItemInSlot(slot)).thenReturn(item);
        }
        ItemStack[] expected = {item, null, item, item, null, item};
        assertArrayEquals(expected, transfer.collectTemplates(menu));
        assertArrayEquals(expected, transfer.collectTemplates(menu));
        verify(menu, never()).getLocation();
    }

    private AbstractTransfer transfer(boolean pushOnly, int... slots) {
        var settings = withSettings();
        if (pushOnly) settings.extraInterfaces(PushTickOnly.class);
        AbstractTransfer transfer = mock(AbstractTransfer.class, settings);
        doReturn(slots).when(transfer).getItemSlots();
        doCallRealMethod().when(transfer).collectTemplates(any(BlockMenu.class));
        return transfer;
    }

    private ItemStack item(Material material) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        return item;
    }
}

package com.ytdd9527.networksexpansion.core.items.machines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.balugaq.netex.utils.BlockMenuUtil;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Output persistence must be recorded only by successful physical menu writes. */
class AutoCrafterOutputCommitTest {

    @Test
    void acceptedOutputDoesNotAddAnotherDirtyMutation() {
        BlockMenu menu = mock(BlockMenu.class);
        ItemStack output = new ItemStack(Material.STONE, 4);

        try (MockedStatic<BlockMenuUtil> menus = mockStatic(BlockMenuUtil.class)) {
            menus.when(() -> BlockMenuUtil.pushItem(menu, output, AutoCrafter.OUTPUT_SLOT))
                    .thenReturn(null);

            ItemStack remainder = AutoCrafter.commitCraftedOutput(menu, output);

            assertSame(output, remainder);
            assertEquals(0, remainder.getAmount(), "All produced items were accepted");
            menus.verify(() -> BlockMenuUtil.pushItem(menu, output, AutoCrafter.OUTPUT_SLOT));
            verify(menu, never()).markDirty();
        }
    }

    @Test
    void partialInsertionPreservesTheExactQuantityForNetworkRollback() {
        BlockMenu menu = mock(BlockMenu.class);
        ItemStack output = new ItemStack(Material.STONE, 8);
        ItemStack leftover = new ItemStack(Material.STONE, 3);

        try (MockedStatic<BlockMenuUtil> menus = mockStatic(BlockMenuUtil.class)) {
            menus.when(() -> BlockMenuUtil.pushItem(menu, output, AutoCrafter.OUTPUT_SLOT))
                    .thenReturn(leftover);

            ItemStack remainder = AutoCrafter.commitCraftedOutput(menu, output);

            assertSame(leftover, remainder, "Return the exact remainder used for rollback");
            assertEquals(3, remainder.getAmount());
            menus.verify(() -> BlockMenuUtil.pushItem(menu, output, AutoCrafter.OUTPUT_SLOT));
            verify(menu, never()).markDirty();
        }
    }

    @Test
    void rejectedFullOutputDoesNotPretendToModifyTheDestination() {
        BlockMenu menu = mock(BlockMenu.class);
        ItemStack output = new ItemStack(Material.STONE, 8);
        ItemStack rejected = new ItemStack(Material.STONE, 8);

        try (MockedStatic<BlockMenuUtil> menus = mockStatic(BlockMenuUtil.class)) {
            menus.when(() -> BlockMenuUtil.pushItem(menu, output, AutoCrafter.OUTPUT_SLOT))
                    .thenReturn(rejected);

            ItemStack remainder = AutoCrafter.commitCraftedOutput(menu, output);

            assertSame(rejected, remainder);
            assertEquals(8, remainder.getAmount());
            verify(menu, never()).markDirty();
        }
    }

    @Test
    void emptyAndAirResultsDoNotCallMenuInsertionOrDirtyTheMenu() {
        BlockMenu menu = mock(BlockMenu.class);
        ItemStack empty = new ItemStack(Material.STONE, 0);
        ItemStack air = new ItemStack(Material.AIR);

        try (MockedStatic<BlockMenuUtil> menus = mockStatic(BlockMenuUtil.class)) {
            assertSame(empty, AutoCrafter.commitCraftedOutput(menu, empty));
            assertSame(air, AutoCrafter.commitCraftedOutput(menu, air));

            menus.verifyNoInteractions();
            verify(menu, never()).markDirty();
        }
    }
}

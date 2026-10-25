package com.ytdd9527.networksexpansion.core.items.machines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.api.interfaces.SoftCellBannable;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.utils.StackUtils;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

/** Output persistence must be recorded only by successful physical menu writes. */
class AutoCrafterOutputCommitTest {

    @BeforeAll
    static void initializeNetworksConfigurationBeforeAutocrafterClass() throws ClassNotFoundException {
        // AutoCrafter implements SoftCellBannable, whose interface constants
        // read Networks' configuration once at class initialization. Initialize
        // them under the same fixture as the existing multi-core Networks tests
        // instead of poisoning the entire test JVM with a failed class init.
        try (MockedStatic<Networks> networks = mockStatic(Networks.class)) {
            ConfigManager config = mock(ConfigManager.class);
            Networks plugin = mock(Networks.class);
            networks.when(Networks::getConfigManager).thenReturn(config);
            networks.when(Networks::getInstance).thenReturn(plugin);
            when(config.isSoftCellBan()).thenReturn(false);
            when(config.getSoftCellBanThreshold()).thenReturn(64);
            when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
            Class.forName(SoftCellBannable.class.getName());
            Class.forName(StackUtils.class.getName());
            Class.forName(AutoCrafter.class.getName());
        }
    }

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
        // Bukkit rejects zero as a constructor amount; set it after creating a valid stack.
        ItemStack empty = new ItemStack(Material.STONE, 1);
        empty.setAmount(0);
        ItemStack air = new ItemStack(Material.AIR);

        try (MockedStatic<BlockMenuUtil> menus = mockStatic(BlockMenuUtil.class)) {
            assertSame(empty, AutoCrafter.commitCraftedOutput(menu, empty));
            assertSame(air, AutoCrafter.commitCraftedOutput(menu, air));

            menus.verifyNoInteractions();
            verify(menu, never()).markDirty();
        }
    }
}

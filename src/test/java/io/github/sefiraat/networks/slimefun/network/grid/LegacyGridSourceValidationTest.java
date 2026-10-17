package io.github.sefiraat.networks.slimefun.network.grid;

import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import io.github.sefiraat.networks.utils.StackUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Detached inventory/menu click ItemStacks may never be credited to network storage. */
class LegacyGridSourceValidationTest {
    private final Location gridLocation = new Location(null, 41, 66, -19);
    private final AbstractGrid grid = mock(AbstractGrid.class, CALLS_REAL_METHODS);
    private final NetworkRoot root = mock(NetworkRoot.class);
    private final Player player = mock(Player.class);
    private final BlockMenu menu = mock(BlockMenu.class);
    private final ClickAction action = mock(ClickAction.class);

    @Test
    void copiedPlayerInventoryItemCannotDepositWithoutAnAuthoritativeSlot() {
        ItemStack clickedSnapshot = mock(ItemStack.class);
        when(clickedSnapshot.getType()).thenReturn(Material.DIAMOND);
        when(player.getItemOnCursor()).thenReturn(new ItemStack(Material.AIR));
        when(menu.getLocation()).thenReturn(gridLocation);

        try (MockedStatic<StackUtils> stacks = mockStatic(StackUtils.class);
             MockedStatic<NetworkTransferUtils> transfer = mockStatic(NetworkTransferUtils.class)) {
            stacks.when(() -> StackUtils.isBlacklisted(clickedSnapshot)).thenReturn(false);

            grid.receiveItem(root, player, clickedSnapshot, action, menu);

            transfer.verifyNoInteractions();
            verifyNoInteractions(root);
            verify(player).getItemOnCursor();
        }
    }

    @Test
    void matchingLiveCursorUsesVerifiedCursorCommitNeverDetachedStack() {
        ItemStack clickedSnapshot = new ItemStack(Material.DIAMOND, 4);
        ItemStack currentCursor = new ItemStack(Material.DIAMOND, 4);
        when(player.getItemOnCursor()).thenReturn(currentCursor);
        when(menu.getLocation()).thenReturn(gridLocation);

        try (MockedStatic<StackUtils> stacks = mockStatic(StackUtils.class);
             MockedStatic<NetworkTransferUtils> transfer = mockStatic(NetworkTransferUtils.class)) {
            stacks.when(() -> StackUtils.isBlacklisted(clickedSnapshot)).thenReturn(false);

            grid.receiveItem(root, player, clickedSnapshot, action, menu);

            transfer.verify(() -> NetworkTransferUtils.movePlayerCursorIntoNetwork(
                root, gridLocation, player));
            transfer.verifyNoMoreInteractions();
        }
    }

    @Test
    void unrelatedCursorCannotAuthorizeDepositOfClickedItem() {
        ItemStack clickedSnapshot = new ItemStack(Material.DIAMOND, 4);
        when(player.getItemOnCursor()).thenReturn(new ItemStack(Material.EMERALD, 4));

        try (MockedStatic<StackUtils> stacks = mockStatic(StackUtils.class);
             MockedStatic<NetworkTransferUtils> transfer = mockStatic(NetworkTransferUtils.class)) {
            stacks.when(() -> StackUtils.isBlacklisted(clickedSnapshot)).thenReturn(false);

            grid.receiveItem(root, player, clickedSnapshot, action, menu);

            transfer.verifyNoInteractions();
        }
    }
}

package com.balugaq.netex.utils;

import com.balugaq.netex.api.enums.TransportMode;
import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import io.github.sefiraat.networks.utils.StackUtils;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class LineOperationUtilTest {
    private static final Location ACCESSOR = new Location(null, 1, 2, 3);
    private NetworkRoot root;
    private BlockMenu menu;

    @BeforeAll
    static void initializePluginConstants() throws ClassNotFoundException {
        // Initialize only the production classes' config constants, without booting a server.
        // All transfer methods under test below are the real compiled production methods.
        try (MockedStatic<Networks> networks = mockStatic(Networks.class)) {
            ConfigManager config = mock(ConfigManager.class);
            Networks plugin = mock(Networks.class);
            networks.when(Networks::getConfigManager).thenReturn(config);
            networks.when(Networks::getInstance).thenReturn(plugin);
            when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
            Class.forName(NetworkRoot.class.getName());
            Class.forName(StackUtils.class.getName());
        }
    }

    @BeforeEach
    void createBoundaries() {
        root = mock(NetworkRoot.class);
        menu = mock(BlockMenu.class);
    }

    @ParameterizedTest
    @EnumSource(value = TransportMode.class, names = {"NULL_ONLY", "P2P", "P2P_SPECIFIED_QUANTITY"})
    void pushOnlyModesDoNotInspectGrabInventories(TransportMode mode) {
        LineOperationUtil.grabItem(ACCESSOR, root, menu, mode, 64);
        verifyNoInteractions(root, menu);
    }

    @Test
    void alreadyLimitedGrabDoesNotReadTheMenu() {
        when(root.allowAccessInput(ACCESSOR)).thenReturn(false);
        LineOperationUtil.grabItem(ACCESSOR, root, menu, TransportMode.NONE, 64);
        verifyNoInteractions(menu);
    }

    @ParameterizedTest
    @EnumSource(value = TransportMode.class, names = {"NONE", "NONNULL_ONLY", "LAZY"})
    void newlyLimitedGrabStopsBeforeTheRemainingSlots(TransportMode mode) {
        when(root.allowAccessInput(ACCESSOR)).thenReturn(true, false);
        ItemStack item = stack(64);
        when(menu.getItemInSlot(0)).thenReturn(item);
        try (MockedStatic<BlockMenuUtil> slots = slots(0, 1, 2);
             MockedStatic<NetworkTransferUtils> transfers = mockStatic(NetworkTransferUtils.class)) {
            LineOperationUtil.grabItem(ACCESSOR, root, menu, mode, 64);
            transfers.verify(() -> NetworkTransferUtils.moveMenuSlotIntoNetwork(root, ACCESSOR, menu, 0, 64));
            transfers.verifyNoMoreInteractions();
            verify(menu, never()).getItemInSlot(1);
            verify(menu, never()).replaceExistingItem(anyInt(), any());
        }
    }

    @Test
    void ordinaryMissStillTriesLaterSlotsAndPreservesTheQuantityBudget() {
        when(root.allowAccessInput(ACCESSOR)).thenReturn(true);
        try (MockedStatic<BlockMenuUtil> slots = slots(0, 1, 2, 3);
             MockedStatic<NetworkTransferUtils> transfers = mockStatic(NetworkTransferUtils.class)) {
            transfers.when(() -> NetworkTransferUtils.moveMenuSlotIntoNetwork(root, ACCESSOR, menu, 1, 64))
                .thenReturn(10);
            transfers.when(() -> NetworkTransferUtils.moveMenuSlotIntoNetwork(root, ACCESSOR, menu, 2, 54))
                .thenReturn(54);
            LineOperationUtil.grabItem(ACCESSOR, root, menu, TransportMode.NONE, 64);
            transfers.verify(() -> NetworkTransferUtils.moveMenuSlotIntoNetwork(root, ACCESSOR, menu, 0, 64));
            transfers.verify(() -> NetworkTransferUtils.moveMenuSlotIntoNetwork(root, ACCESSOR, menu, 1, 64));
            transfers.verify(() -> NetworkTransferUtils.moveMenuSlotIntoNetwork(root, ACCESSOR, menu, 2, 54));
            transfers.verifyNoMoreInteractions();
        }
    }

    @Test
    void voidModeStillDiscardsLaterSlotsWhenTheNetworkStopsAccepting() {
        when(root.allowAccessInput(ACCESSOR)).thenReturn(true, false);
        ItemStack item = stack(64);
        for (int slot = 0; slot < 3; slot++) {
            when(menu.getItemInSlot(slot)).thenReturn(item);
        }
        try (MockedStatic<BlockMenuUtil> slots = slots(0, 1, 2);
             MockedStatic<NetworkTransferUtils> transfers = mockStatic(NetworkTransferUtils.class)) {
            LineOperationUtil.grabItem(ACCESSOR, root, menu, TransportMode.VOID, 64);
            for (int slot = 0; slot < 3; slot++) {
                final int current = slot;
                transfers.verify(() -> NetworkTransferUtils.moveMenuSlotIntoNetwork(root, ACCESSOR, menu, current, 64));
                verify(menu).replaceExistingItem(slot, null);
            }
            verify(menu, times(3)).markDirty();
        }
    }

    @Test
    void specifiedQuantityStopsAfterTheLimiterActivates() {
        when(root.allowAccessInput(ACCESSOR)).thenReturn(true, false);
        ItemStack template = stack(64);
        when(menu.getItemInSlot(anyInt())).thenReturn(template);
        try (MockedStatic<BlockMenuUtil> slots = slots(0, 1, 2);
             MockedStatic<NetworkTransferUtils> transfers = mockStatic(NetworkTransferUtils.class);
             MockedStatic<StackUtils> stacks = mockStatic(StackUtils.class)) {
            stacks.when(() -> StackUtils.itemsMatch(template, template)).thenReturn(true);
            stacks.when(() -> StackUtils.getAsQuantity(template, 1)).thenReturn(template);
            LineOperationUtil.grabItem(ACCESSOR, root, menu, TransportMode.SPECIFIED_QUANTITY, 64);
            // 192 stored, keep 64; the historical reverse-slot order starts at slot 2.
            transfers.verify(() -> NetworkTransferUtils.moveMenuSlotIntoNetwork(root, ACCESSOR, menu, 2, 64));
            transfers.verifyNoMoreInteractions();
        }
    }

    @Test
    void limiterDenialDoesNotHideAnItemFromTheNextTarget() {
        Location next = new Location(null, 2, 2, 3);
        ItemRequest request = request(64);
        ItemStack result = stack(64);
        var memo = new LineOperationUtil.PushAvailabilityMemo(1);
        when(root.allowAccessOutput(ACCESSOR)).thenReturn(false);
        when(root.allowAccessOutput(next)).thenReturn(true);
        when(root.getItemStack0(next, request)).thenReturn(result);

        assertNull(LineOperationUtil.requestNetworkItem(root, ACCESSOR, request, 0, memo));
        assertSame(result, LineOperationUtil.requestNetworkItem(root, next, request, 0, memo));
        verify(root, never()).getItemStack0(ACCESSOR, request);
        verify(root).getItemStack0(next, request);
    }

    @Test
    void zeroQuantityDoesNotHideAnItemFromLaterRequests() {
        ItemRequest request = request(0);
        ItemStack result = stack(64);
        var memo = new LineOperationUtil.PushAvailabilityMemo(1);
        when(root.allowAccessOutput(ACCESSOR)).thenReturn(true);
        when(root.getItemStack0(ACCESSOR, request)).thenReturn(result);
        assertNull(LineOperationUtil.requestNetworkItem(root, ACCESSOR, request, 0, memo));
        when(request.getAmount()).thenReturn(64);
        assertSame(result, LineOperationUtil.requestNetworkItem(root, ACCESSOR, request, 0, memo));
        verify(root, times(1)).getItemStack0(ACCESSOR, request);
    }

    @Test
    void confirmedMissIsReusedOnlyForThatTemplateAndThatPass() {
        when(root.allowAccessOutput(ACCESSOR)).thenReturn(true);
        ItemRequest request = request(64);
        var memo = new LineOperationUtil.PushAvailabilityMemo(2);
        assertNull(LineOperationUtil.requestNetworkItem(root, ACCESSOR, request, 0, memo));
        for (int slot = 0; slot < 54; slot++) {
            assertNull(LineOperationUtil.requestNetworkItem(root, ACCESSOR, request, 0, memo));
        }
        verify(root, times(1)).getItemStack0(ACCESSOR, request);
        assertNull(LineOperationUtil.requestNetworkItem(root, ACCESSOR, request, 1, memo));
        assertNull(LineOperationUtil.requestNetworkItem(
            root, ACCESSOR, request, 0, new LineOperationUtil.PushAvailabilityMemo(2)));
        verify(root, times(3)).getItemStack0(ACCESSOR, request);
    }

    @Test
    void successfulRequestsAndCallersWithoutAMemoKeepRequesting() {
        when(root.allowAccessOutput(ACCESSOR)).thenReturn(true);
        ItemRequest request = request(64);
        ItemStack result = stack(8);
        when(root.getItemStack0(ACCESSOR, request)).thenReturn(result, result, null, null);
        var memo = new LineOperationUtil.PushAvailabilityMemo(1);
        assertSame(result, LineOperationUtil.requestNetworkItem(root, ACCESSOR, request, 0, memo));
        assertSame(result, LineOperationUtil.requestNetworkItem(root, ACCESSOR, request, 0, memo));
        assertNull(LineOperationUtil.requestNetworkItem(root, ACCESSOR, request, 0, null));
        assertNull(LineOperationUtil.requestNetworkItem(root, ACCESSOR, request, 0, null));
        verify(root, times(4)).getItemStack0(ACCESSOR, request);
    }

    private MockedStatic<BlockMenuUtil> slots(int... values) {
        MockedStatic<BlockMenuUtil> slots = mockStatic(BlockMenuUtil.class);
        slots.when(() -> BlockMenuUtil.getSafeTransportSlots(menu, ItemTransportFlow.WITHDRAW)).thenReturn(values);
        return slots;
    }

    private static ItemRequest request(int amount) {
        ItemRequest request = mock(ItemRequest.class);
        when(request.getAmount()).thenReturn(amount);
        return request;
    }

    private static ItemStack stack(int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(Material.STONE);
        when(stack.getAmount()).thenReturn(amount);
        return stack;
    }
}

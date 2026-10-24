package com.balugaq.netex.utils;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.utils.StackUtils;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReferenceArray;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * An inventory may return detached copies from getItemInSlot. A reported Cargo
 * merge must still be persisted to the underlying physical menu.
 */
class BlockMenuUtilPushPersistenceTest {

    private BlockMenu menu;
    private AtomicReferenceArray<ItemStack> physicalSlots;
    private AtomicInteger savedWrites;

    @BeforeAll
    static void initializeNetworkUtilities() throws ClassNotFoundException {
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
    void createDetachedReadInventory() {
        menu = mock(BlockMenu.class);
        physicalSlots = new AtomicReferenceArray<>(9);
        savedWrites = new AtomicInteger();
        when(menu.getSize()).thenReturn(9);
        when(menu.getItemInSlot(anyInt())).thenAnswer(call -> {
            int index = call.getArgument(0);
            ItemStack stack = physicalSlots.get(index);
            return stack == null ? null : stack.clone();
        });
        doAnswer(call -> {
            int index = call.getArgument(0);
            ItemStack updated = call.getArgument(1);
            physicalSlots.set(index, updated == null ? null : updated.clone());
            savedWrites.incrementAndGet();
            return null;
        }).when(menu).replaceExistingItem(anyInt(), any(ItemStack.class), eq(false));
        doAnswer(call -> {
            int index = call.getArgument(0);
            ItemStack updated = call.getArgument(1);
            physicalSlots.set(index, updated == null ? null : updated.clone());
            savedWrites.incrementAndGet();
            return null;
        }).when(menu).replaceExistingItem(anyInt(), any(ItemStack.class));
    }

    @Test
    void mergingIntoDetachedExistingSlotPersistsTheTrueCount() {
        physicalSlots.set(0, new ItemStack(Material.STONE, 60));
        ItemStack incoming = new ItemStack(Material.STONE, 4);

        try (MockedStatic<StackUtils> matching = mockStatic(StackUtils.class)) {
            matchSameMaterial(matching);

            assertNull(BlockMenuUtil.pushItem(menu, incoming, 0));

            assertEquals(64, physicalSlots.get(0).getAmount());
            assertEquals(0, incoming.getAmount(), "Original API consumes the accepted input count");
            assertEquals(1, savedWrites.get(), "Merged slot must be written back once");
            verify(menu).replaceExistingItem(eq(0), any(ItemStack.class), eq(false));
            verify(menu, never()).markDirty();
        }
    }

    @Test
    void twoDetachedPartialStacksPersistWithoutCountingTheSameItemsTwice() {
        physicalSlots.set(0, new ItemStack(Material.COBBLESTONE, 63));
        physicalSlots.set(1, new ItemStack(Material.COBBLESTONE, 62));
        ItemStack incoming = new ItemStack(Material.COBBLESTONE, 3);

        try (MockedStatic<StackUtils> matching = mockStatic(StackUtils.class)) {
            matchSameMaterial(matching);

            assertNull(BlockMenuUtil.pushItem(menu, incoming, 0, 1));

            assertEquals(64, physicalSlots.get(0).getAmount());
            assertEquals(64, physicalSlots.get(1).getAmount());
            assertEquals(0, incoming.getAmount());
            assertEquals(2, savedWrites.get(), "Each changed slot must receive one write");
            verify(menu).replaceExistingItem(eq(0), any(ItemStack.class), eq(false));
            verify(menu).replaceExistingItem(eq(1), any(ItemStack.class), eq(false));
            verify(menu, never()).markDirty();
        }
    }

    @Test
    void aFullSlotIsUntouchedWhileANewSlotStillAcceptsTheOutput() {
        physicalSlots.set(0, new ItemStack(Material.STONE, 64));
        ItemStack incoming = new ItemStack(Material.STONE, 2);

        try (MockedStatic<StackUtils> matching = mockStatic(StackUtils.class)) {
            matchSameMaterial(matching);
            matching.when(() -> StackUtils.getAsQuantity(any(ItemStack.class), anyInt()))
                .thenAnswer(call -> {
                    ItemStack copied = ((ItemStack) call.getArgument(0)).clone();
                    copied.setAmount(call.getArgument(1));
                    return copied;
                });

            assertNull(BlockMenuUtil.pushItem(menu, incoming, 0, 1));

            assertEquals(64, physicalSlots.get(0).getAmount());
            assertEquals(2, physicalSlots.get(1).getAmount());
            assertEquals(0, incoming.getAmount());
            assertEquals(1, savedWrites.get(), "Only the accepted empty slot is written");
            verify(menu, never()).replaceExistingItem(eq(0), any(ItemStack.class), eq(false));
            verify(menu).replaceExistingItem(eq(1), any(ItemStack.class));
            verify(menu, never()).markDirty();
        }
    }

    private static void matchSameMaterial(MockedStatic<StackUtils> matching) {
        matching.when(() -> StackUtils.itemsMatch(any(ItemStack.class), any(ItemStack.class)))
            .thenAnswer(call -> ((ItemStack) call.getArgument(0)).getType()
                == ((ItemStack) call.getArgument(1)).getType());
    }
}

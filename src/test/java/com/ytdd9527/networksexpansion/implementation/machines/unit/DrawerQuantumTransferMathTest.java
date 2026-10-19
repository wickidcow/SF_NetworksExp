package com.ytdd9527.networksexpansion.implementation.machines.unit;

import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

class DrawerQuantumTransferMathTest {

    @BeforeEach
    void prepare() {
        MockBukkit.mock();
    }

    @AfterEach
    void cleanup() {
        MockBukkit.unmock();
    }

    @Test
    void threeBillionSourceNeverNarrowsToNegativeOrEmpty() {
        assertEquals(64, DrawerQuantumTransferMath.fromQuantum(3_000_000_000L, 0, 64));
        assertEquals(2_999_999_936L,
            DrawerQuantumTransferMath.remainingAfterDebit(3_000_000_000L, 64));
        assertEquals(4_999_999_936L,
            DrawerQuantumTransferMath.remainingAfterDebit(5_000_000_000L, 64));
        assertEquals(7, DrawerQuantumTransferMath.fromQuantum(3000, 57, 64));
        assertEquals(0, DrawerQuantumTransferMath.fromQuantum(3000, 64, 64));
    }

    @Test
    void plannedLargeQuantityCannotOverflowTheQuantumDestination() {
        assertEquals(10, DrawerQuantumTransferMath.toQuantum(10, 3_000_000_000L, 4_000_000_000L));
        assertEquals(3_000_000_010L,
            DrawerQuantumTransferMath.creditedAfterReceipt(3_000_000_000L, 4_000_000_000L, 10, 10));
        assertEquals(10, DrawerQuantumTransferMath.toQuantum(10, Integer.MAX_VALUE, 3_000_000_000L));
        assertEquals(0, DrawerQuantumTransferMath.toQuantum(10, 3_000_000_000L, 3_000_000_000L));
    }

    @Test
    void quantumIsCreditedOnlyForActuallyWithdrawnDrawerItems() {
        ItemStack expected = new ItemStack(Material.DIAMOND, 1);
        ItemStack actual = expected.clone();
        actual.setAmount(3);
        assertEquals(3, DrawerQuantumTransferMath.verifiedReceipt(expected, actual, 10));
        assertEquals(3_000_000_003L,
            DrawerQuantumTransferMath.creditedAfterReceipt(3_000_000_000L, 4_000_000_000L, 10, 3));
        assertEquals(0, DrawerQuantumTransferMath.verifiedReceipt(expected, null, 10));
    }

    @Test
    void mismatchedTagsAndImpossibleReceiptsAreRejected() {
        ItemStack expected = tagged("owner-a");
        ItemStack foreign = tagged("owner-b");
        foreign.setAmount(1);
        assertThrows(IllegalStateException.class,
            () -> DrawerQuantumTransferMath.verifiedReceipt(expected, foreign, 10));
        ItemStack excessive = tagged("owner-a");
        excessive.setAmount(11);
        assertThrows(IllegalStateException.class,
            () -> DrawerQuantumTransferMath.verifiedReceipt(expected, excessive, 10));
        assertThrows(IllegalArgumentException.class,
            () -> DrawerQuantumTransferMath.creditedAfterReceipt(5, 10, 4, 5));
        assertThrows(IllegalArgumentException.class,
            () -> DrawerQuantumTransferMath.remainingAfterDebit(5, 6));
    }

    @Test
    void existingQuantumMetadataAndCustomLimitArePreserved() {
        ItemStack original = tagged("owner-a");
        QuantumCache previous = new QuantumCache(original, 3_000_000_000L,
            4_000_000_000L, true, true);
        QuantumCache updated = DrawerQuantumTransferMath.rebalanced(previous, 3_000_000_010L);
        assertEquals(3_000_000_000L, previous.getAmountLong());
        assertEquals(3_000_000_010L, updated.getAmountLong());
        assertEquals(4_000_000_000L, updated.getLimitLong());
        assertTrue(updated.isVoidExcess());
        assertTrue(updated.supportsCustomMaxAmount());
        assertTrue(original.isSimilar(updated.getItemStack()));
        assertThrows(IllegalStateException.class,
            () -> DrawerQuantumTransferMath.rebalanced(previous, 4_000_000_001L));
    }

    private static ItemStack tagged(String owner) {
        ItemStack stack = new ItemStack(Material.DIAMOND, 1);
        stack.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("networkstest", "owner"), PersistentDataType.STRING, owner));
        return stack;
    }
}

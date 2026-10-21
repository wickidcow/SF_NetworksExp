package com.ytdd9527.networksexpansion.implementation.machines.unit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TEST FIRST: source-order contracts for the actual Drawer Quantum quick-transfer
 * GUI, not an in-memory model or a claim of a player exploit. Actual menu/SQLite
 * receipts, metadata readback and native click tests are separate requirements.
 */
class DrawerQuantumQuickTransferSourceContractTest {
    private static String drawerSource() throws IOException {
        Path file = Path.of("src/main/java/com/ytdd9527/networksexpansion/"
            + "implementation/machines/unit/NetworksDrawer.java");
        assertTrue(Files.isRegularFile(file), "Test must inspect the checked-out production source");
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    @Test
    void fromQuantumMustNeverNarrowStoredLongBalance() throws IOException {
        assertFalse(drawerSource().contains("final int left = (int) quantumAmount - canAdd;"),
            "A 3-billion-item source must not become an apparently empty Quantum item");
    }

    @Test
    void toQuantumMustNeverNarrowStoredLongBalance() throws IOException {
        assertFalse(drawerSource().contains(
            "final int quantumAmount = (int) quantumCache.getAmountLong();"),
            "Quantum counts over Integer.MAX_VALUE must retain their full 64-bit value");
    }

    @Test
    void toQuantumMustCommitOnlyAgainstAnAuthoritativeDestinationReceipt() throws IOException {
        assertTrue(drawerSource().contains("DrawerQuantumWithdrawalReceipt.transfer("),
            "The actual handler must use the authoritative Quantum destination receipt");
        String receipt = Files.readString(Path.of("src/main/java/com/ytdd9527/networksexpansion/"
            + "implementation/machines/unit/DrawerQuantumWithdrawalReceipt.java"), StandardCharsets.UTF_8);
        assertTrue(receipt.contains("drawer.withdrawToVerifiedDestination("),
            "Drawer source ownership must be guarded through the authoritative destination write");
        assertTrue(receipt.contains("menu.replaceExistingItem(") && receipt.contains("menu.getItemInSlot("),
            "A detached metadata write alone is not an authoritative destination receipt");
        assertTrue(receipt.contains("DrawerQuantumTransferMath.verifiedReceipt("),
            "The guarded offer must retain its amount and item identity checks");
    }

    @Test
    void existingQuantumFlagsMustNotBeResetOnTransfer() throws IOException {
        assertFalse(drawerSource().contains(
            "new QuantumCache(clone, quantumAmount + canAdd, quantumLimit, false, false)"),
            "Existing Quantum void mode and custom-capacity state must survive a deposit");
    }
}

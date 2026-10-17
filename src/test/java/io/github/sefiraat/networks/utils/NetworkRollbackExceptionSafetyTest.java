package io.github.sefiraat.networks.utils;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * A source network may mutate and/or install a refund before throwing. The
 * world-drop fallback must never mint a second unverified physical item.
 */
class NetworkRollbackExceptionSafetyTest {
    private final Location accessor = new Location(null, 321, 65, -872);
    private MockedStatic<Networks> networks;
    private NetworkRoot root;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        Networks plugin = mock(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        networks.when(Networks::getInstance).thenReturn(plugin);
        networks.when(Networks::getConfigManager).thenReturn(config);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        when(plugin.getLogger()).thenReturn(logger);
        root = mock(NetworkRoot.class);
        TransferAudit.reset();
    }

    @AfterEach
    void tearDown() {
        try {
            NetworkTransferUtils.resetRuntimeSafety();
            TransferAudit.reset();
            if (networks != null) {
                networks.close();
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void successfulCompleteRollbackCountsOnlyActuallyConsumedOffer() {
        doAnswer(invocation -> {
            ItemStack offered = invocation.getArgument(1);
            assertTrue(diamond(1).isSimilar(offered));
            offered.setAmount(0);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        ItemStack offer = diamond(10);
        rollback(offer);

        assertEquals(0, offer.getAmount());
        assertEquals(10, TransferAudit.snapshot().rollbackRequested());
        assertEquals(10, TransferAudit.snapshot().rollbackReinserted());
        assertEquals(0, TransferAudit.snapshot().outstandingRollbackItems());
        assertEquals(0, TransferAudit.snapshot().safetyDropped());
    }

    @Test
    void storageThrowWithoutDecrementMustNeverWorldDropOfferedRefund() {
        doThrow(new IllegalStateException("accepted but failed to adjust remainder"))
            .when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        ItemStack offer = diamond(10);
        rollback(offer);

        assertEquals(10, offer.getAmount());
        assertEquals(10, TransferAudit.snapshot().rollbackRequested());
        assertEquals(0, TransferAudit.snapshot().rollbackReinserted());
        assertEquals(0, TransferAudit.snapshot().safetyDropped());
        assertEquals(10, TransferAudit.snapshot().outstandingRollbackItems());
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void adapterAcceptsEverythingThenThrowsStillLeavesUnverifiedIncident() {
        doAnswer(invocation -> {
            ItemStack offer = invocation.getArgument(1);
            offer.setAmount(0);
            throw new IllegalStateException("after full network write");
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        ItemStack offer = diamond(10);
        rollback(offer);

        assertEquals(0, offer.getAmount());
        assertEquals(10, TransferAudit.snapshot().outstandingRollbackItems());
        assertEquals(0, TransferAudit.snapshot().rollbackReinserted());
        assertEquals(0, TransferAudit.snapshot().safetyDropped());
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void partiallyMutatedOfferThenThrowIsNeverTreatedAsVerifiedAcceptance() {
        doAnswer(invocation -> {
            ItemStack offer = invocation.getArgument(1);
            offer.setAmount(4);
            throw new IllegalStateException("partial transaction");
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        ItemStack offer = diamond(10);
        rollback(offer);

        assertEquals(4, offer.getAmount());
        assertEquals(10, TransferAudit.snapshot().outstandingRollbackItems());
        assertEquals(0, TransferAudit.snapshot().rollbackReinserted());
        assertEquals(0, TransferAudit.snapshot().safetyDropped());
    }

    @Test
    void ambiguousStorageRollbackBlocksRepeatWithdrawalsUntilRootReplacement() {
        doThrow(new IllegalStateException("source adapter failed after write"))
            .when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        rollback(diamond(10));
        assertEquals(1, NetworkTransferUtils.getHeldWithdrawalSourceCount());
        assertTrue(NetworkTransferUtils.getHeldWithdrawalSourceSummaries(5).getFirst()
            .contains("possibly-unreconciled=10xDIAMOND"));

        org.bukkit.entity.Player player = mock(org.bukkit.entity.Player.class);
        when(player.getItemOnCursor()).thenReturn(null);
        assertEquals(0, NetworkTransferUtils.moveNetworkItemOntoCursor(
            root, accessor, player, diamond(1), 3));
        verify(root, never()).getItemStack0(any(), any());

        NetworkRoot replacementRoot = mock(NetworkRoot.class);
        // Graph rebuilds can reuse the original physical storage. A different
        // root object is NOT evidence an ambiguous refund was reconciled.
        assertTrue(RootRollbackSafetyHold.isHeld(replacementRoot, accessor));
        assertEquals(1, NetworkTransferUtils.getHeldWithdrawalSourceCount());

        NetworkTransferUtils.resetRuntimeSafety(); // controlled plugin shutdown
        assertFalse(RootRollbackSafetyHold.isHeld(replacementRoot, accessor));
        assertEquals(0, NetworkTransferUtils.getHeldWithdrawalSourceCount());
    }

    @Test
    void linkageErrorsAtStorageBoundaryFailClosedToo() {
        doThrow(new NoSuchMethodError("foreign adapter mismatch"))
            .when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        rollback(diamond(10));

        assertEquals(10, TransferAudit.snapshot().outstandingRollbackItems());
        assertEquals(0, TransferAudit.snapshot().safetyDropped());
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void normalCompletionWithUnexpectedMaterialChangeDoesNotDropMalformedRemainder() {
        doAnswer(invocation -> {
            ItemStack offer = invocation.getArgument(1);
            offer.setAmount(5);
            offer.setType(Material.EMERALD);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        rollback(diamond(10));

        assertEquals(10, TransferAudit.snapshot().outstandingRollbackItems());
        assertEquals(0, TransferAudit.snapshot().rollbackReinserted());
        assertEquals(0, TransferAudit.snapshot().safetyDropped());
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void normalCompletionWithInflatedRemainderDoesNotDropExtraItems() {
        doAnswer(invocation -> {
            ItemStack offer = invocation.getArgument(1);
            offer.setAmount(15);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        rollback(diamond(10));

        assertEquals(10, TransferAudit.snapshot().outstandingRollbackItems());
        assertEquals(0, TransferAudit.snapshot().safetyDropped());
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void accessResetExceptionDoesNotAttemptAnyNetworkWrite() {
        doThrow(new IllegalStateException("blocked accessor")).when(root).uncontrolAccessInput(accessor);

        rollback(diamond(10));

        verify(root, never()).addItemStack0(any(), any());
        assertEquals(10, TransferAudit.snapshot().rollbackRequested());
        assertEquals(0, TransferAudit.snapshot().rollbackReinserted());
        // MockBukkit creates no world in this fixture, so the drop fails without minting items.
        assertEquals(0, TransferAudit.snapshot().safetyDropped());
    }

    private void rollback(ItemStack offer) {
        NetworkTransferUtils.rollbackNetworkWithdrawal(
            root, accessor, offer, accessor, "unit-test network refund");
    }

    private static ItemStack diamond(int amount) {
        ItemStack item = new ItemStack(Material.DIAMOND, amount);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
            NamespacedKey.fromString("test:rollback_owner"), PersistentDataType.STRING, "preserved");
        item.setItemMeta(meta);
        return item;
    }
}

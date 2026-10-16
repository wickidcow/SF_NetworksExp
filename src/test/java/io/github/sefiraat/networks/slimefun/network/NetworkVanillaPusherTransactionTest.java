package io.github.sefiraat.networks.slimefun.network;

import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkVanillaPusherTransactionTest {

    @BeforeAll
    static void initializePusherClassUnderConfigFixture() throws ClassNotFoundException {
        /*
         * NetworkVanillaPusher implements SoftCellBannable. That interface owns historical
         * static config snapshots, so loading the pusher without a plugin/config instance
         * poisons the JVM with an ExceptionInInitializerError before this focused helper can run.
         */
        try (MockedStatic<Networks> networks = mockStatic(Networks.class)) {
            ConfigManager config = mock(ConfigManager.class);
            networks.when(Networks::getConfigManager).thenReturn(config);
            when(config.isSoftCellBan()).thenReturn(false);
            when(config.getSoftCellBanThreshold()).thenReturn(64);
            Class.forName(NetworkVanillaPusher.class.getName());
        }
    }

    @Test
    void exactDestinationCommitConsumesSourceOnce() {
        ItemStack source = source(Material.DIAMOND, 7);
        ItemStack transfer = exactTransferChain(source, Material.DIAMOND, 7);

        AtomicReference<ItemStack> destination = new AtomicReference<>();
        var result = NetworkVanillaPusher.commitIntoEmptyDestination(
            source,
            destination::get,
            destination::set);

        assertTrue(result.moved());
        assertFalse(result.ambiguous());
        verify(source).setAmount(0);
    }

    @Test
    void changedOrAliasingCloneIsRejectedBeforeDestinationWrite() {
        ItemStack source = source(Material.DIAMOND, 7);
        ItemStack changedClone = transfer(Material.DIAMOND, 7);
        when(source.clone()).thenReturn(changedClone);
        when(source.isSimilar(changedClone)).thenReturn(false);

        AtomicReference<ItemStack> destination = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        var result = NetworkVanillaPusher.commitIntoEmptyDestination(
            source,
            destination::get,
            item -> writes.incrementAndGet());

        assertFalse(result.moved());
        assertFalse(result.ambiguous());
        assertTrue(result.failure() instanceof IllegalStateException);
        assertTrue(writes.get() == 0);
        verify(source, never()).setAmount(0);

        when(source.clone()).thenReturn(source);
        result = NetworkVanillaPusher.commitIntoEmptyDestination(
            source,
            destination::get,
            item -> writes.incrementAndGet());
        assertFalse(result.moved());
        assertTrue(result.failure() instanceof IllegalStateException);
        assertTrue(writes.get() == 0);
    }

    @Test
    void normalizedDestinationRollsBackAndPreservesSource() {
        ItemStack source = source(Material.DIAMOND, 7);
        ItemStack snapshot = transfer(Material.DIAMOND, 7);
        ItemStack transfer = transfer(Material.DIAMOND, 7);
        ItemStack normalized = transfer(Material.DIAMOND, 7);
        when(source.clone()).thenReturn(snapshot);
        when(source.isSimilar(snapshot)).thenReturn(true);
        when(snapshot.clone()).thenReturn(transfer);
        when(snapshot.isSimilar(transfer)).thenReturn(true);
        when(snapshot.isSimilar(normalized)).thenReturn(false);

        AtomicReference<ItemStack> destination = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();
        var result = NetworkVanillaPusher.commitIntoEmptyDestination(
            source,
            destination::get,
            item -> {
                if (item == null) {
                    destination.set(null);
                } else {
                    writes.incrementAndGet();
                    destination.set(normalized);
                }
            });

        assertFalse(result.moved());
        assertFalse(result.ambiguous());
        assertTrue(destination.get() == null);
        assertTrue(writes.get() == 1);
        verify(source, never()).setAmount(0);
    }

    @Test
    void inPlaceDestinationNormalizationCannotRedefineSuccessfulCommit() {
        ItemStack source = source(Material.DIAMOND, 7);
        ItemStack snapshot = transfer(Material.DIAMOND, 7);
        ItemStack transfer = transfer(Material.DIAMOND, 7);
        when(source.clone()).thenReturn(snapshot);
        when(source.isSimilar(snapshot)).thenReturn(true);
        when(snapshot.clone()).thenReturn(transfer);
        when(snapshot.isSimilar(transfer)).thenReturn(true);

        AtomicReference<ItemStack> destination = new AtomicReference<>();
        var result = NetworkVanillaPusher.commitIntoEmptyDestination(
            source,
            destination::get,
            item -> {
                if (item == null) {
                    destination.set(null);
                } else {
                    when(item.getAmount()).thenReturn(1);
                    when(snapshot.isSimilar(item)).thenReturn(true);
                    destination.set(item);
                }
            });

        assertFalse(result.moved());
        assertFalse(result.ambiguous());
        assertTrue(destination.get() == null);
        verify(source, never()).setAmount(0);
    }

    @Test
    void writeThenThrowStillConsumesCommittedSource() {
        ItemStack source = source(Material.GOLD_INGOT, 4);
        ItemStack transfer = exactTransferChain(source, Material.GOLD_INGOT, 4);

        AtomicReference<ItemStack> destination = new AtomicReference<>();
        var result = NetworkVanillaPusher.commitIntoEmptyDestination(
            source,
            destination::get,
            item -> {
                destination.set(item);
                throw new IllegalStateException("setter threw after commit");
            });

        assertTrue(result.moved());
        assertFalse(result.ambiguous());
        assertTrue(result.failure() instanceof IllegalStateException);
        verify(source).setAmount(0);
    }

    @Test
    void throwBeforeWritePreservesSource() {
        ItemStack source = source(Material.IRON_INGOT, 3);
        exactTransferChain(source, Material.IRON_INGOT, 3);

        AtomicReference<ItemStack> destination = new AtomicReference<>();
        var result = NetworkVanillaPusher.commitIntoEmptyDestination(
            source,
            destination::get,
            item -> {
                throw new IllegalStateException("setter rejected before commit");
            });

        assertFalse(result.moved());
        assertFalse(result.ambiguous());
        assertTrue(destination.get() == null);
        verify(source, never()).setAmount(0);
    }

    @Test
    void failedRollbackConsumesSourceInsteadOfLeavingDuplicate() {
        ItemStack source = source(Material.EMERALD, 5);
        ItemStack snapshot = transfer(Material.EMERALD, 5);
        ItemStack transfer = transfer(Material.EMERALD, 5);
        ItemStack normalized = transfer(Material.EMERALD, 5);
        when(source.clone()).thenReturn(snapshot);
        when(source.isSimilar(snapshot)).thenReturn(true);
        when(snapshot.clone()).thenReturn(transfer);
        when(snapshot.isSimilar(transfer)).thenReturn(true);
        when(snapshot.isSimilar(normalized)).thenReturn(false);

        AtomicReference<ItemStack> destination = new AtomicReference<>();
        var result = NetworkVanillaPusher.commitIntoEmptyDestination(
            source,
            destination::get,
            item -> {
                if (item == null) {
                    throw new IllegalStateException("rollback refused");
                }
                destination.set(normalized);
            });

        assertTrue(result.moved());
        assertTrue(result.ambiguous());
        assertTrue(destination.get() == normalized);
        verify(source).setAmount(0);
    }

    @Test
    void occupiedDestinationIsNeverTouched() {
        ItemStack source = source(Material.DIAMOND, 2);
        ItemStack occupied = transfer(Material.NETHERITE_INGOT, 1);
        AtomicReference<ItemStack> destination = new AtomicReference<>(occupied);
        AtomicInteger writes = new AtomicInteger();

        var result = NetworkVanillaPusher.commitIntoEmptyDestination(
            source,
            destination::get,
            item -> writes.incrementAndGet());

        assertFalse(result.moved());
        assertFalse(result.ambiguous());
        assertTrue(destination.get() == occupied);
        assertTrue(writes.get() == 0);
        verify(source, never()).clone();
        verify(source, never()).setAmount(0);
    }

    private static ItemStack exactTransferChain(ItemStack source, Material type, int amount) {
        ItemStack snapshot = transfer(type, amount);
        ItemStack write = transfer(type, amount);
        when(source.clone()).thenReturn(snapshot);
        when(source.isSimilar(snapshot)).thenReturn(true);
        when(snapshot.clone()).thenReturn(write);
        when(snapshot.isSimilar(write)).thenReturn(true);
        return write;
    }

    private static ItemStack source(Material type, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(amount);
        return stack;
    }

    private static ItemStack transfer(Material type, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenReturn(amount);
        return stack;
    }
}

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

class NetworkVanillaGrabberTransactionTest {

    @BeforeAll
    static void initializeGrabberClassUnderConfigFixture() throws ClassNotFoundException {
        try (MockedStatic<Networks> networks = mockStatic(Networks.class)) {
            ConfigManager config = mock(ConfigManager.class);
            networks.when(Networks::getConfigManager).thenReturn(config);
            when(config.isSoftCellBan()).thenReturn(false);
            when(config.getSoftCellBanThreshold()).thenReturn(64);
            Class.forName(NetworkVanillaGrabber.class.getName());
        }
    }

    @Test
    void exactCommitClearsSourceAfterDestinationReadback() {
        ItemStack source = mutableStack(Material.DIAMOND, 7);
        AtomicReference<ItemStack> sourceSlot = new AtomicReference<>(source);
        AtomicReference<ItemStack> destination = new AtomicReference<>();

        var result = NetworkVanillaGrabber.commitInventorySourceToOutput(
            source,
            sourceSlot::get,
            sourceSlot::set,
            destination::get,
            destination::set);

        assertTrue(result.moved());
        assertFalse(result.ambiguous());
        assertTrue(sourceSlot.get() == null);
        assertTrue(destination.get().getAmount() == 7);
    }

    @Test
    void destinationWriteThenThrowStillCommitsWhenReadbackIsExact() {
        ItemStack source = mutableStack(Material.IRON_INGOT, 4);
        AtomicReference<ItemStack> sourceSlot = new AtomicReference<>(source);
        AtomicReference<ItemStack> destination = new AtomicReference<>();

        var result = NetworkVanillaGrabber.commitInventorySourceToOutput(
            source,
            sourceSlot::get,
            sourceSlot::set,
            destination::get,
            item -> {
                destination.set(item);
                throw new IllegalStateException("destination setter threw after commit");
            });

        assertTrue(result.moved());
        assertFalse(result.ambiguous());
        assertTrue(result.failure() instanceof IllegalStateException);
        assertTrue(sourceSlot.get() == null);
        assertTrue(destination.get().getAmount() == 4);
    }

    @Test
    void sourceRemovalWriteThenThrowIsRecognizedAsCommitted() {
        ItemStack source = mutableStack(Material.GOLD_INGOT, 3);
        AtomicReference<ItemStack> sourceSlot = new AtomicReference<>(source);
        AtomicReference<ItemStack> destination = new AtomicReference<>();

        var result = NetworkVanillaGrabber.commitInventorySourceToOutput(
            source,
            sourceSlot::get,
            item -> {
                sourceSlot.set(item);
                throw new IllegalStateException("source setter threw after removal");
            },
            destination::get,
            destination::set);

        assertTrue(result.moved());
        assertFalse(result.ambiguous());
        assertTrue(result.failure() instanceof IllegalStateException);
        assertTrue(sourceSlot.get() == null);
        assertTrue(destination.get().getAmount() == 3);
    }

    @Test
    void rejectedSourceRemovalRollsDestinationBack() {
        ItemStack source = mutableStack(Material.EMERALD, 5);
        AtomicReference<ItemStack> sourceSlot = new AtomicReference<>(source);
        AtomicReference<ItemStack> destination = new AtomicReference<>();
        AtomicInteger sourceWrites = new AtomicInteger();

        var result = NetworkVanillaGrabber.commitInventorySourceToOutput(
            source,
            sourceSlot::get,
            item -> {
                sourceWrites.incrementAndGet();
                throw new IllegalStateException("source removal refused");
            },
            destination::get,
            destination::set);

        assertFalse(result.moved());
        assertFalse(result.ambiguous());
        assertTrue(result.failure() instanceof IllegalStateException);
        assertTrue(sourceSlot.get() == source);
        assertTrue(destination.get() == null);
        assertTrue(sourceWrites.get() == 1);
    }

    @Test
    void normalizedDestinationRollsBackBeforeSourceMutation() {
        ItemStack source = mutableStack(Material.DIAMOND, 6);
        AtomicReference<ItemStack> sourceSlot = new AtomicReference<>(source);
        AtomicReference<ItemStack> destination = new AtomicReference<>();
        AtomicInteger destinationWrites = new AtomicInteger();

        var result = NetworkVanillaGrabber.commitInventorySourceToOutput(
            source,
            sourceSlot::get,
            sourceSlot::set,
            destination::get,
            item -> {
                destinationWrites.incrementAndGet();
                if (item == null) {
                    destination.set(null);
                } else {
                    item.setAmount(1);
                    destination.set(item);
                }
            });

        assertFalse(result.moved());
        assertFalse(result.ambiguous());
        assertTrue(sourceSlot.get() == source);
        assertTrue(destination.get() == null);
        assertTrue(destinationWrites.get() == 2);
    }

    @Test
    void rollbackFailurePreservesRemainderForReadablePartialDestination() {
        ItemStack source = mutableStack(Material.NETHERITE_INGOT, 2);
        AtomicReference<ItemStack> sourceSlot = new AtomicReference<>(source);
        AtomicReference<ItemStack> destination = new AtomicReference<>();
        AtomicInteger destinationWrites = new AtomicInteger();

        var result = NetworkVanillaGrabber.commitInventorySourceToOutput(
            source,
            sourceSlot::get,
            sourceSlot::set,
            destination::get,
            item -> {
                if (destinationWrites.getAndIncrement() == 0) {
                    item.setAmount(1);
                    destination.set(item);
                } else {
                    throw new IllegalStateException("destination rollback refused");
                }
            });

        assertTrue(result.moved());
        assertTrue(result.ambiguous());
        assertTrue(result.failure() instanceof IllegalStateException);
        assertTrue(sourceSlot.get() == source);
        assertTrue(sourceSlot.get().getAmount() == 1);
        assertTrue(destination.get().getAmount() == 1);
    }

    @Test
    void unreadableUnresolvedDestinationFallsBackToWholeSourceRemoval() {
        ItemStack source = mutableStack(Material.NETHERITE_INGOT, 2);
        AtomicReference<ItemStack> sourceSlot = new AtomicReference<>(source);
        AtomicReference<ItemStack> destination = new AtomicReference<>();
        AtomicInteger reads = new AtomicInteger();

        var result = NetworkVanillaGrabber.commitInventorySourceToOutput(
            source,
            sourceSlot::get,
            sourceSlot::set,
            () -> {
                if (reads.getAndIncrement() == 0) {
                    return null;
                }
                throw new IllegalStateException("destination read unavailable");
            },
            destination::set);

        assertTrue(result.moved());
        assertTrue(result.ambiguous());
        assertTrue(result.failure() instanceof IllegalStateException);
        assertTrue(sourceSlot.get() == null);
    }

    @Test
    void changedOrAliasingCloneIsRejectedBeforeDestinationWrite() {
        ItemStack source = mutableStack(Material.DIAMOND, 2);
        AtomicReference<ItemStack> sourceSlot = new AtomicReference<>(source);
        AtomicReference<ItemStack> destination = new AtomicReference<>();
        AtomicInteger writes = new AtomicInteger();

        when(source.clone()).thenReturn(source);
        var result = NetworkVanillaGrabber.commitInventorySourceToOutput(
            source,
            sourceSlot::get,
            sourceSlot::set,
            destination::get,
            item -> writes.incrementAndGet());

        assertFalse(result.moved());
        assertFalse(result.ambiguous());
        assertTrue(result.failure() instanceof IllegalStateException);
        assertTrue(writes.get() == 0);
        assertTrue(sourceSlot.get() == source);
        verify(source, never()).setAmount(0);
    }

    private static ItemStack mutableStack(Material type, int amount) {
        AtomicInteger current = new AtomicInteger(amount);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.getAmount()).thenAnswer(invocation -> current.get());
        org.mockito.Mockito.doAnswer(invocation -> {
            current.set(invocation.getArgument(0));
            return null;
        }).when(stack).setAmount(org.mockito.ArgumentMatchers.anyInt());
        when(stack.isSimilar(org.mockito.ArgumentMatchers.any(ItemStack.class))).thenAnswer(invocation -> {
            ItemStack other = invocation.getArgument(0);
            return other != null && other.getType() == type;
        });
        when(stack.clone()).thenAnswer(invocation -> mutableStack(type, current.get()));
        return stack;
    }
}

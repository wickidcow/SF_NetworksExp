package io.github.sefiraat.networks.network.barrel;

import com.balugaq.netex.utils.BlockMenuUtil;
import io.github.sefiraat.networks.utils.StackUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Set;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Verifies the actual IE2 output slots before issuing a detached withdrawal receipt. */
final class InfinityExpansion2Withdrawal {
    // Only active calls retain a menu. Different adapter instances may share the same menu.
    private static final Set<BlockMenu> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());

    @FunctionalInterface
    interface SourceConsumer {
        void consume(int slot, int amount);
    }

    private InfinityExpansion2Withdrawal() {
    }

    static @Nullable ItemStack withdraw(
        @NotNull BlockMenu menu, int @NotNull [] outputSlots,
        @Nullable ItemStack target, int requested) {
        return withdraw(menu, outputSlots, target, requested,
            (slot, amount) -> BlockMenuUtil.consumeItem(menu, slot, amount));
    }

    static @Nullable ItemStack withdraw(
        @NotNull BlockMenu menu, int @NotNull [] outputSlots,
        @Nullable ItemStack target, int requested, @NotNull SourceConsumer consumer) {
        if (requested <= 0 || empty(target) || outputSlots.length == 0) {
            return null;
        }
        synchronized (ACTIVE) {
            if (!ACTIVE.add(menu)) {
                throw new IllegalStateException("Reentrant IE2 output withdrawal is unverified");
            }
        }
        try {
            return withdrawOwned(menu, outputSlots.clone(), target, requested, consumer);
        } finally {
            synchronized (ACTIVE) {
                ACTIVE.remove(menu);
            }
        }
    }

    private static @Nullable ItemStack withdrawOwned(
        BlockMenu menu, int[] slots, ItemStack target, int requested, SourceConsumer consumer) {
        final int size = menu.getSize();
        if (slots.length > size) {
            throw new IllegalStateException("Invalid IE2 output-slot count");
        }
        final Set<Integer> seen = new HashSet<>();
        for (int slot : slots) {
            if (slot < 0 || slot >= size || !seen.add(slot)) {
                throw new IllegalStateException("Invalid or duplicate IE2 output slot: " + slot);
            }
        }

        final ItemStack[] before = new ItemStack[slots.length];
        final ItemStack[] expected = new ItemStack[slots.length];
        final int[] debits = new int[slots.length];
        ItemStack prototype = null;
        int received = 0;
        for (int index = 0; index < slots.length; index++) {
            final ItemStack source = snapshot(menu.getItemInSlot(slots[index]));
            before[index] = source;
            expected[index] = source;
            if (source == null || received >= requested || !StackUtils.itemsMatch(source, target)) {
                continue;
            }
            // Eligibility follows Networks' matcher, but do not normalize physical
            // metadata to the request or combine distinct item identities into one stack.
            if (prototype == null) {
                prototype = snapshot(source);
            } else if (!prototype.isSimilar(source)) {
                continue;
            }
            final int moved = Math.min(source.getAmount(), requested - received);
            final int remaining = source.getAmount() - moved;
            expected[index] = remaining == 0 ? null : snapshot(source);
            if (expected[index] != null) expected[index].setAmount(remaining);
            debits[index] = moved;
            received += moved;
        }
        if (received == 0) return null;

        final ItemStack receipt = snapshot(prototype);
        receipt.setAmount(received);
        // Finish every potentially failing snapshot before touching physical inventory.
        verify(menu, slots, before);
        for (int index = 0; index < slots.length; index++) {
            if (debits[index] == 0) continue;
            if (!same(before[index], menu.getItemInSlot(slots[index]))) {
                throw new IllegalStateException("IE2 output changed before its source debit");
            }
            // Preserve the existing live-stack consumption path when it genuinely
            // debits the menu. Only a still-unchanged source needs a write-through
            // fallback for a defensive-copy getter; never overwrite an unexpected state.
            consumer.consume(slots[index], debits[index]);
            final ItemStack observed = menu.getItemInSlot(slots[index]);
            if (same(before[index], observed)) {
                menu.replaceExistingItem(slots[index], snapshot(expected[index]));
            } else if (!same(expected[index], observed)) {
                throw new IllegalStateException("IE2 source consumption changed an unexpected quantity or identity");
            }
            if (!same(expected[index], menu.getItemInSlot(slots[index]))) {
                throw new IllegalStateException("IE2 output setter did not commit the exact debit");
            }
        }
        menu.markDirty();
        // Later setters and dirty callbacks can restore a previously checked slot.
        // Check the entire advertised output set before returning any credit.
        verify(menu, slots, expected);
        return receipt;
    }

    private static void verify(BlockMenu menu, int[] slots, ItemStack[] expected) {
        for (int index = 0; index < slots.length; index++) {
            if (!same(expected[index], menu.getItemInSlot(slots[index]))) {
                throw new IllegalStateException("IE2 output matrix failed withdrawal readback at slot " + slots[index]);
            }
        }
    }

    private static @Nullable ItemStack snapshot(@Nullable ItemStack source) {
        if (empty(source)) return null;
        final ItemStack cloned = source.clone();
        if (cloned != source && same(source, cloned)) return cloned;
        // Some SlimefunItemStack clone implementations rebuild a template. Only
        // accept a fallback copy when its exact original metadata survives.
        final ItemStack detached = new ItemStack(source.getType(), source.getAmount());
        if (source.hasItemMeta() && !detached.setItemMeta(source.getItemMeta())) {
            throw new IllegalStateException("IE2 output metadata could not be copied");
        }
        if (!same(source, detached)) {
            throw new IllegalStateException("IE2 output could not be snapshotted without changing identity");
        }
        return detached;
    }

    private static boolean same(@Nullable ItemStack expected, @Nullable ItemStack actual) {
        if (empty(expected)) return empty(actual);
        return !empty(actual) && expected.getAmount() == actual.getAmount() && expected.isSimilar(actual);
    }

    private static boolean empty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }
}

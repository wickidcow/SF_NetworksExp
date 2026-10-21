package com.ytdd9527.networksexpansion.implementation.machines.unit;

import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Exact, long-safe planning and receipt checks for the Drawer Quantum GUI. */
final class DrawerQuantumTransferMath {
    private DrawerQuantumTransferMath() {
    }

    static int fromQuantum(long stored, int drawerAmount, int drawerMax) {
        if (stored <= 0 || drawerAmount < 0 || drawerMax < drawerAmount) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, Math.min(stored, (long) drawerMax - drawerAmount));
    }

    static int toQuantum(int drawerAmount, long stored, long limit) {
        if (drawerAmount <= 0 || stored < 0 || limit < stored) {
            return 0;
        }
        return (int) Math.min((long) drawerAmount,
            Math.min(Integer.MAX_VALUE, limit - stored));
    }

    static long remainingAfterDebit(long before, int debit) {
        if (before < 0 || debit < 0 || (long) debit > before) {
            throw new IllegalArgumentException("Unverifiable Quantum source debit");
        }
        return before - debit;
    }

    static long creditedAfterReceipt(long before, long limit, int planned, int actuallyWithdrawn) {
        if (before < 0 || limit < before || planned < 0 || actuallyWithdrawn < 0
            || actuallyWithdrawn > planned || (long) actuallyWithdrawn > limit - before) {
            throw new IllegalArgumentException("Unverifiable Drawer source receipt");
        }
        return before + actuallyWithdrawn;
    }

    /** Validates the exact offered stack while the Drawer retains source ownership. */
    static int verifiedReceipt(@NotNull ItemStack template, @Nullable ItemStack delivered, int planned) {
        if (delivered == null) {
            return 0;
        }
        if (planned <= 0 || delivered.getAmount() <= 0 || delivered.getAmount() > planned
            || !template.isSimilar(delivered)) {
            throw new IllegalStateException("Drawer withdrawal has unverified item identity or amount");
        }
        return delivered.getAmount();
    }

    /** Preserve the old Quantum template, void flag and custom-limit support. */
    static @NotNull QuantumCache rebalanced(@NotNull QuantumCache old, long newBalance) {
        final ItemStack exactTemplate = old.getItemStack();
        if (exactTemplate == null || newBalance < 0 || newBalance > old.getLimitLong()) {
            throw new IllegalStateException("Cannot rebalance an unassigned Quantum item");
        }
        return new QuantumCache(exactTemplate.clone(), newBalance, old.getLimitLong(),
            old.isVoidExcess(), old.supportsCustomMaxAmount());
    }
}

package com.ytdd9527.networksexpansion.core.items.machines;

/**
 * Pure batch-size calculation shared by Auto Crafter runtime code and regression tests.
 *
 * <p>Keeping this calculation independent from Bukkit/Slimefun classes lets the three-core
 * compatibility matrix test the original stacked-blueprint contract without bootstrapping a server.</p>
 */
final class AutoCrafterBatchPlanner {

    private AutoCrafterBatchPlanner() {
    }

    static int calculateSafeBatchSize(
        int requestedCrafts,
        int outputPerCraft,
        int maxStackSize,
        int currentOutputAmount) {

        if (requestedCrafts <= 0 || outputPerCraft <= 0 || maxStackSize <= 0) {
            return 0;
        }

        final int occupied = Math.max(0, Math.min(currentOutputAmount, maxStackSize));
        final int remainingRoom = maxStackSize - occupied;
        if (remainingRoom < outputPerCraft) {
            return 0;
        }

        return Math.min(requestedCrafts, remainingRoom / outputPerCraft);
    }
}

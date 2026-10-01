package io.github.sefiraat.networks.slimefun.network.pusher;

final class RecipeAwarePusherPlanner {

    private RecipeAwarePusherPlanner() {
    }

    static int calculateTransferAmount(
        int requiredPerBatch,
        int bufferedBatches,
        int existingAmount,
        int insertCapacity) {

        if (requiredPerBatch <= 0 || bufferedBatches <= 0 || insertCapacity <= 0) {
            return 0;
        }

        final long desiredLong = (long) requiredPerBatch * bufferedBatches;
        final int desired = desiredLong >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) desiredLong;
        final int existing = Math.max(0, existingAmount);

        if (existing >= desired) {
            return 0;
        }

        return Math.min(desired - existing, insertCapacity);
    }
}

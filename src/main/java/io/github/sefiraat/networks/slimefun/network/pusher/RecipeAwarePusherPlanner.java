package io.github.sefiraat.networks.slimefun.network.pusher;

final class RecipeAwarePusherPlanner {

    static final int MIN_BUFFERED_BATCHES = 1;
    static final int MAX_BUFFERED_BATCHES = 64;

    private RecipeAwarePusherPlanner() {
    }

    static boolean isValidBufferedBatches(int value) {
        return value >= MIN_BUFFERED_BATCHES && value <= MAX_BUFFERED_BATCHES;
    }

    static int sanitizeBufferedBatches(int value, int fallback) {
        if (isValidBufferedBatches(value)) {
            return value;
        }
        if (isValidBufferedBatches(fallback)) {
            return fallback;
        }
        return MIN_BUFFERED_BATCHES;
    }

    static int stepBufferedBatches(int current, int delta) {
        final int normalized = sanitizeBufferedBatches(current, MIN_BUFFERED_BATCHES);
        final int range = MAX_BUFFERED_BATCHES - MIN_BUFFERED_BATCHES + 1;
        return MIN_BUFFERED_BATCHES
            + Math.floorMod((normalized - MIN_BUFFERED_BATCHES) + delta, range);
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

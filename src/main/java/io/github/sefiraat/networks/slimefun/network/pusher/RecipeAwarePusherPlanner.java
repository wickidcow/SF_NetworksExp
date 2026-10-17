package io.github.sefiraat.networks.slimefun.network.pusher;

final class RecipeAwarePusherPlanner {

    static final int MIN_BUFFERED_BATCHES = 1;
    static final int MAX_BUFFERED_BATCHES = 64;

    private RecipeAwarePusherPlanner() {
    }

    static boolean isValidBufferedBatches(int value) {
        return value >= MIN_BUFFERED_BATCHES && value <= MAX_BUFFERED_BATCHES;
    }

    /** Parse only a short ASCII decimal integer, never arbitrary player math expressions. */
    static int parseBufferedBatches(String raw) {
        if (raw == null || raw.length() > 16) {
            throw new NumberFormatException("Invalid Recipe-Aware batch target");
        }

        final String value = raw.trim();
        if (value.isEmpty() || value.length() > 2) {
            throw new NumberFormatException("Enter a number from 1 to 64");
        }

        int parsed = 0;
        for (int i = 0; i < value.length(); i++) {
            final char ch = value.charAt(i);
            if (ch < '0' || ch > '9') {
                throw new NumberFormatException("Only decimal digits are allowed");
            }
            parsed = parsed * 10 + (ch - '0');
        }
        if (!isValidBufferedBatches(parsed)) {
            throw new NumberFormatException("Batch target must be from 1 to 64");
        }
        return parsed;
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

    /**
     * Hold an ingredient at one batch while another template has no physical stack.
     * Once all types are represented, return to the configured multi-batch target.
     */
    static int calculateFairTransferAmount(
        int requiredPerBatch,
        int bufferedBatches,
        int existingAmount,
        int insertCapacity,
        boolean anotherIngredientMissing) {
        return calculateTransferAmount(
            requiredPerBatch,
            anotherIngredientMissing ? MIN_BUFFERED_BATCHES : bufferedBatches,
            existingAmount,
            insertCapacity);
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

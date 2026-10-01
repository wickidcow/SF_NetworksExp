package io.github.sefiraat.networks.integrations.infinityexpansion2;

import java.util.Arrays;

/** Preserves the first occurrence of each legal chest slot without stream/boxed-distinct work. */
final class StorageSlotLayout {

    private static final int SLOT_COUNT = 54;

    private StorageSlotLayout() {
    }

    static int[] sanitize(int[] slots) {
        if (slots == null || slots.length == 0) {
            return new int[0];
        }
        final int[] result = new int[Math.min(slots.length, SLOT_COUNT)];
        long seen = 0L;
        int size = 0;
        for (int slot : slots) {
            if (slot >= 0 && slot < SLOT_COUNT) {
                final long bit = 1L << slot;
                if ((seen & bit) == 0L) {
                    seen |= bit;
                    result[size++] = slot;
                }
            }
        }
        // Always return detached storage, even when the supplied layout was already valid.
        return size == result.length ? result : Arrays.copyOf(result, size);
    }
}

package com.ytdd9527.networksexpansion.core.items.machines;

/**
 * Spreads infrequent transfers from a densely built factory across Slimefun
 * ticks rather than triggering all machines on the same startup cycle.
 *
 * <p>The rate is not changed: every configured transfer still attempts its
 * work once per {@code interval} ticks. The phase affects only the first
 * attempt after runtime cache initialization and keeps push and grab phases
 * aligned for bidirectional machines when their intervals match.</p>
 */
public final class TransferTickPhases {
    private TransferTickPhases() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Deterministic phase for a machine position and configured interval.
     *
     * <p>Odd, relatively prime coefficients ensure neighboring x/z positions
     * and vertical stacks distribute evenly when a server chooses common
     * intervals such as two, three, or four ticks.</p>
     */
    public static int initialCounter(int x, int y, int z, int interval, boolean staggerEnabled) {
        if (!staggerEnabled || interval <= 1) {
            return 0;
        }
        return Math.floorMod(x + 31 * y + 131 * z, interval);
    }
}

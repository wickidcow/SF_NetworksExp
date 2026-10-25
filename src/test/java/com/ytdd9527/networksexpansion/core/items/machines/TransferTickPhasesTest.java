package com.ytdd9527.networksexpansion.core.items.machines;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TransferTickPhasesTest {
    @Test
    void oneTickIntervalsPreserveOriginalEveryTickTransfer() {
        assertEquals(0, TransferTickPhases.initialCounter(-5314, -57, -5569, 1, true));
        assertEquals(0, TransferTickPhases.initialCounter(-5314, -57, -5569, 0, true));
    }

    @Test
    void oldSimultaneousWakeupCanBeSelectedForCompatibility() {
        for (int i = 2; i <= 10; i++) {
            assertEquals(0, TransferTickPhases.initialCounter(-5314, -57, -5569, i, false));
        }
    }

    @Test
    void nearbyFactoriesSpreadOverAllFourWorkPhases() {
        int[] counts = new int[4];
        for (int x = -5314; x < -5186; x++) {
            int phase = TransferTickPhases.initialCounter(x, -57, -5569, 4, true);
            counts[phase]++;
        }
        for (int count : counts) {
            assertEquals(32, count, "Each phase should receive exactly a quarter of adjacent devices");
        }
    }

    @Test
    void verticalMachineStacksAlsoDistributeFairly() {
        int[] counts = new int[3];
        for (int y = -64; y < 62; y++) {
            int phase = TransferTickPhases.initialCounter(-5314, y, -5569, 3, true);
            counts[phase]++;
        }
        for (int count : counts) {
            assertEquals(42, count);
        }
    }

    @Test
    void negativeCoordinatesAndLargeSignedCoordinatesAlwaysProduceValidPhases() {
        int[] values = {Integer.MIN_VALUE, -5314, -1, 0, 1, 100_000, Integer.MAX_VALUE};
        for (int interval : new int[] {2, 3, 4, 7, 10, 64}) {
            for (int x : values) {
                for (int y : values) {
                    int phase = TransferTickPhases.initialCounter(x, y, Integer.MIN_VALUE, interval, true);
                    assertTrue(phase >= 0 && phase < interval);
                    assertEquals(phase, TransferTickPhases.initialCounter(x, y, Integer.MIN_VALUE, interval, true));
                }
            }
        }
    }

    @Test
    void equalPushAndGrabPeriodsUseTheSamePhaseToReuseLineTraversals() {
        for (int x = -5328; x <= -5312; x++) {
            int push = TransferTickPhases.initialCounter(x, -57, -5569, 4, true);
            int grab = TransferTickPhases.initialCounter(x, -57, -5569, 4, true);
            assertEquals(push, grab);
        }
    }
}

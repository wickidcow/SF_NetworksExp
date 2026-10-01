package io.github.sefiraat.networks.integrations.infinityexpansion2;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Random;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

class StorageSlotLayoutTest {

    @Test
    void acceptsNullEmptyAndCompletelyInvalidLayouts() {
        assertArrayEquals(new int[0], StorageSlotLayout.sanitize(null));
        assertArrayEquals(new int[0], StorageSlotLayout.sanitize(new int[0]));
        assertArrayEquals(new int[0], StorageSlotLayout.sanitize(new int[] {-1, 54, Integer.MIN_VALUE, Integer.MAX_VALUE}));
    }

    @Test
    void retainsFirstOccurrenceOrderRatherThanSortingTransportSlots() {
        assertArrayEquals(new int[] {53, 9, 0, 10, 1, 2, 52},
            StorageSlotLayout.sanitize(new int[] {53, 9, 0, 10, 9, 1, 0, 2, 53, 52}));
    }

    @Test
    void preservesUpperBitsAndRejectsValuesThatCouldWrapABitShift() {
        assertArrayEquals(new int[] {0, 31, 32, 53, 52},
            StorageSlotLayout.sanitize(new int[] {-64, 64, 0, 31, 32, 53, 54, 52, 117, -11, 53}));
    }

    @Test
    void neverReturnsOrMutatesTheAddonOwnedArray() {
        int[] original = {9, 0, 53};
        int[] result = StorageSlotLayout.sanitize(original);
        assertNotSame(original, result);
        result[0] = 42;
        assertArrayEquals(new int[] {9, 0, 53}, original);
        original[1] = 17;
        assertEquals(0, result[1]);
    }

    @Test
    void readsChangedLayoutsAgainRatherThanCachingMutableValues() {
        int[] original = {9, 0, 53, 9};
        assertArrayEquals(new int[] {9, 0, 53}, StorageSlotLayout.sanitize(original));
        original[0] = 7;
        assertArrayEquals(new int[] {7, 0, 53, 9}, StorageSlotLayout.sanitize(original));
    }

    @Test
    void boundsResultsForVeryLargeLayoutsAndPreservesAllFiftyFourSlots() {
        int[] descending = IntStream.range(0, 54).map(index -> 53 - index).toArray();
        int[] repeated = new int[100_000];
        for (int index = 0; index < repeated.length; index++) repeated[index] = descending[index % 54];
        assertArrayEquals(descending, StorageSlotLayout.sanitize(repeated));
    }

    @Test
    void matchesTheOriginalStreamForTenThousandDeterministicMixedLayouts() {
        var random = new Random(0x53464c4eL);
        for (int iteration = 0; iteration < 10_000; iteration++) {
            int[] original = new int[random.nextInt(513)];
            for (int index = 0; index < original.length; index++) {
                original[index] = random.nextBoolean() ? random.nextInt(160) - 50 : random.nextInt();
            }
            int[] preserved = original.clone();
            int[] expected = Arrays.stream(original).filter(slot -> slot >= 0 && slot < 54).distinct().toArray();
            assertArrayEquals(expected, StorageSlotLayout.sanitize(original), "Layout " + iteration);
            assertArrayEquals(preserved, original);
        }
    }
}

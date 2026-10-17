package io.github.sefiraat.networks.slimefun.network.pusher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RecipeAwarePusherPlannerTest {

    @Test
    void validatesPerMachineBufferRange() {
        assertEquals(true, RecipeAwarePusherPlanner.isValidBufferedBatches(1));
        assertEquals(true, RecipeAwarePusherPlanner.isValidBufferedBatches(64));
        assertEquals(false, RecipeAwarePusherPlanner.isValidBufferedBatches(0));
        assertEquals(false, RecipeAwarePusherPlanner.isValidBufferedBatches(65));
    }

    @Test
    void chatBatchTargetsAcceptOnlySmallDecimalIntegers() {
        assertEquals(1, RecipeAwarePusherPlanner.parseBufferedBatches("1"));
        assertEquals(64, RecipeAwarePusherPlanner.parseBufferedBatches("64"));
        assertEquals(8, RecipeAwarePusherPlanner.parseBufferedBatches(" 8 "));

        for (String invalid : new String[] {
            "", "0", "65", "-1", "+1", "1+1", "2**1000000", "8k",
            "1.0", "99999999999999999999999", "eight", "  "
        }) {
            assertThrows(NumberFormatException.class,
                () -> RecipeAwarePusherPlanner.parseBufferedBatches(invalid), invalid);
        }
        assertThrows(NumberFormatException.class,
            () -> RecipeAwarePusherPlanner.parseBufferedBatches(null));
    }

    @Test
    void fallsBackForInvalidStoredBufferValues() {
        assertEquals(8, RecipeAwarePusherPlanner.sanitizeBufferedBatches(0, 8));
        assertEquals(8, RecipeAwarePusherPlanner.sanitizeBufferedBatches(65, 8));
        assertEquals(1, RecipeAwarePusherPlanner.sanitizeBufferedBatches(0, 0));
    }

    @Test
    void quickBatchAdjustmentWrapsWithinOneToSixtyFour() {
        assertEquals(9, RecipeAwarePusherPlanner.stepBufferedBatches(8, 1));
        assertEquals(7, RecipeAwarePusherPlanner.stepBufferedBatches(8, -1));
        assertEquals(1, RecipeAwarePusherPlanner.stepBufferedBatches(64, 1));
        assertEquals(64, RecipeAwarePusherPlanner.stepBufferedBatches(1, -1));
    }

    @Test
    void topsUpConfiguredRecipeBuffer() {
        assertEquals(12, RecipeAwarePusherPlanner.calculateTransferAmount(2, 8, 4, 64));
    }

    @Test
    void stopsWhenIngredientBufferIsSatisfied() {
        assertEquals(0, RecipeAwarePusherPlanner.calculateTransferAmount(2, 8, 16, 64));
    }

    @Test
    void respectsDestinationCapacity() {
        assertEquals(3, RecipeAwarePusherPlanner.calculateTransferAmount(2, 8, 0, 3));
    }

    @Test
    void handlesSingleIngredientRecipes() {
        assertEquals(5, RecipeAwarePusherPlanner.calculateTransferAmount(1, 8, 3, 64));
    }

    @Test
    void rejectsInvalidOrFullDestinations() {
        assertEquals(0, RecipeAwarePusherPlanner.calculateTransferAmount(2, 8, 0, 0));
        assertEquals(0, RecipeAwarePusherPlanner.calculateTransferAmount(0, 8, 0, 64));
    }

    @Test
    void saturatesLargeRecipeTargetsWithoutOverflow() {
        assertEquals(64, RecipeAwarePusherPlanner.calculateTransferAmount(Integer.MAX_VALUE, 64, 0, 64));
    }
}

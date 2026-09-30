package io.github.sefiraat.networks.slimefun.network.pusher;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class RecipeAwarePusherPlannerTest {

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

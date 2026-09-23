package com.ytdd9527.networksexpansion.core.items.machines;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AutoCrafterBatchTest {

    @Test
    void craftsFullStackForSingleOutputRecipes() {
        assertEquals(64, AutoCrafter.calculateSafeBatchSize(64, 1, 64, 0));
    }

    @Test
    void clampsMultiOutputRecipesToOneLegalStack() {
        assertEquals(16, AutoCrafter.calculateSafeBatchSize(64, 4, 64, 0));
    }

    @Test
    void fillsRemainingWithholdingOutputSpace() {
        assertEquals(6, AutoCrafter.calculateSafeBatchSize(64, 4, 64, 40));
    }

    @Test
    void keepsNormalCrafterAtOneRecipe() {
        assertEquals(1, AutoCrafter.calculateSafeBatchSize(1, 1, 64, 0));
    }

    @Test
    void refusesCraftWhenNoCompleteRecipeOutputFits() {
        assertEquals(0, AutoCrafter.calculateSafeBatchSize(64, 4, 64, 62));
    }
}

package com.ytdd9527.networksexpansion.implementation.machines.manual;

import org.bukkit.DyeColor;
import org.bukkit.entity.TropicalFish;
import org.bukkit.inventory.meta.TropicalFishBucketMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Read-only comparison regressions using the real ItemDifferenter entry point. */
class ItemDifferenterTropicalFishTest {
    @BeforeEach
    void startServerBoundary() {
        MockBukkit.mock();
    }

    @AfterEach
    void stopServerBoundary() {
        MockBukkit.unmock();
    }

    @Test
    void twoAbsentVariantsMustNotReadUndefinedFields() {
        TropicalFishBucketMeta first = absent();
        TropicalFishBucketMeta second = absent();
        assertNull(ItemDifferenter.canQuickEscapeMetaVariant(first, second));
        verify(first, never()).getPattern();
        verify(first, never()).getBodyColor();
        verify(first, never()).getPatternColor();
        verify(second, never()).getPattern();
        verify(second, never()).getBodyColor();
        verify(second, never()).getPatternColor();
    }

    @Test
    void unequalPresenceKeepsTheExistingDiagnostic() {
        assertEquals("neq.tropical.variant.has",
            ItemDifferenter.canQuickEscapeMetaVariant(absent(), complete()));
        assertEquals("neq.tropical.variant.has",
            ItemDifferenter.canQuickEscapeMetaVariant(complete(), absent()));
    }

    @Test
    void identicalCompleteVariantsKeepTheExistingResult() {
        assertNull(ItemDifferenter.canQuickEscapeMetaVariant(complete(), complete()));
    }

    private static TropicalFishBucketMeta absent() {
        TropicalFishBucketMeta meta = mock(TropicalFishBucketMeta.class);
        when(meta.getPattern()).thenThrow(new IllegalStateException("Pattern is absent"));
        when(meta.getBodyColor()).thenThrow(new IllegalStateException("Body color is absent"));
        when(meta.getPatternColor()).thenThrow(new IllegalStateException("Pattern color is absent"));
        return meta;
    }

    private static TropicalFishBucketMeta complete() {
        TropicalFishBucketMeta meta = mock(TropicalFishBucketMeta.class);
        when(meta.hasVariant()).thenReturn(true);
        when(meta.getPattern()).thenReturn(TropicalFish.Pattern.KOB);
        when(meta.getBodyColor()).thenReturn(DyeColor.ORANGE);
        when(meta.getPatternColor()).thenReturn(DyeColor.WHITE);
        return meta;
    }
}

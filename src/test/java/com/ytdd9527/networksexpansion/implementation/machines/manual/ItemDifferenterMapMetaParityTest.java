package com.ytdd9527.networksexpansion.implementation.machines.manual;

import org.bukkit.Color;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.map.MapView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Pin the actual 1.21.11 map comparison contract before considering a 26.3-only API.
 * Mocked metadata here does not prove historical item deserialization or rendering.
 */
class ItemDifferenterMapMetaParityTest {
    @BeforeEach
    void initializeServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void closeServer() {
        MockBukkit.unmock();
    }


    @Test
    void absentViewsAndColorsRemainEquivalent() {
        assertNull(ItemDifferenter.canQuickEscapeMetaVariant(
            map(false, null, false, null), map(false, null, false, null)));
    }

    @Test
    void mapViewPresenceMismatchTakesPriorityOverColor() {
        MapMeta first = map(true, mock(MapView.class), false, null);
        MapMeta second = map(false, null, true, Color.RED);

        assertEquals("neq.map.view.has", ItemDifferenter.canQuickEscapeMetaVariant(first, second));
        verify(first, never()).getMapView();
        verify(first, never()).hasColor();
        verify(second, never()).getColor();
    }

    @Test
    void colorPresenceMismatchIsNotSilentlyIgnoredOnTheBaseline() {
        MapMeta first = map(false, null, true, Color.GREEN);
        MapMeta second = map(false, null, false, null);

        assertEquals("neq.map.color.has", ItemDifferenter.canQuickEscapeMetaVariant(first, second));
        verify(first, never()).getMapView();
        verify(first, never()).getColor();
    }

    @Test
    void differentMapViewsRemainDistinctBeforeColorValueComparison() {
        MapMeta first = map(true, mock(MapView.class), true, Color.RED);
        MapMeta second = map(true, mock(MapView.class), true, Color.BLUE);

        assertEquals("neq.map.view.get", ItemDifferenter.canQuickEscapeMetaVariant(first, second));
        verify(first, never()).getColor();
        verify(second, never()).getColor();
    }

    @Test
    void differentHistoricalMapTintsRemainDistinct() {
        MapMeta first = map(false, null, true, Color.RED);
        MapMeta second = map(false, null, true, Color.GREEN);

        assertEquals("neq.map.color.get", ItemDifferenter.canQuickEscapeMetaVariant(first, second));
    }

    @Test
    void matchingMapViewAndMatchingTintRemainEquivalent() {
        MapView originalView = mock(MapView.class);
        assertNull(ItemDifferenter.canQuickEscapeMetaVariant(
            map(true, originalView, true, Color.GREEN),
            map(true, originalView, true, Color.GREEN)));
    }

    @Test
    void unreadablePresentTintFailsInsteadOfFabricatingItemEquality() {
        MapMeta first = map(false, null, true, Color.RED);
        MapMeta unreadable = map(false, null, true, Color.GREEN);
        when(unreadable.getColor()).thenThrow(new IllegalStateException("Historical tint cannot be read"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> ItemDifferenter.canQuickEscapeMetaVariant(first, unreadable));
        assertEquals("Historical tint cannot be read", failure.getMessage());
    }

    private static MapMeta map(boolean hasView, MapView view, boolean hasColor, Color color) {
        MapMeta result = mock(MapMeta.class);
        when(result.hasMapView()).thenReturn(hasView);
        when(result.getMapView()).thenReturn(view);
        when(result.hasColor()).thenReturn(hasColor);
        when(result.getColor()).thenReturn(color);
        return result;
    }
}

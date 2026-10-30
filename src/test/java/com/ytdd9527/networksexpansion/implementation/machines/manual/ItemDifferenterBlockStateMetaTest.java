package com.ytdd9527.networksexpansion.implementation.machines.manual;

import org.bukkit.block.BlockState;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Preserve Item Differenter block-state diagnostics without reading undefined
 * metadata. These mocked API boundaries are not a saved-world recovery test.
 */
class ItemDifferenterBlockStateMetaTest {
    @BeforeEach
    void startServerBoundary() {
        MockBukkit.mock();
    }

    @AfterEach
    void stopServerBoundary() {
        MockBukkit.unmock();
    }

    @Test
    void twoAbsentBlockStatesAreEquivalentWithoutReadingUndefinedValues() {
        BlockStateMeta first = absent();
        BlockStateMeta second = absent();

        assertNull(ItemDifferenter.canQuickEscapeMetaVariant(first, second));
        verify(first, never()).getBlockState();
        verify(second, never()).getBlockState();
    }

    @Test
    void oneAbsentBlockStateKeepsThePresenceDiagnosticInBothOrders() {
        BlockStateMeta absent = absent();
        BlockStateMeta present = present(mock(BlockState.class));

        assertEquals("neq.blockstate.has",
            ItemDifferenter.canQuickEscapeMetaVariant(absent, present));
        assertEquals("neq.blockstate.has",
            ItemDifferenter.canQuickEscapeMetaVariant(present, absent));
        verify(absent, never()).getBlockState();
        verify(present, never()).getBlockState();
    }

    @Test
    void identicalPresentBlockStatesRemainEquivalent() {
        BlockState state = mock(BlockState.class);

        assertNull(ItemDifferenter.canQuickEscapeMetaVariant(
            present(state), present(state)));
    }

    @Test
    void distinctPresentBlockStatesKeepTheValueDiagnostic() {
        assertEquals("neq.blockstate.get",
            ItemDifferenter.canQuickEscapeMetaVariant(
                present(mock(BlockState.class)), present(mock(BlockState.class))));
    }

    @Test
    void unreadablePresentBlockStateStillFailsRatherThanMatching() {
        BlockStateMeta first = present(mock(BlockState.class));
        BlockStateMeta unreadable = present(mock(BlockState.class));
        when(unreadable.getBlockState()).thenThrow(
            new IllegalStateException("Unreadable saved block state"));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> ItemDifferenter.canQuickEscapeMetaVariant(first, unreadable));
        assertEquals("Unreadable saved block state", failure.getMessage());
    }

    private static BlockStateMeta absent() {
        BlockStateMeta meta = mock(BlockStateMeta.class);
        when(meta.getBlockState()).thenThrow(
            new IllegalStateException("Absent block state"));
        return meta;
    }

    private static BlockStateMeta present(BlockState state) {
        BlockStateMeta meta = mock(BlockStateMeta.class);
        when(meta.hasBlockState()).thenReturn(true);
        when(meta.getBlockState()).thenReturn(state);
        return meta;
    }
}

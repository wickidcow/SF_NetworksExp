package com.ytdd9527.networksexpansion.implementation.machines.manual;

import org.bukkit.entity.Axolotl;
import org.bukkit.inventory.meta.AxolotlBucketMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Preserve the existing ItemDifferenter diagnostic contract for axolotl bucket metadata. */
class ItemDifferenterAxolotlVariantTest {
    @BeforeEach
    void startServerBoundary() {
        MockBukkit.mock();
    }

    @AfterEach
    void stopServerBoundary() {
        MockBukkit.unmock();
    }

    @Test
    void twoAbsentVariantsDoNotMismatchOrReadUndefinedValues() {
        AxolotlBucketMeta first = absent();
        AxolotlBucketMeta second = absent();
        assertNull(ItemDifferenter.canQuickEscapeMetaVariant(first, second));
        verify(first, never()).getVariant();
        verify(second, never()).getVariant();
    }

    @Test
    void oneMissingVariantRetainsPresenceDiagnosticWithoutReadingIt() {
        AxolotlBucketMeta first = absent();
        AxolotlBucketMeta second = present(Axolotl.Variant.LUCY);
        assertEquals("neq.axolotl.variant.has",
            ItemDifferenter.canQuickEscapeMetaVariant(first, second));
        assertEquals("neq.axolotl.variant.has",
            ItemDifferenter.canQuickEscapeMetaVariant(second, first));
        verify(first, never()).getVariant();
        verify(second, never()).getVariant();
    }

    @Test
    void identicalPresentVariantsRemainEqual() {
        assertNull(ItemDifferenter.canQuickEscapeMetaVariant(
            present(Axolotl.Variant.LUCY), present(Axolotl.Variant.LUCY)));
    }

    @Test
    void distinctPresentVariantsRetainValueDiagnostic() {
        assertEquals("neq.axolotl.variant.get",
            ItemDifferenter.canQuickEscapeMetaVariant(
                present(Axolotl.Variant.LUCY), present(Axolotl.Variant.WILD)));
    }

    private static AxolotlBucketMeta absent() {
        AxolotlBucketMeta meta = mock(AxolotlBucketMeta.class);
        when(meta.getVariant()).thenThrow(new IllegalStateException("Absent axolotl variant"));
        return meta;
    }

    private static AxolotlBucketMeta present(Axolotl.Variant variant) {
        AxolotlBucketMeta meta = mock(AxolotlBucketMeta.class);
        when(meta.hasVariant()).thenReturn(true);
        when(meta.getVariant()).thenReturn(variant);
        return meta;
    }
}

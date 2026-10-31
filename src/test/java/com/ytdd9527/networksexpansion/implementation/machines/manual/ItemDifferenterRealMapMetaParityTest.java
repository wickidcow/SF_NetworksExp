package com.ytdd9527.networksexpansion.implementation.machines.manual;

import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.MapMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercise the 1.21.11 item-meta copy boundary with actual ItemStacks, rather
 * than only mocked MapMeta methods. This is not a historical-world or 26.3
 * deserialization test; the legacy map-tint reader must remain until those
 * separate compatibility gates are demonstrated.
 */
class ItemDifferenterRealMapMetaParityTest {
    @BeforeEach
    void startServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void distinctColorsRemainDistinctAfterItemStackClone() {
        MapMeta red = copiedMeta(tintedMap(Color.RED));
        MapMeta green = copiedMeta(tintedMap(Color.GREEN));

        assertTrue(red.hasColor());
        assertTrue(green.hasColor());
        assertEquals(Color.RED, red.getColor());
        assertEquals(Color.GREEN, green.getColor());
        assertEquals("neq.map.color.get", ItemDifferenter.canQuickEscapeMetaVariant(red, green));
    }

    @Test
    void absentTintRemainsDifferentFromPresentTintAfterCopy() {
        MapMeta absent = copiedMeta(new ItemStack(Material.FILLED_MAP));
        MapMeta green = copiedMeta(tintedMap(Color.GREEN));

        assertFalse(absent.hasColor());
        assertTrue(green.hasColor());
        assertEquals("neq.map.color.has", ItemDifferenter.canQuickEscapeMetaVariant(absent, green));
    }

    @Test
    void equalTintsRemainEquivalentAfterCopy() {
        MapMeta first = copiedMeta(tintedMap(Color.GREEN));
        MapMeta second = copiedMeta(tintedMap(Color.GREEN));

        assertNull(ItemDifferenter.canQuickEscapeMetaVariant(first, second));
    }

    @Test
    void distinctHistoricalTintsRemainDistinctAfterBinaryItemSerialization() {
        MapMeta red = serializedMeta(tintedMap(Color.RED));
        MapMeta green = serializedMeta(tintedMap(Color.GREEN));

        assertTrue(red.hasColor(), "Binary item serialization discarded the red map tint");
        assertTrue(green.hasColor(), "Binary item serialization discarded the green map tint");
        assertEquals(Color.RED, red.getColor());
        assertEquals(Color.GREEN, green.getColor());
        assertEquals("neq.map.color.get", ItemDifferenter.canQuickEscapeMetaVariant(red, green));
    }

    @Test
    void absentAndPresentHistoricalTintsRemainDistinctAfterBinarySerialization() {
        MapMeta absent = serializedMeta(new ItemStack(Material.FILLED_MAP));
        MapMeta green = serializedMeta(tintedMap(Color.GREEN));

        assertFalse(absent.hasColor());
        assertTrue(green.hasColor(), "Binary item serialization discarded the green map tint");
        assertEquals("neq.map.color.has", ItemDifferenter.canQuickEscapeMetaVariant(absent, green));
    }

    @Test
    void equalHistoricalTintsRemainEquivalentAfterBinarySerialization() {
        MapMeta first = serializedMeta(tintedMap(Color.GREEN));
        MapMeta second = serializedMeta(tintedMap(Color.GREEN));

        assertEquals(Color.GREEN, first.getColor());
        assertEquals(Color.GREEN, second.getColor());
        assertNull(ItemDifferenter.canQuickEscapeMetaVariant(first, second));
    }

    // This is a same-version 1.21.11 MockBukkit binary roundtrip, not a Paper 26.3
    // migration, native-world restart, or cross-fork compatibility claim.
    private static MapMeta serializedMeta(ItemStack original) {
        ItemStack restored = ItemStack.deserializeBytes(original.serializeAsBytes());
        MapMeta meta = (MapMeta) restored.getItemMeta();
        assertNotNull(meta);
        return meta;
    }

    private static ItemStack tintedMap(Color color) {
        ItemStack item = new ItemStack(Material.FILLED_MAP);
        MapMeta meta = (MapMeta) item.getItemMeta();
        assertNotNull(meta);
        meta.setColor(color);
        item.setItemMeta(meta);
        return item;
    }

    private static MapMeta copiedMeta(ItemStack original) {
        ItemStack copy = original.clone();
        MapMeta meta = (MapMeta) copy.getItemMeta();
        assertNotNull(meta);
        return meta;
    }
}

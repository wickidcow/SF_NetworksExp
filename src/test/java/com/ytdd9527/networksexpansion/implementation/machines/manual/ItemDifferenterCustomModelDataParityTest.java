package com.ytdd9527.networksexpansion.implementation.machines.manual;

import java.util.List;
import org.bukkit.Color;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Custom-model identity applies to vanilla items as well as Slimefun items.
 * These are metadata comparison tests, not a native item serialization test.
 */
class ItemDifferenterCustomModelDataParityTest {
    @Test
    void absentComponentsMatchWithoutReadingUndefinedValues() {
        ItemMeta first = mock(ItemMeta.class);
        ItemMeta second = mock(ItemMeta.class);

        assertTrue(ItemDifferenter.sameCustomModelDataState(first, second));
        verify(first, never()).getCustomModelDataComponent();
        verify(second, never()).getCustomModelDataComponent();
    }

    @Test
    void presenceMismatchDoesNotReadAnAbsentComponent() {
        ItemMeta present = mock(ItemMeta.class);
        ItemMeta absent = mock(ItemMeta.class);
        when(present.hasCustomModelDataComponent()).thenReturn(true);

        assertFalse(ItemDifferenter.sameCustomModelDataState(present, absent));
        assertFalse(ItemDifferenter.sameCustomModelDataState(absent, present));
        verify(present, never()).getCustomModelDataComponent();
        verify(absent, never()).getCustomModelDataComponent();
    }

    @Test
    void equalComponentsMatchAcrossDifferentMetadataInstances() {
        ItemMeta first = withComponent(component(
            List.of(1.5f), List.of(true), List.of("model:one"), List.of(Color.RED)));
        ItemMeta second = withComponent(component(
            List.of(1.5f), List.of(true), List.of("model:one"), List.of(Color.RED)));

        assertTrue(ItemDifferenter.sameCustomModelDataState(first, second));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void everyComponentChannelCanDistinguishOtherwiseEqualItems(int channel) {
        ItemMeta original = withComponent(component(
            List.of(1.5f), List.of(true), List.of("model:one"), List.of(Color.RED)));
        ItemMeta changed = withComponent(component(
            channel == 0 ? List.of(2.5f) : List.of(1.5f),
            channel == 1 ? List.of(false) : List.of(true),
            channel == 2 ? List.of("model:two") : List.of("model:one"),
            channel == 3 ? List.of(Color.BLUE) : List.of(Color.RED)));

        assertFalse(ItemDifferenter.sameCustomModelDataState(original, changed),
            "Changed custom-model component channel " + channel + " must not match");
        assertFalse(ItemDifferenter.sameCustomModelDataState(changed, original),
            "Custom-model comparison must be symmetric");
    }

    private static ItemMeta withComponent(CustomModelDataComponent component) {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasCustomModelDataComponent()).thenReturn(true);
        when(meta.getCustomModelDataComponent()).thenReturn(component);
        return meta;
    }

    private static CustomModelDataComponent component(
        List<Float> floats, List<Boolean> flags, List<String> strings, List<Color> colors) {
        CustomModelDataComponent component = mock(CustomModelDataComponent.class);
        when(component.getFloats()).thenReturn(floats);
        when(component.getFlags()).thenReturn(flags);
        when(component.getStrings()).thenReturn(strings);
        when(component.getColors()).thenReturn(colors);
        return component;
    }
}

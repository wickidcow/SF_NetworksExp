package com.ytdd9527.networksexpansion.implementation.machines.manual;

import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.core.services.CustomItemDataService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

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

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3})
    void vanillaCalcReportsCustomModelDifferencesOnProductionPath(int channel) {
        ItemMeta original = withComponent(component(
            List.of(1.5f), List.of(true), List.of("model:one"), List.of(Color.RED)));
        ItemMeta changed = withComponent(component(
            channel == 0 ? List.of(2.5f) : List.of(1.5f),
            channel == 1 ? List.of(false) : List.of(true),
            channel == 2 ? List.of("model:two") : List.of("model:one"),
            channel == 3 ? List.of(Color.BLUE) : List.of(Color.RED)));

        assertEquals("neq.custommodeldata", compareVanilla(original, changed));
        assertEquals("neq.custommodeldata", compareVanilla(changed, original));
    }

    @Test
    void vanillaCalcPreservesEqualityWhenModelComponentsMatch() {
        ItemMeta first = withComponent(component(
            List.of(1.5f), List.of(true), List.of("model:one"), List.of(Color.RED)));
        ItemMeta second = withComponent(component(
            List.of(1.5f), List.of(true), List.of("model:one"), List.of(Color.RED)));
        assertEquals("eq", compareVanilla(first, second));
    }

    /**
     * Exercise the public comparator rather than only the component helper.
     * Mocked boundaries isolate the vanilla metadata branch without a live server;
     * this does not establish native item serialization or world persistence.
     */
    private static String compareVanilla(ItemMeta first, ItemMeta second) {
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        for (ItemMeta meta : List.of(first, second)) {
            when(meta.getPersistentDataContainer()).thenReturn(pdc);
            when(meta.getEnchants()).thenReturn(Map.of());
            when(meta.getItemFlags()).thenReturn(Set.of());
        }

        ItemStack left = mock(ItemStack.class);
        ItemStack right = mock(ItemStack.class);
        when(left.getType()).thenReturn(Material.STONE);
        when(right.getType()).thenReturn(Material.STONE);
        when(left.getAmount()).thenReturn(1);
        when(right.getAmount()).thenReturn(1);
        when(left.hasItemMeta()).thenReturn(true);
        when(right.hasItemMeta()).thenReturn(true);
        when(left.getItemMeta()).thenReturn(first);
        when(right.getItemMeta()).thenReturn(second);

        CustomItemDataService service = mock(CustomItemDataService.class);
        when(service.getItemData(first)).thenReturn(Optional.empty());
        when(service.getItemData(second)).thenReturn(Optional.empty());
        try (MockedStatic<Slimefun> slimefun = mockStatic(Slimefun.class);
             MockedStatic<StackUtils> stackUtils = mockStatic(StackUtils.class)) {
            slimefun.when(Slimefun::getItemDataService).thenReturn(service);
            return ItemDifferenter.calc(left, right);
        }
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

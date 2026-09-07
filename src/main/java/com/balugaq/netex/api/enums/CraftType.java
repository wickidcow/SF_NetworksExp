package com.balugaq.netex.api.enums;

import com.balugaq.netex.api.helpers.SupportedAncientAltarRecipes;
import com.balugaq.netex.api.helpers.SupportedArmorForgeRecipes;
import com.balugaq.netex.api.helpers.SupportedCompressorRecipes;
import com.balugaq.netex.api.helpers.SupportedCraftingTableRecipes;
import com.balugaq.netex.api.helpers.SupportedExpansionWorkbenchRecipes;
import com.balugaq.netex.api.helpers.SupportedGrindStoneRecipes;
import com.balugaq.netex.api.helpers.SupportedJuicerRecipes;
import com.balugaq.netex.api.helpers.SupportedMagicWorkbenchRecipes;
import com.balugaq.netex.api.helpers.SupportedOreCrusherRecipes;
import com.balugaq.netex.api.helpers.SupportedPressureChamberRecipes;
import com.balugaq.netex.api.helpers.SupportedQuantumWorkbenchRecipes;
import com.balugaq.netex.api.helpers.SupportedSmelteryRecipes;
import com.balugaq.netex.utils.Lang;
import com.ytdd9527.networksexpansion.core.items.unusable.Blueprint;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import lombok.Getter;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * @author balugaq
 */
@NullMarked
public enum CraftType {
    ANCIENT_ALTAR(SupportedAncientAltarRecipes.getRecipes(), SupportedAncientAltarRecipes::testRecipe),
    ARMOR_FORGE(SupportedArmorForgeRecipes.getRecipes(), SupportedArmorForgeRecipes::testRecipe),
    COMPRESSOR(SupportedCompressorRecipes.getRecipes(), SupportedCompressorRecipes::testRecipe),
    CRAFTING(SupportedCraftingTableRecipes.getRecipes(), SupportedCraftingTableRecipes::testRecipe),
    EXPANSION_WORKBENCH(SupportedExpansionWorkbenchRecipes.getRecipes(), SupportedExpansionWorkbenchRecipes::testRecipe),
    GRIND_STONE(SupportedGrindStoneRecipes.getRecipes(), SupportedGrindStoneRecipes::testRecipe),
    JUICER(SupportedJuicerRecipes.getRecipes(), SupportedJuicerRecipes::testRecipe),
    MAGIC_WORKBENCH(SupportedMagicWorkbenchRecipes.getRecipes(), SupportedMagicWorkbenchRecipes::testRecipe),
    ORE_CRUSHER(SupportedOreCrusherRecipes.getRecipes(), SupportedOreCrusherRecipes::testRecipe),
    PRESSURE_CHAMBER(SupportedPressureChamberRecipes.getRecipes(), SupportedPressureChamberRecipes::testRecipe),
    QUANTUM_WORKBENCH(SupportedQuantumWorkbenchRecipes.getRecipes(), SupportedQuantumWorkbenchRecipes::testRecipe),
    SMELTERY(SupportedSmelteryRecipes.getRecipes(), SupportedSmelteryRecipes::testRecipe);

    private static final Map<CraftType, Set<Map.Entry<ItemStack[], ItemStack>>> map = new HashMap<>();

    @Getter
    private final Set<Map.Entry<ItemStack[], ItemStack>> recipeEntries;
    private final BiPredicate<ItemStack[], ItemStack[]> testRecipe;

    CraftType(Map<ItemStack[], ItemStack> recipes, BiPredicate<ItemStack[], ItemStack[]> testRecipe) {
        this.recipeEntries = recipes.entrySet();
        this.testRecipe = testRecipe;
    }

    public boolean testRecipe(ItemStack[] inputs, ItemStack[] recipe) {
        return testRecipe.test(inputs, recipe);
    }

    public boolean isValidBlueprint(SlimefunItem item) {
        if (item instanceof Blueprint blueprint) {
            return blueprint.craftType() == this;
        }
        return false;
    }

    public void blueprintSetter(ItemStack itemStack, ItemStack[] inputs, ItemStack crafted) {
        Blueprint.setBlueprint(itemStack, inputs, crafted);
    }

    public String translate() {
        return Lang.getString("messages.normal-operation.common.crafter_types." + this.name().toLowerCase());
    }

    @FunctionalInterface
    public interface BlueprintSetter {
        void apply(ItemStack itemStack, ItemStack[] inputs, ItemStack crafted);
    }

    public static Collection<Set<Map.Entry<ItemStack[], ItemStack>>> entries() {
        SupportedCraftingTableRecipes.refreshRecipes();
        return Arrays.stream(values()).map(CraftType::getRecipeEntries).toList();
    }

    public static Map<CraftType, Set<Map.Entry<ItemStack[], ItemStack>>> map() {
        // Networks can enable before addon plugins have finished registering their Slimefun recipes.
        // Refresh the mutable crafting recipe map before every encoder lookup; the helper itself is incremental.
        SupportedCraftingTableRecipes.refreshRecipes();

        if (map.isEmpty()) {
            map.put(ANCIENT_ALTAR, ANCIENT_ALTAR.recipeEntries);
            map.put(ARMOR_FORGE, ARMOR_FORGE.recipeEntries);
            map.put(COMPRESSOR, COMPRESSOR.recipeEntries);
            map.put(CRAFTING, CRAFTING.recipeEntries);
            map.put(EXPANSION_WORKBENCH, EXPANSION_WORKBENCH.recipeEntries);
            map.put(GRIND_STONE, GRIND_STONE.recipeEntries);
            map.put(JUICER, JUICER.recipeEntries);
            map.put(MAGIC_WORKBENCH, MAGIC_WORKBENCH.recipeEntries);
            map.put(ORE_CRUSHER, ORE_CRUSHER.recipeEntries);
            map.put(PRESSURE_CHAMBER, PRESSURE_CHAMBER.recipeEntries);
            map.put(QUANTUM_WORKBENCH, QUANTUM_WORKBENCH.recipeEntries);
            map.put(SMELTERY, SMELTERY.recipeEntries);
        }

        return map;
    }
}

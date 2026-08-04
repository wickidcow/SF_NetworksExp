package com.balugaq.netex.api.helpers;

import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.multiblocks.MultiBlockMachine;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.SlimefunItems;
import io.github.thebusybiscuit.slimefun4.implementation.items.backpacks.SlimefunBackpack;
import lombok.experimental.UtilityClass;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

@SuppressWarnings("JavaExistingMethodCanBeUsed")
@UtilityClass
public final class SupportedCraftingTableRecipes {

    private static final Map<ItemStack[], ItemStack> RECIPES = new LinkedHashMap<>();

    static {
        String id = SlimefunItems.ENHANCED_CRAFTING_TABLE.getItemId();
        SlimefunItem recipeTypeItem = SlimefunItem.getById(id);
        if (recipeTypeItem instanceof MultiBlockMachine mb) {
            boolean isInput = true;
            ItemStack[] input = null;
            ItemStack[] output;
            for (ItemStack[] recipe : mb.getRecipes()) {
                if (isInput) {
                    input = recipe;
                } else {
                    output = recipe;
                    if (input.length != 9) {
                        ItemStack[] newInput = new ItemStack[9];
                        for (int i = 0; i < 9; i++) {
                            if (i < input.length) {
                                newInput[i] = input[i];
                            } else {
                                newInput[i] = null;
                            }
                        }
                        input = newInput;
                    }
                    RECIPES.put(input, output[0]);
                }
                isInput = !isInput;
            }
        }
        for (SlimefunItem item : Slimefun.getRegistry().getEnabledSlimefunItems()) {
            RecipeType recipeType = item.getRecipeType();
            if ((recipeType == RecipeType.ENHANCED_CRAFTING_TABLE) && allowedRecipe(item)) {
                ItemStack[] itemStacks = new ItemStack[9];
                int i = 0;
                for (ItemStack itemStack : item.getRecipe()) {
                    if (itemStack == null) {
                        itemStacks[i] = null;
                    } else {
                        itemStacks[i] = new ItemStack(itemStack.clone());
                    }
                    if (++i >= 9) {
                        break;
                    }
                }
                SupportedCraftingTableRecipes.addRecipe(itemStacks, item.getRecipeOutput());
            }
        }
    }

    public static @NotNull Map<ItemStack[], ItemStack> getRecipes() {
        return RECIPES;
    }

    public static void addRecipe(@NotNull ItemStack[] input, @NotNull ItemStack output) {
        RECIPES.put(copyRecipe(input), output.clone());
    }

    /**
     * Finds one exact nine-slot recipe match and returns a defensive snapshot binding the input
     * matrix to its corresponding output. This avoids partial-recipe and wrong-output matches when
     * multiple recipes share similar ingredients.
     */
    public static @Nullable RecipeMatch findRecipe(@NotNull ItemStack[] input) {
        for (Map.Entry<ItemStack[], ItemStack> entry : RECIPES.entrySet()) {
            if (testRecipe(input, entry.getKey())) {
                return new RecipeMatch(entry.getKey(), entry.getValue());
            }
        }
        return null;
    }

    public static boolean testRecipe(@NotNull ItemStack[] input, @NotNull ItemStack @NotNull [] recipe) {
        int slots = Math.max(input.length, recipe.length);
        for (int slot = 0; slot < slots; slot++) {
            ItemStack supplied = slot < input.length ? input[slot] : null;
            ItemStack required = slot < recipe.length ? recipe[slot] : null;

            boolean suppliedEmpty = isEmpty(supplied);
            boolean requiredEmpty = isEmpty(required);
            if (suppliedEmpty || requiredEmpty) {
                if (suppliedEmpty != requiredEmpty) {
                    return false;
                }
                continue;
            }

            if (!StackUtils.itemsMatch(supplied, required)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    private static ItemStack[] copyRecipe(ItemStack[] recipe) {
        ItemStack[] copy = new ItemStack[recipe.length];
        for (int i = 0; i < recipe.length; i++) {
            copy[i] = recipe[i] == null ? null : recipe[i].clone();
        }
        return copy;
    }

    public record RecipeMatch(ItemStack[] recipe, ItemStack output) {
        public RecipeMatch {
            recipe = copyRecipe(recipe);
            output = output.clone();
        }

        @Override
        public ItemStack[] recipe() {
            return copyRecipe(recipe);
        }

        @Override
        public ItemStack output() {
            return output.clone();
        }
    }

    public static boolean allowedRecipe(@NotNull SlimefunItem item) {
        return !(item instanceof SlimefunBackpack);
    }
}

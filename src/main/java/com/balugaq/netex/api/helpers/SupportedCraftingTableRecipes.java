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

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@SuppressWarnings("JavaExistingMethodCanBeUsed")
@UtilityClass
public final class SupportedCraftingTableRecipes {

    private static final Map<ItemStack[], ItemStack> RECIPES = new LinkedHashMap<>();
    private static final Set<String> REGISTERED_SLIMEFUN_ITEMS = new HashSet<>();
    private static int lastEnabledItemCount = -1;

    static {
        addEnhancedCraftingTableRecipes();
        refreshRecipes();
    }

    private static void addEnhancedCraftingTableRecipes() {
        String id = SlimefunItems.ENHANCED_CRAFTING_TABLE.getItemId();
        SlimefunItem recipeTypeItem = SlimefunItem.getById(id);
        if (!(recipeTypeItem instanceof MultiBlockMachine mb)) {
            return;
        }

        boolean isInput = true;
        ItemStack[] input = null;
        for (ItemStack[] recipe : mb.getRecipes()) {
            if (isInput) {
                input = recipe;
            } else if (input != null && recipe.length > 0 && recipe[0] != null) {
                if (input.length != 9) {
                    ItemStack[] newInput = new ItemStack[9];
                    for (int i = 0; i < 9; i++) {
                        newInput[i] = i < input.length ? input[i] : null;
                    }
                    input = newInput;
                }
                addRecipe(input, recipe[0]);
            }
            isInput = !isInput;
        }
    }

    /**
     * Adds Enhanced Crafting Table recipes from Slimefun items that became available after Networks loaded.
     *
     * <p>Addon plugins such as InfinityExpansion2 commonly finish registering their items after Networks has
     * initialized. The old one-time static snapshot permanently missed those recipes, causing the Network Recipe
     * Encoder to reject otherwise valid addon recipes (for example IE2 generator recipes using Void Blocks).
     * This refresh is incremental: already-seen Slimefun item ids are skipped, so normal encoder use does not
     * rebuild or duplicate the recipe table.</p>
     */
    public static synchronized void refreshRecipes() {
        var enabledItems = Slimefun.getRegistry().getEnabledSlimefunItems();
        if (enabledItems.size() == lastEnabledItemCount) {
            return;
        }

        for (SlimefunItem item : enabledItems) {
            RecipeType recipeType = item.getRecipeType();
            if (recipeType != RecipeType.ENHANCED_CRAFTING_TABLE || !allowedRecipe(item)) {
                continue;
            }
            if (!REGISTERED_SLIMEFUN_ITEMS.add(item.getId())) {
                continue;
            }

            ItemStack[] itemStacks = new ItemStack[9];
            int i = 0;
            for (ItemStack itemStack : item.getRecipe()) {
                itemStacks[i] = itemStack == null ? null : new ItemStack(itemStack.clone());
                if (++i >= 9) {
                    break;
                }
            }
            addRecipe(itemStacks, item.getRecipeOutput());
        }

        lastEnabledItemCount = enabledItems.size();
    }

    public static @NotNull Map<ItemStack[], ItemStack> getRecipes() {
        refreshRecipes();
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
        refreshRecipes();
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

            if (!recipeIngredientMatches(supplied, required)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Slimefun recipes identify custom ingredients by their registered Slimefun id. Comparing the full
     * Paper 1.21 data-component patch here is too strict for recipe templates and can reject a live copy of
     * the same addon item. Vanilla/non-Slimefun ingredients continue to use Networks' normal strict matcher.
     */
    public static boolean recipeIngredientMatches(@NotNull ItemStack supplied, @NotNull ItemStack required) {
        SlimefunItem requiredItem = SlimefunItem.getByItem(required);
        if (requiredItem != null) {
            SlimefunItem suppliedItem = SlimefunItem.getByItem(supplied);
            return suppliedItem != null && requiredItem.getId().equals(suppliedItem.getId());
        }
        return StackUtils.itemsMatch(supplied, required);
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

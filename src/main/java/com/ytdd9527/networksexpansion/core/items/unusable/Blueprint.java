package com.ytdd9527.networksexpansion.core.items.unusable;

import com.balugaq.netex.api.interfaces.CraftTyped;
import com.balugaq.netex.utils.Lang;
import io.github.sefiraat.networks.network.stackcaches.BlueprintInstance;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.Theme;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentCraftingBlueprintType;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.DistinctiveItem;
import io.github.sefiraat.networks.utils.DisplayNameUtils;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.ArrayList;
import java.util.List;

public class Blueprint extends UnusableSlimefunItem implements DistinctiveItem, CraftTyped {
    public Blueprint(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @SuppressWarnings("deprecation")
    @ParametersAreNonnullByDefault
    public static void setBlueprint(ItemStack blueprint, ItemStack[] recipe, ItemStack output) {
        final ItemMeta itemMeta = blueprint.getItemMeta();
        DataTypeMethods.setCustom(
            itemMeta,
            Keys.BLUEPRINT_INSTANCE,
            PersistentCraftingBlueprintType.TYPE,
            new BlueprintInstance(recipe, output));
        itemMeta.setLore(buildLore(recipe, output));
        blueprint.setItemMeta(itemMeta);
    }

    /**
     * Rebuilds only the visible blueprint lore from the already-persisted recipe payload.
     * The existing PDC is never rewritten, making this safe for explicit Doctor presentation migration.
     */
    public static boolean refreshStoredBlueprintLore(@NotNull ItemStack blueprint) {
        ItemMeta itemMeta = blueprint.getItemMeta();
        BlueprintInstance instance = DataTypeMethods.getCustomSafely(
            itemMeta,
            Keys.BLUEPRINT_INSTANCE,
            PersistentCraftingBlueprintType.TYPE);
        if (instance == null || instance.getRecipeItems() == null || instance.getItemStack() == null) {
            return false;
        }

        List<String> refreshedLore = buildLore(instance.getRecipeItems(), instance.getItemStack());
        List<String> currentLore = itemMeta.hasLore() ? itemMeta.getLore() : null;
        if (refreshedLore.equals(currentLore)) {
            return false;
        }

        itemMeta.setLore(refreshedLore);
        blueprint.setItemMeta(itemMeta);
        return true;
    }

    private static List<String> buildLore(ItemStack[] recipe, ItemStack output) {
        List<String> lore = new ArrayList<>();
        lore.add(Lang.getString("messages.blueprint.title"));

        for (ItemStack item : recipe) {
            if (item == null) {
                lore.add(Theme.PASSIVE + "- " + Lang.getString("messages.blueprint.empty"));
                continue;
            }
            lore.add(Theme.PASSIVE + "- " + DisplayNameUtils.getDisplayName(item));
        }

        lore.add("");
        lore.add(Lang.getString("messages.blueprint.output"));
        lore.add(Theme.PASSIVE + "- " + DisplayNameUtils.getDisplayName(output));
        return lore;
    }

    /*
     * Fix https://github.com/Sefiraat/Networks/issues/201
     */
    @Override
    public boolean canStack(@NotNull ItemMeta meta1, @NotNull ItemMeta meta2) {
        return meta1.getPersistentDataContainer().equals(meta2.getPersistentDataContainer());
    }
}

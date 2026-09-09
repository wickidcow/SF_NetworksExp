package com.ytdd9527.networksexpansion.core.items;

import com.balugaq.netex.api.interfaces.FeedbackSendable;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.NotConfigurable;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Range;

/**
 * We may add something soon
 *
 * @author Final_ROOT
 * @author baluagq
 * @since 2.0
 */
public abstract class SpecialSlimefunItem extends SlimefunItem implements FeedbackSendable {
    public SpecialSlimefunItem(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        @Nullable ItemStack @NotNull [] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    public SpecialSlimefunItem(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        @Nullable ItemStack @NotNull [] recipe,
        @Nullable ItemStack recipeOutput) {
        super(itemGroup, item, recipeType, recipe, recipeOutput);
    }

    public SpecialSlimefunItem(
        @NotNull ItemGroup itemGroup,
        @NotNull ItemStack item,
        @NotNull String id,
        @NotNull RecipeType recipeType,
        @Nullable ItemStack @NotNull [] recipe) {
        super(itemGroup, item, id, recipeType, recipe);
    }

    public SpecialSlimefunItem(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        @Nullable ItemStack @NotNull [] recipe,
        @Range(from = 1, to = 64) int outputAmount
    ) {
        this(itemGroup, item, recipeType, recipe, StackUtils.getAsQuantity(item, outputAmount));
    }

    @Override
    public void register(@NotNull SlimefunAddon addon) {
        super.register(addon);

        this.enchantable = false;
        this.disenchantable = true;
        if (!(this instanceof NotConfigurable)) {
            Slimefun.getItemCfg().setDefaultValue(getId() + ".allow-enchanting", this.enchantable);
            Slimefun.getItemCfg().setDefaultValue(getId() + ".allow-disenchanting", this.disenchantable);
        }
    }

    @NotNull
    public SpecialSlimefunItem registerThis() {
        if (!isEnabledByExpansionConfig()) {
            return this;
        }

        this.register(Networks.getInstance());
        return this;
    }

    private boolean isEnabledByExpansionConfig() {
        final String id = getId();

        // Original Networks items also inherit this class. Expansion configuration
        // must never suppress the core feature set.
        if (!id.startsWith("NTW_EXPANSION_")) {
            return true;
        }
        if (!Networks.getConfigManager().isNetworksExpansionEnabled()) {
            return false;
        }

        if (id.equals("NTW_EXPANSION_DRAWER_MANAGER")) {
            return Networks.getConfigManager().isNetworksExpansionFeatureEnabled("drawer-manager");
        }
        if (id.equals("NTW_EXPANSION_QUANTUM_MANAGER")) {
            return Networks.getConfigManager().isNetworksExpansionFeatureEnabled("quantum-manager");
        }
        if (id.equals("NTW_EXPANSION_ADVANCED_QUANTUM_STORAGE")) {
            return Networks.getConfigManager().isNetworksExpansionFeatureEnabled("advanced-quantum-storage");
        }
        if (id.equals("NTW_EXPANSION_ITEM_FLOW_VIEWER")) {
            return Networks.getConfigManager().isNetworksExpansionFeatureEnabled("item-flow-viewer");
        }
        if (id.contains("DUE_MACHINE")) {
            return Networks.getConfigManager().isNetworksExpansionFeatureEnabled("due-machines");
        }
        if (id.contains("LINE_POWER_OUTLET")) {
            return Networks.getConfigManager().isNetworksExpansionFeatureEnabled("power-outlets");
        }
        if (id.contains("LINE_TRANSFER")) {
            return Networks.getConfigManager().isNetworksExpansionFeatureEnabled("line-transfers");
        }
        if (id.startsWith("NTW_EXPANSION_ADVANCED_TRANSFER")) {
            return Networks.getConfigManager().isNetworksExpansionFeatureEnabled("advanced-transfers");
        }
        if (id.contains("CARGO_STORAGE_UNIT")
            || id.equals("NTW_EXPANSION_DRAWER_TIPS")
            || id.equals("NTW_EXPANSION_STORAGE_UPGRADE_TABLE")) {
            return Networks.getConfigManager().isNetworksExpansionFeatureEnabled("drawers");
        }
        if (isExtraCraftingMachine(id)) {
            return Networks.getConfigManager().isNetworksExpansionFeatureEnabled("extra-crafting-machines");
        }

        return true;
    }

    private static boolean isExtraCraftingMachine(@NotNull String id) {
        return id.contains("_BLUEPRINT")
            || id.contains("_RECIPE_ENCODER")
            || id.contains("_AUTO_")
            || id.equals("NTW_EXPANSION_CRAFTER_MANAGER");
    }
}

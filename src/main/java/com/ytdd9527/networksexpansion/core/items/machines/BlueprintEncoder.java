package com.ytdd9527.networksexpansion.core.items.machines;

import com.balugaq.netex.api.enums.CraftType;
import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.api.helpers.Icon;
import com.balugaq.netex.api.interfaces.CraftTyped;
import com.balugaq.netex.api.interfaces.RecipeCompletableWithGuide;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.balugaq.netex.utils.Lang;
import com.ytdd9527.networksexpansion.utils.itemstacks.ItemStackUtil;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.slimefun.NetworkSlimefunItems;
import io.github.sefiraat.networks.slimefun.network.NetworkObject;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.inventory.DirtyChestMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class BlueprintEncoder extends NetworkObject implements CraftTyped, RecipeCompletableWithGuide {
    private static final int[] BACKGROUND = new int[]{
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 15, 17, 18, 20, 24, 25, 27, 28, 29, 33, 36, 37, 38, 39, 40, 41,
        42, 43, 44
    };
    private static final int[] RECIPE_SLOTS = new int[]{12, 13, 14, 21, 22, 23, 30, 31, 32};
    private static final int[] BLUEPRINT_BACK = new int[]{10, 28};
    private static final int BLANK_BLUEPRINT_SLOT = 19;
    private static final int ENCODE_SLOT = 16;
    private static final int OUTPUT_SLOT = 34;
    private static final int ITEM_TARGET_DESC_SLOT = 26;
    private static final int ITEM_TARGET_SLOT = 35;
    private static final int CHARGE_COST = 2000;

    public BlueprintEncoder(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack @NotNull [] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.ENCODER);
        for (int recipeSlot : RECIPE_SLOTS) {
            this.getSlotsToDrop().add(recipeSlot);
        }
        this.getSlotsToDrop().add(BLANK_BLUEPRINT_SLOT);
        this.getSlotsToDrop().add(OUTPUT_SLOT);
    }

    @Override
    public void postRegister() {
        new BlockMenuPreset(this.getId(), this.getItemName()) {

            @Override
            public void init() {
                drawBackground(BACKGROUND);
                drawBackground(Icon.BLUEPRINT_BACK_STACK, BLUEPRINT_BACK);

                addItem(ENCODE_SLOT, Icon.ENCODE_STACK, (player, i, itemStack, clickAction) -> false);
                addItem(ITEM_TARGET_DESC_SLOT, Icon.ITEM_TARGET_DESC_STACK, (player, i, itemStack, clickAction) -> false);
            }

            @Override
            public void newInstance(@NotNull BlockMenu menu, @NotNull Block b) {
                menu.addMenuClickHandler(ENCODE_SLOT, (player, s, itemStack, clickAction) -> {
                    int times = clickAction.isShiftClicked() ? 64 : 1;
                    for (int i = 0; i < times; i++) {
                        if (!tryEncode(player, menu)) {
                            break;
                        }
                    }
                    return false;
                });
                // addJEGButton(menu, JEG_SLOT);
                var fix = menu.getItemInSlot(ITEM_TARGET_SLOT);
                if (StackUtils.itemsMatch(fix, ChestMenuUtils.getBackground())) {
                    menu.replaceExistingItem(ITEM_TARGET_SLOT, null);
                }
            }

            @Override
            public boolean canOpen(@NotNull Block block, @NotNull Player player) {
                return player.hasPermission("slimefun.inventory.bypass")
                    || (this.getSlimefunItem().canUse(player, false)
                    && Slimefun.getProtectionManager()
                    .hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK));
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                if (flow == ItemTransportFlow.WITHDRAW) {
                    return new int[]{OUTPUT_SLOT};
                }

                return new int[0];
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(DirtyChestMenu menu, ItemTransportFlow flow, ItemStack itemStack) {
                if (flow == ItemTransportFlow.WITHDRAW) return new int[]{OUTPUT_SLOT};

                List<Integer> slots = new ArrayList<>();
                if (StackUtils.itemsMatch(itemStack, menu.getItemInSlot(BLANK_BLUEPRINT_SLOT))) {
                    slots.add(BLANK_BLUEPRINT_SLOT);
                }

                for (int slot : RECIPE_SLOTS) {
                    if (StackUtils.itemsMatch(itemStack, menu.getItemInSlot(slot))) {
                        slots.add(slot);
                    }
                }
                return slots.stream().mapToInt(Integer::intValue).toArray();
            }
        };
    }

    public boolean tryEncode(@NotNull Player player, @NotNull BlockMenu blockMenu) {
        final NodeDefinition definition = NetworkStorage.getNode(blockMenu.getLocation());

        if (definition == null || definition.getNode() == null) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_NETWORK_FOUND);
            player.sendMessage(Lang.getString("messages.feedback.no_network_found"));
            return false;
        }

        final NetworkRoot root = definition.getNode().getRoot();
        if (root.getRootPower() < CHARGE_COST) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.encoder.not_enough_power"));
            sendFeedback(blockMenu.getLocation(), FeedbackType.NOT_ENOUGH_POWER);
            return false;
        }

        ItemStack blueprint = blockMenu.getItemInSlot(BLANK_BLUEPRINT_SLOT);
        if (blueprint == null || blueprint.getType() == Material.AIR || blueprint.getAmount() <= 0) {
            final ItemStack template = NetworkSlimefunItems.CRAFTING_BLUEPRINT.getItem();
            NetworkTransferUtils.moveNetworkItemIntoMenu(
                root,
                blockMenu.getLocation(),
                blockMenu,
                template,
                template.getMaxStackSize(),
                BLANK_BLUEPRINT_SLOT);
            blueprint = blockMenu.getItemInSlot(BLANK_BLUEPRINT_SLOT);
            if (blueprint == null || blueprint.getType() == Material.AIR || blueprint.getAmount() <= 0) {
                player.sendMessage(Lang.getString("messages.feedback.no_blueprint_found"));
                sendFeedback(blockMenu.getLocation(), FeedbackType.NO_BLUEPRINT_FOUND);
                return false;
            }
        }

        final SlimefunItem blueprintItem = SlimefunItem.getByItem(blueprint);
        if (blueprintItem != null && blueprintItem.isDisabled()) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.encoder.disabled_blueprint"));
            sendFeedback(blockMenu.getLocation(), FeedbackType.DISABLED_BLUEPRINT);
            return false;
        }

        if (!isValidBlueprint(blueprintItem)) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.encoder.invalid_blueprint"));
            sendFeedback(blockMenu.getLocation(), FeedbackType.INVALID_BLUEPRINT);
            return false;
        }

        final ItemStack[] inputs = new ItemStack[RECIPE_SLOTS.length];
        for (int index = 0; index < RECIPE_SLOTS.length; index++) {
            final ItemStack stackInSlot = blockMenu.getItemInSlot(RECIPE_SLOTS[index]);
            if (stackInSlot != null && stackInSlot.getType() != Material.AIR && stackInSlot.getAmount() > 0) {
                inputs[index] = ItemStackUtil.getCleanItem(stackInSlot.clone());
            }
        }

        ItemStack crafted = null;
        ItemStack[] consumptionRecipe = null;
        ItemStack target = blockMenu.getItemInSlot(ITEM_TARGET_SLOT);
        if (target == null || target.getType() == Material.AIR) {
            target = null;
        }

        for (var recipes : CraftType.map().entrySet()) {
            boolean found = false;
            for (Map.Entry<ItemStack[], ItemStack> recipe : recipes.getValue()) {
                if (!testRecipe(recipes.getKey(), inputs, recipe.getKey())) {
                    continue;
                }
                final ItemStack candidate = recipe.getValue();
                if (target != null && !StackUtils.itemsMatch(candidate, target)) {
                    continue;
                }

                crafted = candidate.clone();
                consumptionRecipe = cleanRecipe(recipe.getKey());
                found = true;
                break;
            }
            if (found) {
                break;
            }
        }

        if (crafted == null && canTestVanillaRecipe(inputs)) {
            crafted = Bukkit.craftItem(copyRecipe(inputs), player.getWorld(), player);
            consumptionRecipe = new ItemStack[RECIPE_SLOTS.length];
            for (int index = 0; index < RECIPE_SLOTS.length; index++) {
                if (inputs[index] != null && inputs[index].getType() != Material.AIR) {
                    consumptionRecipe[index] = StackUtils.getAsQuantity(inputs[index], 1);
                }
            }
        }

        if (crafted == null
            || crafted.getType() == Material.AIR
            || crafted.getAmount() <= 0
            || consumptionRecipe == null
            || !hasExactIngredients(blockMenu, consumptionRecipe)) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.encoder.invalid_recipe"));
            sendFeedback(blockMenu.getLocation(), FeedbackType.INVALID_RECIPE);
            return false;
        }

        final SlimefunItem outputItem = SlimefunItem.getByItem(crafted);
        if (outputItem != null && outputItem.isDisabled()) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.encoder.disabled_output"));
            sendFeedback(blockMenu.getLocation(), FeedbackType.DISABLED_OUTPUT);
            return false;
        }

        final ItemStack encodedBlueprint = StackUtils.getAsQuantity(blueprint, 1);
        blueprintSetter(encodedBlueprint, copyRecipe(consumptionRecipe), crafted.clone());
        if (!BlockMenuUtil.fits(blockMenu, encodedBlueprint, OUTPUT_SLOT)) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.encoder.output_full"));
            sendFeedback(blockMenu.getLocation(), FeedbackType.OUTPUT_FULL);
            return false;
        }

        // Commit only after all validation and capacity checks have succeeded.
        BlockMenuUtil.consumeItem(blockMenu, BLANK_BLUEPRINT_SLOT, 1, false);
        for (int index = 0; index < RECIPE_SLOTS.length; index++) {
            final ItemStack required = index < consumptionRecipe.length ? consumptionRecipe[index] : null;
            if (required == null || required.getType() == Material.AIR || required.getAmount() <= 0) {
                continue;
            }
            BlockMenuUtil.consumeItem(blockMenu, RECIPE_SLOTS[index], Math.max(1, required.getAmount()), true);
        }

        final ItemStack outputRemainder = BlockMenuUtil.pushItem(blockMenu, encodedBlueprint, OUTPUT_SLOT);
        if (outputRemainder != null && outputRemainder.getType() != Material.AIR && outputRemainder.getAmount() > 0) {
            NetworkTransferUtils.rollbackNetworkWithdrawal(
                root,
                blockMenu.getLocation(),
                outputRemainder,
                blockMenu.getLocation(),
                "blueprint output commit");
        }

        // Keep the encoder stocked without reserving an entire stack before the craft commits.
        final ItemStack remainingBlueprints = blockMenu.getItemInSlot(BLANK_BLUEPRINT_SLOT);
        if (remainingBlueprints == null
            || remainingBlueprints.getType() == Material.AIR
            || remainingBlueprints.getAmount() <= 0) {
            final ItemStack template = NetworkSlimefunItems.CRAFTING_BLUEPRINT.getItem();
            NetworkTransferUtils.moveNetworkItemIntoMenu(
                root,
                blockMenu.getLocation(),
                blockMenu,
                template,
                template.getMaxStackSize(),
                BLANK_BLUEPRINT_SLOT);
        }

        blockMenu.markDirty();
        root.removeRootPower(CHARGE_COST);
        sendFeedback(blockMenu.getLocation(), FeedbackType.SUCCESS);
        return true;
    }

    private static ItemStack[] cleanRecipe(ItemStack[] recipe) {
        final ItemStack[] copy = new ItemStack[recipe.length];
        for (int index = 0; index < recipe.length; index++) {
            if (recipe[index] != null && recipe[index].getType() != Material.AIR) {
                copy[index] = ItemStackUtil.getCleanItem(recipe[index].clone());
            }
        }
        return copy;
    }

    private static ItemStack[] copyRecipe(ItemStack[] recipe) {
        final ItemStack[] copy = new ItemStack[recipe.length];
        for (int index = 0; index < recipe.length; index++) {
            copy[index] = recipe[index] == null ? null : recipe[index].clone();
        }
        return copy;
    }

    private static boolean hasExactIngredients(BlockMenu menu, ItemStack[] recipe) {
        for (int index = 0; index < RECIPE_SLOTS.length; index++) {
            final ItemStack required = index < recipe.length ? recipe[index] : null;
            final ItemStack supplied = menu.getItemInSlot(RECIPE_SLOTS[index]);
            final boolean requiredEmpty = required == null
                || required.getType() == Material.AIR
                || required.getAmount() <= 0;
            final boolean suppliedEmpty = supplied == null
                || supplied.getType() == Material.AIR
                || supplied.getAmount() <= 0;

            if (requiredEmpty || suppliedEmpty) {
                if (requiredEmpty != suppliedEmpty) {
                    return false;
                }
                continue;
            }

            if (!StackUtils.itemsMatch(supplied, required)
                || supplied.getAmount() < Math.max(1, required.getAmount())) {
                return false;
            }
        }
        return true;
    }

    public void blueprintSetter(ItemStack itemStack, ItemStack @NotNull [] inputs, ItemStack crafted) {
        craftType().blueprintSetter(itemStack, inputs, crafted);
    }

    public boolean isValidBlueprint(SlimefunItem item) {
        return craftType().isValidBlueprint(item);
    }

    public Set<Map.Entry<ItemStack[], ItemStack>> getRecipeEntries() {
        return craftType().getRecipeEntries();
    }

    public boolean testRecipe(CraftType craftType, ItemStack[] inputs, ItemStack[] recipe) {
        return craftType.testRecipe(inputs, recipe);
    }
    public boolean canTestVanillaRecipe(ItemStack[] inputs) {
        return false;
    }

    @Override
    @NotNull
    public SlimefunItem getSlimefunItem() {
        return this;
    }
}

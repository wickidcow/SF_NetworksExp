package com.ytdd9527.networksexpansion.core.items.machines;

import com.balugaq.netex.api.data.SuperRecipe;
import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.balugaq.netex.utils.Lang;
import com.ytdd9527.networksexpansion.core.items.SpecialSlimefunItem;
import com.ytdd9527.networksexpansion.utils.itemstacks.ItemStackUtil;
import io.github.sefiraat.networks.events.NetworkCraftEvent;
import io.github.sefiraat.networks.slimefun.network.AdminDebuggable;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.EnergyNetComponent;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockBreakHandler;
import io.github.thebusybiscuit.slimefun4.core.handlers.BlockPlaceHandler;
import io.github.thebusybiscuit.slimefun4.core.networks.energy.EnergyNetComponentType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("DuplicatedCode")
public abstract class AbstractManualCrafter extends SpecialSlimefunItem implements AdminDebuggable, EnergyNetComponent {
    public AbstractManualCrafter(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        @NotNull ItemStack @NotNull [] recipe) {
        super(itemGroup, item, recipeType, recipe);
    }

    @Override
    public void preRegister() {
        BlockPlaceHandler blockPlaceHandler = getMachineBlockPlaceHandler();
        if (blockPlaceHandler != null) {
            addItemHandler(blockPlaceHandler);
        }

        BlockBreakHandler blockBreakHandler = getMachineBlockBreakHandler();
        if (blockBreakHandler != null) {
            addItemHandler(blockBreakHandler);
        }

        BlockTicker blockTicker = getMachineBlockTicker();
        if (blockTicker != null) {
            addItemHandler(blockTicker);
        }
    }

    @Override
    public void postRegister() {
        new BlockMenuPreset(this.getId(), this.getItemName()) {
            @Override
            public void init() {
                for (Map.Entry<Integer, ItemStack> entry : getBackgrounds().entrySet()) {
                    addItem(entry.getKey(), entry.getValue(), ChestMenuUtils.getEmptyClickHandler());
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
                if (isTransportable()) {
                    if (flow == ItemTransportFlow.WITHDRAW) {
                        return getOutputSlots();
                    }
                    return getInputSlots();
                }
                return new int[0];
            }

            @SuppressWarnings("deprecation")
            @Override
            public void newInstance(@NotNull BlockMenu menu, @NotNull Block b) {
                menu.addMenuClickHandler(getHandleSlot(), (p, slot, item, action) -> {
                    craft(p, menu);
                    return false;
                });
                Map<Integer, ChestMenu.MenuClickHandler> menuClickHandlers = getMenuClickHandlers();
                if (menuClickHandlers != null) {
                    for (Map.Entry<Integer, ChestMenu.MenuClickHandler> entry : menuClickHandlers.entrySet()) {
                        menu.addMenuClickHandler(entry.getKey(), entry.getValue());
                    }
                }
            }
        };
    }

    public void craft(@NotNull Player player, @NotNull BlockMenu blockMenu) {
        boolean success = false;
        for (SuperRecipe recipe : getRecipes()) {
            if (getCapacityLong() < recipe.getConsumeEnergy()) {
                continue;
            }

            if (getChargeLong(blockMenu.getLocation()) >= recipe.getConsumeEnergy()) {
                if (recipe.getHandler() != null) {
                    success = recipe.getHandler().handle(player, blockMenu);
                } else {
                    if (recipe.isShaped()) {
                        success = shapedCraft(recipe, player, blockMenu);
                    } else {
                        success = shapelessCraft(recipe, player, blockMenu);
                    }
                }
            }
            if (success) {
                break;
            }
        }
    }

    public boolean shapedCraft(@NotNull SuperRecipe recipe, @NotNull Player player, @NotNull BlockMenu blockMenu) {
        World world = blockMenu.getLocation().getWorld();
        if (world == null) {
            return false;
        }

        for (int i = 0; i < Math.min(recipe.getInput().length, getInputSlots().length); i++) {
            ItemStack wanted = recipe.getInput()[i];
            if (wanted == null || wanted.getType() == Material.AIR) {
                continue;
            }

            ItemStack itemInSlot = blockMenu.getItemInSlot(getInputSlots()[i]);
            if (itemInSlot == null || itemInSlot.getType() == Material.AIR) {
                return false;
            }

            if (!StackUtils.itemsMatch(itemInSlot, wanted, false, true)) {
                return false;
            }
        }

        if (!BlockMenuUtil.fits(blockMenu, recipe.getOutput(), getOutputSlots())) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_ENOUGH_SPACE);
            return false;
        }

        /*
         * Fire every cancellable craft event before committing the input mutation. Previously the
         * recipe inputs were consumed first, so a listener cancelling this event could delete the
         * ingredients. Multi-output recipes could also push an earlier output before a later event
         * cancelled the same craft. Retain the existing output order and disabled-output behavior,
         * but make cancellation an all-or-nothing pre-commit decision.
         */
        final ItemStack[] preparedOutputs = recipe.getOutput().clone();
        for (int i = 0; i < preparedOutputs.length; i++) {
            ItemStack item = preparedOutputs[i];
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            SlimefunItem sfi = SlimefunItem.getByItem(item);
            if (sfi != null && (sfi.isDisabled() || sfi.isDisabledIn(world))) {
                player.sendMessage(
                    Lang.getString("messages.unsupported-operation.manual_crafter.disabled-output"));
                sendFeedback(blockMenu.getLocation(), FeedbackType.DISABLED_OUTPUT);
                preparedOutputs[i] = null;
                continue;
            }

            NetworkCraftEvent event = new NetworkCraftEvent(player, this, recipe.getInput(), item);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return false;
            }
            preparedOutputs[i] = event.getOutput();
        }

        for (int i = 0; i < Math.min(recipe.getInput().length, getInputSlots().length); i++) {
            ItemStack wanted = recipe.getInput()[i];
            if (wanted == null || wanted.getType() == Material.AIR) {
                continue;
            }

            BlockMenuUtil.consumeItem(blockMenu, getInputSlots()[i], wanted.getAmount());
        }

        for (ItemStack item : preparedOutputs) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            ItemStack left = BlockMenuUtil.pushItem(blockMenu, ItemStackUtil.getCleanItem(item), getOutputSlots());
            if (left != null && left.getType() != Material.AIR) {
                player.sendMessage(Lang.getString("messages.unsupported-operation.manual_crafter.full-output"));
                sendFeedback(blockMenu.getLocation(), FeedbackType.NO_ENOUGH_SPACE);
                world.dropItem(blockMenu.getLocation(), left);
            }
        }

        if (recipe.getConsumeEnergy() > 0) {
            removeCharge(blockMenu.getLocation(), (long) recipe.getConsumeEnergy());
        }
        player.sendMessage(Lang.getString("messages.completed-operation.manual_crafter.success"));
        sendFeedback(blockMenu.getLocation(), FeedbackType.SUCCESS);
        return true;
    }

    public boolean shapelessCraft(@NotNull SuperRecipe recipe, @NotNull Player player, @NotNull BlockMenu blockMenu) {
        World world = blockMenu.getLocation().getWorld();
        if (world == null) {
            return false;
        }

        // Preserve the historical ItemStack-key grouping used by authored recipes.
        Map<ItemStack, Integer> wanted = new HashMap<>();
        for (int i = 0; i < Math.min(recipe.getInput().length, getInputSlots().length); i++) {
            ItemStack wantedItem = recipe.getInput()[i];
            if (wantedItem == null || wantedItem.getType() == Material.AIR) {
                continue;
            }

            if (wanted.containsKey(wantedItem)) {
                wanted.put(wantedItem, wanted.get(wantedItem) + wantedItem.getAmount());
            } else {
                wanted.put(wantedItem, wantedItem.getAmount());
            }
        }

        Map<ItemStack, Integer> have = new HashMap<>();
        for (int slot : getInputSlots()) {
            ItemStack itemInSlot = blockMenu.getItemInSlot(slot);
            if (itemInSlot == null || itemInSlot.getType() == Material.AIR) {
                continue;
            }

            if (have.containsKey(itemInSlot)) {
                have.put(itemInSlot, have.get(itemInSlot) + itemInSlot.getAmount());
            } else {
                have.put(itemInSlot, itemInSlot.getAmount());
            }
        }

        for (Map.Entry<ItemStack, Integer> entry : wanted.entrySet()) {
            if (!have.containsKey(entry.getKey()) || have.get(entry.getKey()) < entry.getValue()) {
                // Missing ingredients are a failed preflight. The previous break continued into output
                // handling and could complete a craft without the complete shapeless recipe.
                return false;
            }
        }

        if (!BlockMenuUtil.fits(blockMenu, recipe.getOutput(), getOutputSlots())) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_ENOUGH_SPACE);
            return false;
        }

        /*
         * Do not structurally modify the requirement map while iterating it. Track each ingredient's
         * remaining quantity locally; successful preflight guarantees the matching amount exists.
         */
        for (Map.Entry<ItemStack, Integer> entry : wanted.entrySet()) {
            int remaining = entry.getValue();
            for (int slot : getInputSlots()) {
                ItemStack itemInSlot = blockMenu.getItemInSlot(slot);
                if (itemInSlot == null || itemInSlot.getType() == Material.AIR) {
                    continue;
                }

                if (StackUtils.itemsMatch(itemInSlot, entry.getKey())) {
                    int amount = Math.min(remaining, itemInSlot.getAmount());
                    if (amount > 0) {
                        BlockMenuUtil.consumeItem(blockMenu, slot, amount);
                        remaining -= amount;
                    }
                }

                if (remaining <= 0) {
                    break;
                }
            }

            if (remaining > 0) {
                throw new IllegalStateException(
                    "Manual crafter shapeless preflight and consumption disagreed for " + entry.getKey().getType());
            }
        }

        for (ItemStack item : recipe.getOutput()) {
            if (item != null && item.getType() != Material.AIR) {
                SlimefunItem sfi = SlimefunItem.getByItem(item);
                if (sfi != null) {
                    if (sfi.isDisabled() || sfi.isDisabledIn(world)) {
                        player.sendMessage(
                            Lang.getString("messages.unsupported-operation.manual_crafter.disabled-output"));
                        sendFeedback(blockMenu.getLocation(), FeedbackType.DISABLED_OUTPUT);
                        continue;
                    }
                }
                ItemStack left = BlockMenuUtil.pushItem(blockMenu, item, getOutputSlots());
                if (left != null && left.getType() != Material.AIR) {
                    player.sendMessage(Lang.getString("messages.unsupported-operation.manual_crafter.full-output"));
                    sendFeedback(blockMenu.getLocation(), FeedbackType.NO_ENOUGH_SPACE);
                    world.dropItem(blockMenu.getLocation(), left);
                }
            }
        }

        if (recipe.getConsumeEnergy() > 0) {
            removeCharge(blockMenu.getLocation(), (long) recipe.getConsumeEnergy());
        }
        player.sendMessage(Lang.getString("messages.completed-operation.manual_crafter.success"));
        sendFeedback(blockMenu.getLocation(), FeedbackType.SUCCESS);
        return true;
    }

    @Override
    @NotNull
    public EnergyNetComponentType getEnergyComponentType() {
        return EnergyNetComponentType.CONSUMER;
    }

    public abstract List<SuperRecipe> getRecipes();

    public abstract int[] getInputSlots();

    public abstract int[] getOutputSlots();

    public abstract int getHandleSlot();

    public abstract boolean isTransportable();

    public abstract Map<Integer, ItemStack> getBackgrounds();

    @SuppressWarnings("deprecation")
    public abstract @Nullable Map<Integer, ChestMenu.MenuClickHandler> getMenuClickHandlers();

    public abstract BlockPlaceHandler getMachineBlockPlaceHandler();

    public abstract BlockBreakHandler getMachineBlockBreakHandler();

    public abstract @Nullable BlockTicker getMachineBlockTicker();
}

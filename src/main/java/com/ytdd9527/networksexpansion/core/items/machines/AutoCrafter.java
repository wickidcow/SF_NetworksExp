package com.ytdd9527.networksexpansion.core.items.machines;

import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.api.helpers.Icon;
import com.balugaq.netex.api.interfaces.CraftTyped;
import com.balugaq.netex.api.interfaces.SoftCellBannable;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.ytdd9527.networksexpansion.core.items.unusable.Blueprint;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.network.stackcaches.BlueprintInstance;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.slimefun.network.NetworkObject;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("DuplicatedCode")
public class AutoCrafter extends NetworkObject implements SoftCellBannable, CraftTyped {
    public static final int BLUEPRINT_SLOT = 10;
    public static final int OUTPUT_SLOT = 16;
    public static final Map<Location, BlueprintInstance> INSTANCE_MAP = new ConcurrentHashMap<>();
    private static final int[] BACKGROUND_SLOTS = new int[]{3, 4, 5, 12, 13, 14, 21, 22, 23};
    private static final int[] BLUEPRINT_BACKGROUND = new int[]{0, 1, 2, 9, 11, 18, 19, 20};
    private static final int[] OUTPUT_BACKGROUND = new int[]{6, 7, 8, 15, 17, 24, 25, 26};
    protected final int chargePerCraft;
    protected final boolean withholding;

    public AutoCrafter(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack @NotNull [] recipe,
        int chargePerCraft,
        boolean withholding) {
        super(itemGroup, item, recipeType, recipe, NodeType.CRAFTER);

        this.chargePerCraft = chargePerCraft;
        this.withholding = withholding;

        this.getSlotsToDrop().add(BLUEPRINT_SLOT);
        this.getSlotsToDrop().add(OUTPUT_SLOT);

        addItemHandler(new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return io.github.sefiraat.networks.Networks.getConfigManager().useSynchronizedMachineTickers();
            }

            @Override
            public void tick(@NotNull Block block, SlimefunItem slimefunItem, @NotNull SlimefunBlockData data) {
                BlockMenu blockMenu = data.getBlockMenu();
                if (blockMenu != null) {
                    addToRegistry(block);
                    craftPreFlight(blockMenu);
                }
            }
        });
    }

    public static void updateCache(@NotNull BlockMenu blockMenu) {
        AutoCrafter.INSTANCE_MAP.remove(blockMenu.getLocation());
    }

    protected void craftPreFlight(@NotNull BlockMenu blockMenu) {

        releaseCache(blockMenu);

        final NodeDefinition definition = NetworkStorage.getNode(blockMenu.getLocation());

        if (definition == null || definition.getNode() == null) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_NETWORK_FOUND);
            return;
        }

        final NetworkRoot root = definition.getNode().getRoot();

        if (checkSoftCellBan(blockMenu.getLocation(), root)) {
            return;
        }

        if (!withholding) {
            final ItemStack stored = blockMenu.getItemInSlot(OUTPUT_SLOT);
            if (stored != null && stored.getType() != Material.AIR) {
                NetworkTransferUtils.moveMenuSlotIntoNetwork(
                    root, blockMenu.getLocation(), blockMenu, OUTPUT_SLOT);
            }
        }

        final ItemStack blueprint = blockMenu.getItemInSlot(BLUEPRINT_SLOT);

        if (blueprint == null || blueprint.getType() == Material.AIR) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_BLUEPRINT_FOUND);
            return;
        }

        final long networkCharge = root.getRootPower();

        if (networkCharge < this.chargePerCraft) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NOT_ENOUGH_POWER);
            return;
        }

        final SlimefunItem item = SlimefunItem.getByItem(blueprint);

        if (!isValidBlueprint(item)) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.INVALID_BLUEPRINT);
            return;
        }

        BlueprintInstance instance = AutoCrafter.INSTANCE_MAP.get(blockMenu.getLocation());

        if (instance == null) {
            final ItemMeta blueprintMeta = blueprint.getItemMeta();
            BlueprintInstance instance2 = Keys.getBlueprintInstance(blueprintMeta);
            if (instance2 == null) {
                sendFeedback(blockMenu.getLocation(), FeedbackType.NO_BLUEPRINT_INSTANCE_FOUND);
                return;
            }

            if (instance2 == BlueprintInstance.INVALID) {
                sendFeedback(blockMenu.getLocation(), FeedbackType.BROKEN_BLUEPRINT);
                return;
            }

            setCache(blockMenu, instance2);
            instance = instance2;
        }

        final ItemStack output = blockMenu.getItemInSlot(OUTPUT_SLOT);
        int blueprintAmount = canBlueprintStack() ? blueprint.getAmount() : 1;

        ItemStack targetOutput = instance.getItemStack();
        if (targetOutput == null || targetOutput.getType() == Material.AIR || targetOutput.getAmount() <= 0) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.BROKEN_BLUEPRINT);
            return;
        }
        if (output != null
            && output.getType() != Material.AIR
            && targetOutput != null
            && (output.getAmount() + targetOutput.getAmount() * blueprintAmount > output.getMaxStackSize()
            || !StackUtils.itemsMatch(targetOutput, output))) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.OUTPUT_FULL);
            return;
        }

        if (tryCraft(blockMenu, instance, root, blueprintAmount, output)) {
            root.removeRootPower(this.chargePerCraft);
        }
    }

    @SuppressWarnings("DataFlowIssue")
    private boolean tryCraft(
        @NotNull BlockMenu blockMenu,
        @NotNull BlueprintInstance instance,
        @NotNull NetworkRoot root,
        int blueprintAmount,
        @Nullable ItemStack existing) {
        /* Make sure the network has the required items
         * Needs to be revisited as matching is happening stacks 2x when it should
         * only need the one
         */
        HashMap<ItemStack, Integer> requiredItems = new HashMap<>();
        for (int i = 0; i < instance.getRecipeItems().length; i++) {
            final ItemStack requested = instance.getRecipeItems()[i];
            if (requested != null) {
                requiredItems.merge(requested, requested.getAmount() * blueprintAmount, Integer::sum);
            }
        }

        for (Map.Entry<ItemStack, Integer> entry : requiredItems.entrySet()) {
            if (!root.contains(new ItemRequest(entry.getKey(), entry.getValue()))) {
                sendFeedback(blockMenu.getLocation(), FeedbackType.NOT_ENOUGH_ITEMS_IN_NETWORK);
                return false;
            }
        }

        ItemStack[] fetcheds = new ItemStack[instance.getRecipeItems().length];

        // Then fetch the actual items
        for (int i = 0; i < instance.getRecipeItems().length; i++) {
            final ItemStack requested = instance.getRecipeItems()[i];
            if (requested != null) {
                final ItemStack fetched = root.getItemStack0(
                    blockMenu.getLocation(), new ItemRequest(requested, requested.getAmount() * blueprintAmount));
                fetcheds[i] = fetched;
                if (fetched == null || fetched.getAmount() < requested.getAmount() * blueprintAmount) {
                    returnItems(root, fetcheds, blockMenu);
                    return false;
                }
            }
        }

        // Push item
        final Location location = blockMenu.getLocation().clone().add(0.5, 1.1, 0.5);
        if (root.isDisplayParticles() && location.getWorld() != null) {
            location.getWorld().spawnParticle(Particle.WAX_OFF, location, 0, 0, 4, 0);
        }

        ItemStack crafted = instance.getItemStack().clone();

        crafted.setAmount(crafted.getAmount() * blueprintAmount);

        if (crafted.getAmount() > crafted.getMaxStackSize()) {
            returnItems(root, fetcheds, blockMenu);
            sendFeedback(blockMenu.getLocation(), FeedbackType.RESULT_IS_TOO_LARGE);
            return false;
        }

        if (!withholding && existing != null && existing.getType() != Material.AIR) {
            root.uncontrolAccessInput(blockMenu.getLocation());
            root.addItemStack0(blockMenu.getLocation(), crafted);
        }
        if (crafted.getType() != Material.AIR && crafted.getAmount() > 0) {
            final ItemStack remainder = BlockMenuUtil.pushItem(blockMenu, crafted, OUTPUT_SLOT);
            if (remainder == null || remainder.getAmount() <= 0) {
                crafted.setAmount(0);
            } else {
                crafted = remainder;
            }
            blockMenu.markDirty();
        }
        if (crafted.getAmount() > 0) {
            NetworkTransferUtils.rollbackNetworkWithdrawal(
                root, blockMenu.getLocation(), crafted, blockMenu.getLocation(), "auto-crafter output");
        }
        sendFeedback(blockMenu.getLocation(), FeedbackType.WORKING);
        return true;
    }

    protected void returnItems(
        @NotNull NetworkRoot root, @Nullable ItemStack @NotNull [] inputs, @NotNull BlockMenu blockMenu) {
        for (ItemStack input : inputs) {
            if (input == null || input.getType() == Material.AIR || input.getAmount() <= 0) {
                continue;
            }

            NetworkTransferUtils.rollbackNetworkWithdrawal(
                root, blockMenu.getLocation(), input, blockMenu.getLocation(), "auto-crafter ingredient restore");
        }
    }

    public void releaseCache(@NotNull BlockMenu blockMenu) {
        INSTANCE_MAP.remove(blockMenu.getLocation());
    }

    public void setCache(@NotNull BlockMenu blockMenu, @NotNull BlueprintInstance blueprintInstance) {
        if (!blockMenu.hasViewer()) {
            INSTANCE_MAP.putIfAbsent(blockMenu.getLocation().clone(), blueprintInstance);
        }
    }

    @Override
    public void postRegister() {
        new BlockMenuPreset(this.getId(), this.getItemName()) {

            @Override
            public void init() {
                drawBackground(BACKGROUND_SLOTS);
                drawBackground(Icon.BLUEPRINT_BACKGROUND_STACK, BLUEPRINT_BACKGROUND);
                drawBackground(Icon.OUTPUT_BACKGROUND_STACK, OUTPUT_BACKGROUND);
            }

            @Override
            public void newInstance(@NotNull BlockMenu menu, @NotNull Block b) {
                menu.addMenuOpeningHandler(p -> releaseCache(menu));
                menu.addMenuCloseHandler(p -> releaseCache(menu));
                menu.addMenuClickHandler(BLUEPRINT_SLOT, (player, slot, clickedItem, clickAction) -> {
                    releaseCache(menu);
                    return true;
                });
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
                if (AutoCrafter.this.withholding && flow == ItemTransportFlow.WITHDRAW) {
                    return new int[]{OUTPUT_SLOT};
                }
                return new int[0];
            }
        };
    }

    public boolean isValidBlueprint(SlimefunItem item) {
        return item instanceof Blueprint;
    }

    public boolean canBlueprintStack() {
        return false;
    }
}

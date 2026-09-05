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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings("DuplicatedCode")
public class AutoCrafter extends NetworkObject implements SoftCellBannable, CraftTyped {
    public static final int BLUEPRINT_SLOT = 10;
    public static final int OUTPUT_SLOT = 16;
    public static final Map<Location, BlueprintInstance> INSTANCE_MAP = new ConcurrentHashMap<>();
    private static final Map<Location, List<IngredientRequest>> INGREDIENT_PLAN_MAP = new ConcurrentHashMap<>();
    private static final Map<Location, Integer> IDLE_MISS_MAP = new ConcurrentHashMap<>();
    private static final Map<Location, Integer> IDLE_SKIP_MAP = new ConcurrentHashMap<>();
    private static final int[] BACKGROUND_SLOTS = new int[]{3, 4, 5, 12, 13, 14, 21, 22, 23};
    private static final int[] BLUEPRINT_BACKGROUND = new int[]{0, 1, 2, 9, 11, 18, 19, 20};
    private static final int[] OUTPUT_BACKGROUND = new int[]{6, 7, 8, 15, 17, 24, 25, 26};
    private static final int IDLE_BACKOFF_THRESHOLD = 3;
    private static final int IDLE_BACKOFF_MAX_TICKS = 4;
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
                    final Location location = blockMenu.getLocation();
                    if (shouldSkipIdleTick(location)) {
                        return;
                    }
                    recordCraftResult(location, craftPreFlight(blockMenu));
                }
            }
        });
    }

    public static void updateCache(@NotNull BlockMenu blockMenu) {
        clearRuntimeCache(blockMenu.getLocation());
    }

    private static boolean shouldSkipIdleTick(@NotNull Location location) {
        final Integer remaining = IDLE_SKIP_MAP.get(location);
        if (remaining == null || remaining <= 0) {
            return false;
        }
        if (remaining <= 1) {
            IDLE_SKIP_MAP.remove(location);
        } else {
            IDLE_SKIP_MAP.put(location.clone(), remaining - 1);
        }
        return true;
    }

    private static void recordCraftResult(@NotNull Location location, boolean crafted) {
        if (crafted) {
            IDLE_MISS_MAP.remove(location);
            IDLE_SKIP_MAP.remove(location);
            return;
        }

        final int misses = IDLE_MISS_MAP.merge(location.clone(), 1, Integer::sum);
        if (misses < IDLE_BACKOFF_THRESHOLD) {
            return;
        }

        final int exponent = Math.min(2, misses - IDLE_BACKOFF_THRESHOLD);
        final int skipTicks = Math.min(IDLE_BACKOFF_MAX_TICKS, 1 << exponent);
        IDLE_SKIP_MAP.put(location.clone(), skipTicks);
    }

    private static void clearRuntimeCache(@NotNull Location location) {
        INSTANCE_MAP.remove(location);
        INGREDIENT_PLAN_MAP.remove(location);
        IDLE_MISS_MAP.remove(location);
        IDLE_SKIP_MAP.remove(location);
    }

    protected boolean craftPreFlight(@NotNull BlockMenu blockMenu) {
        final Location location = blockMenu.getLocation();
        final NodeDefinition definition = NetworkStorage.getNode(location);

        if (definition == null || definition.getNode() == null) {
            sendFeedback(location, FeedbackType.NO_NETWORK_FOUND);
            return false;
        }

        final NetworkRoot root = definition.getNode().getRoot();

        if (checkSoftCellBan(location, root)) {
            return false;
        }

        if (!withholding) {
            final ItemStack stored = blockMenu.getItemInSlot(OUTPUT_SLOT);
            if (stored != null && stored.getType() != Material.AIR) {
                NetworkTransferUtils.moveMenuSlotIntoNetwork(root, location, blockMenu, OUTPUT_SLOT);
            }
        }

        final ItemStack blueprint = blockMenu.getItemInSlot(BLUEPRINT_SLOT);

        if (blueprint == null || blueprint.getType() == Material.AIR) {
            sendFeedback(location, FeedbackType.NO_BLUEPRINT_FOUND);
            return false;
        }

        final long networkCharge = root.getRootPower();

        if (networkCharge < this.chargePerCraft) {
            sendFeedback(location, FeedbackType.NOT_ENOUGH_POWER);
            return false;
        }

        BlueprintInstance instance = AutoCrafter.INSTANCE_MAP.get(location);

        if (instance == null) {
            final SlimefunItem item = SlimefunItem.getByItem(blueprint);
            if (!isValidBlueprint(item)) {
                sendFeedback(location, FeedbackType.INVALID_BLUEPRINT);
                return false;
            }

            final ItemMeta blueprintMeta = blueprint.getItemMeta();
            BlueprintInstance decoded = Keys.getBlueprintInstance(blueprintMeta);
            if (decoded == null) {
                sendFeedback(location, FeedbackType.NO_BLUEPRINT_INSTANCE_FOUND);
                return false;
            }

            if (decoded == BlueprintInstance.INVALID) {
                sendFeedback(location, FeedbackType.BROKEN_BLUEPRINT);
                return false;
            }

            setCache(blockMenu, decoded);
            instance = decoded;
        }

        final ItemStack output = blockMenu.getItemInSlot(OUTPUT_SLOT);
        int blueprintAmount = canBlueprintStack() ? blueprint.getAmount() : 1;

        ItemStack targetOutput = instance.getItemStack();
        if (targetOutput == null || targetOutput.getType() == Material.AIR || targetOutput.getAmount() <= 0) {
            sendFeedback(location, FeedbackType.BROKEN_BLUEPRINT);
            return false;
        }
        if (output != null
            && output.getType() != Material.AIR
            && (output.getAmount() + targetOutput.getAmount() * blueprintAmount > output.getMaxStackSize()
            || !StackUtils.itemsMatch(targetOutput, output))) {
            sendFeedback(location, FeedbackType.OUTPUT_FULL);
            return false;
        }

        if (tryCraft(blockMenu, instance, root, blueprintAmount, output)) {
            root.removeRootPower(this.chargePerCraft);
            return true;
        }
        return false;
    }

    @SuppressWarnings("DataFlowIssue")
    private boolean tryCraft(
        @NotNull BlockMenu blockMenu,
        @NotNull BlueprintInstance instance,
        @NotNull NetworkRoot root,
        int blueprintAmount,
        @Nullable ItemStack existing) {
        /*
         * Cache an aggregated ingredient plan per decoded blueprint. Recipes commonly repeat the same
         * ingredient in several crafting-grid slots; requesting the combined amount once avoids walking
         * the network multiple times for an identical item on every Auto Crafter tick.
         */
        final Location location = blockMenu.getLocation();
        final List<IngredientRequest> ingredientPlan = INGREDIENT_PLAN_MAP.computeIfAbsent(
            location.clone(), ignored -> buildIngredientPlan(instance));
        final ItemStack[] fetcheds = new ItemStack[ingredientPlan.size()];

        for (int i = 0; i < ingredientPlan.size(); i++) {
            final IngredientRequest ingredient = ingredientPlan.get(i);
            final long scaledAmount = (long) ingredient.amount() * blueprintAmount;
            if (scaledAmount <= 0 || scaledAmount > Integer.MAX_VALUE) {
                returnItems(root, fetcheds, blockMenu);
                sendFeedback(location, FeedbackType.RESULT_IS_TOO_LARGE);
                return false;
            }

            final int requestedAmount = (int) scaledAmount;
            final ItemStack fetched = root.getItemStack0(
                location, new ItemRequest(ingredient.template(), requestedAmount));
            fetcheds[i] = fetched;
            if (fetched == null || fetched.getAmount() < requestedAmount) {
                returnItems(root, fetcheds, blockMenu);
                sendFeedback(location, FeedbackType.NOT_ENOUGH_ITEMS_IN_NETWORK);
                return false;
            }
        }

        if (root.isDisplayParticles()) {
            final Location particleLocation = location.clone().add(0.5, 1.1, 0.5);
            if (particleLocation.getWorld() != null) {
                particleLocation.getWorld().spawnParticle(Particle.WAX_OFF, particleLocation, 0, 0, 4, 0);
            }
        }

        ItemStack crafted = instance.getItemStack().clone();

        crafted.setAmount(crafted.getAmount() * blueprintAmount);

        if (crafted.getAmount() > crafted.getMaxStackSize()) {
            returnItems(root, fetcheds, blockMenu);
            sendFeedback(location, FeedbackType.RESULT_IS_TOO_LARGE);
            return false;
        }

        if (!withholding && existing != null && existing.getType() != Material.AIR) {
            root.uncontrolAccessInput(location);
            root.addItemStack0(location, crafted);
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
            NetworkTransferUtils.rollbackNetworkWithdrawal(root, location, crafted, location, "auto-crafter output");
        }
        sendFeedback(location, FeedbackType.WORKING);
        return true;
    }

    private static @NotNull List<IngredientRequest> buildIngredientPlan(@NotNull BlueprintInstance instance) {
        final List<IngredientRequest> plan = new ArrayList<>();
        for (ItemStack requested : instance.getRecipeItems()) {
            if (requested == null || requested.getType() == Material.AIR || requested.getAmount() <= 0) {
                continue;
            }

            boolean merged = false;
            for (int i = 0; i < plan.size(); i++) {
                final IngredientRequest existing = plan.get(i);
                if (StackUtils.itemsMatch(existing.template(), requested)) {
                    plan.set(i, new IngredientRequest(existing.template(), existing.amount() + requested.getAmount()));
                    merged = true;
                    break;
                }
            }

            if (!merged) {
                final ItemStack template = requested.clone();
                template.setAmount(1);
                plan.add(new IngredientRequest(template, requested.getAmount()));
            }
        }
        return List.copyOf(plan);
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
        clearRuntimeCache(blockMenu.getLocation());
    }

    public void setCache(@NotNull BlockMenu blockMenu, @NotNull BlueprintInstance blueprintInstance) {
        if (!blockMenu.hasViewer()) {
            final Location location = blockMenu.getLocation();
            INSTANCE_MAP.putIfAbsent(location.clone(), blueprintInstance);
            INGREDIENT_PLAN_MAP.remove(location);
        }
    }

    @Override
    protected void postBreak(@NotNull BlockBreakEvent event) {
        super.postBreak(event);
        clearRuntimeCache(event.getBlock().getLocation());
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

    private record IngredientRequest(@NotNull ItemStack template, int amount) {
    }
}

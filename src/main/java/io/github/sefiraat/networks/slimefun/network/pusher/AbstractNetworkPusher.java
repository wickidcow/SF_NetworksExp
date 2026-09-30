package io.github.sefiraat.networks.slimefun.network.pusher;

import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.api.helpers.Icon;
import com.balugaq.netex.api.interfaces.SoftCellBannable;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.slimefun.network.NetworkDirectional;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("DuplicatedCode")
public abstract class AbstractNetworkPusher extends NetworkDirectional implements SoftCellBannable {
    /**
     * Bounds destination-routing and network-withdrawal work from multi-template pushers in one SF tick.
     * The classic four-slot pusher remains full-speed, while More/Best pushers rotate through larger
     * template sets instead of issuing every expensive request in the same server tick.
     */
    private static final int MAX_UNIQUE_REQUESTS_PER_TICK = 4;

    /**
     * Failed requests are retried quickly at first, then progressively less often while a destination
     * remains unable to accept that exact item. Successful transfers immediately clear the backoff.
     */
    private static final int FAILED_RETRY_BASE_TICKS = 4;
    private static final int FAILED_RETRY_MAX_TICKS = 20;
    private static final long NANOS_PER_TICK = 50_000_000L;
    private static final long STALE_BACKOFF_RETENTION_NANOS = 30_000_000_000L;
    private static final int BACKOFF_PRUNE_MASK = 0xFF;
    private static final Map<PushRequestKey, FailureBackoff> FAILED_REQUEST_BACKOFF = new ConcurrentHashMap<>();
    private static final AtomicInteger BACKOFF_PRUNE_COUNTER = new AtomicInteger();

    private static final String RECIPE_AWARE_KEY = "recipe-aware";
    private static final int DEFAULT_RECIPE_BUFFER_BATCHES = 8;

    private static final int NORTH_SLOT = 11;
    private static final int SOUTH_SLOT = 29;
    private static final int EAST_SLOT = 21;
    private static final int WEST_SLOT = 19;
    private static final int UP_SLOT = 14;
    private static final int DOWN_SLOT = 32;

    private final @NotNull ItemSetting<Integer> recipeBufferBatches;

    public AbstractNetworkPusher(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.PUSHER);
        this.recipeBufferBatches =
            new IntRangeSetting(this, "recipe_buffer_batches", DEFAULT_RECIPE_BUFFER_BATCHES, 1, 64);
        addItemSetting(this.recipeBufferBatches);
        for (int slot : getItemSlots()) {
            this.getSlotsToDrop().add(slot);
        }
    }

    @Override
    protected void onTick(@Nullable BlockMenu blockMenu, @NotNull Block block) {
        super.onTick(blockMenu, block);
        if (blockMenu != null) {
            updateRecipeAwareControl(blockMenu);
            tryPushItem(blockMenu);
        }
    }

    private void updateRecipeAwareControl(@NotNull BlockMenu blockMenu) {
        if (!blockMenu.hasViewer()) {
            return;
        }

        final int modeSlot = getRecipeAwareModeSlot();
        if (modeSlot < 0) {
            return;
        }

        final boolean enabled = isRecipeAware(blockMenu);
        blockMenu.replaceExistingItem(modeSlot, createRecipeAwareModeStack(enabled));
        blockMenu.addMenuClickHandler(modeSlot, (player, slot, itemStack, clickAction) -> {
            final boolean next = !isRecipeAware(blockMenu);
            StorageCacheUtils.setData(blockMenu.getLocation(), RECIPE_AWARE_KEY, Boolean.toString(next));
            clearBackoffForSource(blockMenu.getLocation());
            blockMenu.replaceExistingItem(modeSlot, createRecipeAwareModeStack(next));
            return false;
        });
    }

    private int getRecipeAwareModeSlot() {
        final int[] slots = getOtherBackgroundSlots();
        return slots == null || slots.length == 0 ? -1 : slots[0];
    }

    private @NotNull ItemStack createRecipeAwareModeStack(boolean enabled) {
        final int bufferedBatches = recipeBufferBatches.getValue();
        return new CustomItemStack(
            enabled ? Material.CRAFTING_TABLE : Material.HOPPER,
            enabled ? "&aRecipe-Aware Mode: ON" : "&7Recipe-Aware Mode: OFF",
            "",
            "&fOFF: template items use normal pusher behavior.",
            "&fON: duplicate template slots and template stack amounts",
            "&fdefine the ingredient quantities for one recipe.",
            "&fThe pusher keeps up to &b" + bufferedBatches + " &frecipe batches",
            "&fbuffered in the target's valid input slots.",
            "",
            "&eClick to toggle.");
    }

    private static boolean isRecipeAware(@NotNull BlockMenu blockMenu) {
        return Boolean.parseBoolean(StorageCacheUtils.getData(blockMenu.getLocation(), RECIPE_AWARE_KEY));
    }

    private void tryPushItem(@NotNull BlockMenu blockMenu) {
        final NodeDefinition definition = NetworkStorage.getNode(blockMenu.getLocation());

        if (definition == null || definition.getNode() == null) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_NETWORK_FOUND);
            return;
        }

        final NetworkRoot root = definition.getNode().getRoot();
        if (checkSoftCellBan(blockMenu.getLocation(), root)) {
            return;
        }

        final BlockFace direction = getCurrentDirection(blockMenu);
        final Block sourceBlock = blockMenu.getBlock();
        final Block targetBlock = sourceBlock.getRelative(direction);
        final BlockMenu targetMenu = StorageCacheUtils.getMenu(targetBlock.getLocation());

        if (targetMenu == null) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_TARGET_BLOCK);
            return;
        }

        // Do not mutate a foreign menu off-thread while a player is interacting with it.
        if (!Bukkit.isPrimaryThread() && targetMenu.hasViewer()) {
            return;
        }

        final boolean recipeAware = isRecipeAware(blockMenu);
        final List<PushRequest> requests = collectPushRequests(blockMenu, recipeAware);
        if (requests.isEmpty()) {
            return;
        }

        /*
         * Item-aware destinations such as Supreme machines can perform recipe selection and multiple
         * similarity scans every time transport slots are requested. More/Best Pushers previously did
         * that for every configured template in one tick, which can produce multi-millisecond bursts.
         * Rotate a bounded window through the unique requests instead. Failed requests additionally use
         * a short adaptive backoff so a full/busy destination is not queried again every single tick.
         */
        final int requestCount = requests.size();
        final int attemptBudget = Math.min(MAX_UNIQUE_REQUESTS_PER_TICK, requestCount);
        final long rotationSeed = sourceBlock.getWorld().getGameTime() * MAX_UNIQUE_REQUESTS_PER_TICK
            + sourceBlock.getX() * 31L
            + sourceBlock.getY() * 17L
            + sourceBlock.getZ();
        final int startIndex = (int) Math.floorMod(rotationSeed, (long) requestCount);
        final long now = System.nanoTime();
        maybePruneBackoff(now);

        int attempted = 0;
        boolean movedAny = false;
        for (int offset = 0; offset < requestCount && attempted < attemptBudget; offset++) {
            final PushRequest request = requests.get((startIndex + offset) % requestCount);
            final ItemStack template = request.template;
            final PushRequestKey requestKey = createRequestKey(sourceBlock, targetBlock, template);

            // Cooling requests do not consume the per-tick budget, leaving room for other ingredients.
            if (isBackedOff(requestKey, now)) {
                continue;
            }
            attempted++;

            final int[] slots = BlockMenuUtil.getSafeTransportSlots(
                targetMenu,
                ItemTransportFlow.INSERT,
                template);

            if (slots.length == 0) {
                recordFailure(requestKey, now);
                continue;
            }

            final int transferAmount;
            if (recipeAware) {
                final int existing = countMatchingItems(targetMenu, template, slots);
                final int insertCapacity =
                    NetworkTransferUtils.getMenuInsertCapacity(targetMenu, template, slots);
                transferAmount = RecipeAwarePusherPlanner.calculateTransferAmount(
                    request.amount,
                    recipeBufferBatches.getValue(),
                    existing,
                    insertCapacity);

                if (transferAmount <= 0) {
                    FAILED_REQUEST_BACKOFF.remove(requestKey);
                    continue;
                }
            } else {
                transferAmount = request.amount;
            }

            final int moved = NetworkTransferUtils.moveNetworkItemIntoMenu(
                root,
                blockMenu.getLocation(),
                targetMenu,
                template,
                transferAmount,
                slots);

            if (moved > 0) {
                FAILED_REQUEST_BACKOFF.remove(requestKey);
                movedAny = true;
            } else {
                recordFailure(requestKey, now);
            }
        }

        // If every selected request is cooling down, keep the current feedback state and do no destination work.
        if (attempted == 0) {
            return;
        }

        // Feedback and particles are visual state, so update them once per pusher tick rather than per template.
        sendFeedback(
            blockMenu.getLocation(),
            movedAny ? FeedbackType.WORKING : FeedbackType.NO_ITEM_FOUND);
        if (movedAny && root.isDisplayParticles()) {
            showParticle(blockMenu.getLocation(), direction);
        }
    }

    /**
     * Builds one request per unique template item. Normal mode preserves the historic per-template
     * stack transfer allowance. Recipe-aware mode instead treats duplicate template slots and each
     * template stack amount as the ingredient quantity for one recipe batch.
     */
    private @NotNull List<PushRequest> collectPushRequests(
        @NotNull BlockMenu blockMenu,
        boolean recipeAware) {
        final int[] itemSlots = getItemSlots();
        final List<PushRequest> requests = new ArrayList<>(itemSlots.length);

        /*
         * Template counts are tiny (4/9/12), so a compact linear list is cheaper than building
         * a LinkedHashMap and then copying its entry set into another ArrayList every ticker pass.
         * We still clone each unique template once so backoff keys own stable one-amount snapshots.
         */
        for (int itemSlot : itemSlots) {
            final ItemStack testItem = blockMenu.getItemInSlot(itemSlot);
            if (testItem == null || testItem.getType() == Material.AIR) {
                continue;
            }

            final int configuredAmount = recipeAware
                ? Math.max(1, testItem.getAmount())
                : Math.max(1, testItem.getMaxStackSize());
            final ItemStack template = testItem.clone();
            template.setAmount(1);

            boolean merged = false;
            for (PushRequest existing : requests) {
                if (StackUtils.itemsMatch(existing.template, template)) {
                    existing.amount = saturatingAdd(existing.amount, configuredAmount);
                    merged = true;
                    break;
                }
            }

            if (!merged) {
                requests.add(new PushRequest(template, configuredAmount));
            }
        }

        return requests;
    }

    private static int countMatchingItems(
        @NotNull BlockMenu targetMenu,
        @NotNull ItemStack template,
        int @NotNull [] slots) {
        long count = 0L;
        for (int slot : slots) {
            if (slot < 0 || slot >= targetMenu.getSize()) {
                continue;
            }

            final ItemStack existing = targetMenu.getItemInSlot(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                continue;
            }

            if (StackUtils.itemsMatch(template, existing)) {
                count += existing.getAmount();
                if (count >= Integer.MAX_VALUE) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return (int) count;
    }

    private static void clearBackoffForSource(@NotNull org.bukkit.Location location) {
        final UUID worldId = location.getWorld().getUID();
        final int x = location.getBlockX();
        final int y = location.getBlockY();
        final int z = location.getBlockZ();
        FAILED_REQUEST_BACKOFF.keySet().removeIf(key ->
            key.worldId().equals(worldId)
                && key.sourceX() == x
                && key.sourceY() == y
                && key.sourceZ() == z);
    }

    private static @NotNull PushRequestKey createRequestKey(
        @NotNull Block sourceBlock,
        @NotNull Block targetBlock,
        @NotNull ItemStack template) {
        /*
         * collectPushRequests() already creates a private one-amount clone for every unique
         * template. Reuse that immutable per-tick snapshot here; cloning it again for a
         * backoff lookup created hundreds/thousands of short-lived ItemStacks on large networks.
         * Failed keys safely retain this snapshot because nothing mutates it after collection.
         */
        return new PushRequestKey(
            sourceBlock.getWorld().getUID(),
            sourceBlock.getX(),
            sourceBlock.getY(),
            sourceBlock.getZ(),
            targetBlock.getX(),
            targetBlock.getY(),
            targetBlock.getZ(),
            template);
    }

    private static boolean isBackedOff(@NotNull PushRequestKey requestKey, long now) {
        final FailureBackoff backoff = FAILED_REQUEST_BACKOFF.get(requestKey);
        return backoff != null && now < backoff.retryAfterNanos();
    }

    private static void recordFailure(@NotNull PushRequestKey requestKey, long now) {
        FAILED_REQUEST_BACKOFF.compute(requestKey, (key, previous) -> {
            final int failures = previous == null
                ? 1
                : Math.min(previous.consecutiveFailures() + 1, 31);
            final int shift = Math.min(failures - 1, 3);
            final int delayTicks = Math.min(FAILED_RETRY_MAX_TICKS, FAILED_RETRY_BASE_TICKS << shift);
            return new FailureBackoff(failures, now + delayTicks * NANOS_PER_TICK);
        });
    }

    private static void maybePruneBackoff(long now) {
        if ((BACKOFF_PRUNE_COUNTER.incrementAndGet() & BACKOFF_PRUNE_MASK) != 0) {
            return;
        }

        FAILED_REQUEST_BACKOFF.entrySet().removeIf(entry ->
            entry.getValue().retryAfterNanos() + STALE_BACKOFF_RETENTION_NANOS < now);
    }

    private static int saturatingAdd(int left, int right) {
        final long sum = (long) left + right;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
    }

    private static final class PushRequest {
        private final ItemStack template;
        private int amount;

        private PushRequest(@NotNull ItemStack template, int amount) {
            this.template = template;
            this.amount = amount;
        }
    }

    private record PushRequestKey(
        UUID worldId,
        int sourceX,
        int sourceY,
        int sourceZ,
        int targetX,
        int targetY,
        int targetZ,
        ItemStack template) {
    }

    private record FailureBackoff(int consecutiveFailures, long retryAfterNanos) {
    }

    @Override
    public int getNorthSlot() {
        return NORTH_SLOT;
    }

    @Override
    public int getSouthSlot() {
        return SOUTH_SLOT;
    }

    @Override
    public int getEastSlot() {
        return EAST_SLOT;
    }

    @Override
    public int getWestSlot() {
        return WEST_SLOT;
    }

    @Override
    public int getUpSlot() {
        return UP_SLOT;
    }

    @Override
    public int getDownSlot() {
        return DOWN_SLOT;
    }

    @Override
    protected Particle.@NotNull DustOptions getDustOptions() {
        return new Particle.DustOptions(Color.MAROON, 1);
    }

    @Nullable
    @Override
    protected ItemStack getOtherBackgroundStack() {
        return Icon.PUSHER_TEMPLATE_BACKGROUND_STACK;
    }

    public abstract int @NotNull [] getBackgroundSlots();

    public abstract int @NotNull [] getOtherBackgroundSlots();

    public abstract int @NotNull [] getItemSlots();
}

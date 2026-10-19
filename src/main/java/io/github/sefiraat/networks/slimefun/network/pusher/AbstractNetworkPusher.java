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
import io.github.thebusybiscuit.slimefun4.libraries.dough.common.ChatColors;
import io.github.thebusybiscuit.slimefun4.utils.ChatUtils;
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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
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
    private static final String RECIPE_BUFFER_BATCHES_KEY = "recipe-aware-buffer-batches";
    private static final int DEFAULT_RECIPE_BUFFER_BATCHES = 8;
    // Zero means a machine has no override. Read the current server setting on demand so
    // a configuration reload still reaches existing, non-customized pushers.
    private static final int INHERIT_RECIPE_BUFFER_BATCHES = 0;
    private static final Map<Location, Integer> RECIPE_BUFFER_BATCHES = new ConcurrentHashMap<>();

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
            new IntRangeSetting(this, "recipe_buffer_batches", 1, DEFAULT_RECIPE_BUFFER_BATCHES, 64);
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

        final Location location = blockMenu.getLocation();
        // Never replace a foreign item that an older, initially empty GUI allowed
        // into this slot. Allow its recovery before recreating the control.
        if (!refreshRecipeAwareControl(blockMenu, modeSlot)) {
            return;
        }
        blockMenu.addMenuClickHandler(modeSlot, (player, slot, itemStack, clickAction) -> {
            // An admin or another addon can replace a block while its old menu is still open.
            // Never write per-machine settings through a stale menu, for any click type.
            if (getCurrentRecipeAwareMenu(location, blockMenu) == null) {
                player.sendMessage(ChatColors.color("&cThat Network Pusher is no longer available."));
                return false;
            }
            if (!refreshRecipeAwareControl(blockMenu, modeSlot)) {
                // A foreign item can arrive from another addon after the handler
                // was registered. Permit its ordinary recovery, not a toggle.
                player.sendMessage(ChatColors.color(
                    "&eRemove the item from the Recipe-Aware control slot first."));
                return true;
            }
            if (clickAction.isShiftClicked()) {
                final int delta = clickAction.isRightClicked() ? 1 : -1;
                final int next = RecipeAwarePusherPlanner.stepBufferedBatches(
                    getRecipeBufferBatches(location),
                    delta);
                setRecipeBufferBatches(location, next);
                clearBackoffForSource(location);
                refreshRecipeAwareControl(blockMenu, modeSlot);
                return false;
            }

            if (clickAction.isRightClicked()) {
                player.sendMessage(ChatColors.color(
                    "&eEnter the Recipe-Aware batch target (1-" + RecipeAwarePusherPlanner.MAX_BUFFERED_BATCHES + ")"));
                ChatUtils.awaitInput(player, input -> {
                    final BlockMenu liveMenu = getCurrentRecipeAwareMenu(location, blockMenu);
                    if (liveMenu == null) {
                        RECIPE_BUFFER_BATCHES.remove(location);
                        player.sendMessage(ChatColors.color(
                            "&cThat Network Pusher is no longer available."));
                        return;
                    }

                    if (!refreshRecipeAwareControl(liveMenu, modeSlot)) {
                        player.sendMessage(ChatColors.color(
                            "&eRemove the item from the Recipe-Aware control slot first."));
                        liveMenu.open(player);
                        return;
                    }

                    try {
                        final int value = RecipeAwarePusherPlanner.parseBufferedBatches(input);
                        if (!RecipeAwarePusherPlanner.isValidBufferedBatches(value)) {
                            player.sendMessage(ChatColors.color(
                                "&cEnter a whole number between "
                                    + RecipeAwarePusherPlanner.MIN_BUFFERED_BATCHES
                                    + " and "
                                    + RecipeAwarePusherPlanner.MAX_BUFFERED_BATCHES
                                    + "."));
                        } else {
                            setRecipeBufferBatches(location, value);
                            clearBackoffForSource(location);
                            player.sendMessage(ChatColors.color(
                                "&aRecipe-Aware batch target set to &b" + value + "&a."));
                        }

                        refreshRecipeAwareControl(liveMenu, modeSlot);
                        liveMenu.open(player);
                    } catch (NumberFormatException exception) {
                        player.sendMessage(ChatColors.color(
                            "&cEnter a whole number between "
                                + RecipeAwarePusherPlanner.MIN_BUFFERED_BATCHES
                                + " and "
                                + RecipeAwarePusherPlanner.MAX_BUFFERED_BATCHES
                                + "."));
                        liveMenu.open(player);
                    }
                });
                return false;
            }

            final boolean next = !isRecipeAware(blockMenu);
            StorageCacheUtils.setData(location, RECIPE_AWARE_KEY, Boolean.toString(next));
            clearBackoffForSource(location);
            refreshRecipeAwareControl(blockMenu, modeSlot);
            return false;
        });
    }

    /**
     * Refresh only recognized GUI icons. A previously empty control slot may
     * contain a real player item on upgraded servers; never destroy that stack.
     */
    private boolean refreshRecipeAwareControl(@NotNull BlockMenu blockMenu, int modeSlot) {
        final Location location = blockMenu.getLocation();
        final boolean enabled = isRecipeAware(blockMenu);
        final ItemStack desired = createRecipeAwareModeStack(location, enabled);
        final ItemStack present = blockMenu.getItemInSlot(modeSlot);
        final ItemStack alternate =
            present == null || present.getType().isAir() || present.isSimilar(desired)
                ? desired : createRecipeAwareModeStack(location, !enabled);
        final RecipeAwareControlSlot.Decision decision =
            RecipeAwareControlSlot.inspect(present, desired, alternate);

        if (decision == RecipeAwareControlSlot.Decision.FOREIGN) {
            return false;
        }
        if (decision == RecipeAwareControlSlot.Decision.REFRESH) {
            blockMenu.replaceExistingItem(modeSlot, desired);
        }
        return true;
    }

    private @Nullable BlockMenu getCurrentRecipeAwareMenu(
        @NotNull Location location,
        @NotNull BlockMenu expectedMenu) {

        if (!StorageCacheUtils.isBlock(location, getId())) {
            return null;
        }

        final BlockMenu liveMenu = StorageCacheUtils.getMenu(location);
        return liveMenu == expectedMenu ? liveMenu : null;
    }

    private int getRecipeAwareModeSlot() {
        final int[] slots = getOtherBackgroundSlots();
        return slots == null || slots.length == 0 ? -1 : slots[0];
    }

    private @NotNull ItemStack createRecipeAwareModeStack(
        @NotNull Location location,
        boolean enabled) {

        final int bufferedBatches = getRecipeBufferBatches(location);
        final ItemStack control = new CustomItemStack(
            enabled ? Material.CRAFTING_TABLE : Material.HOPPER,
            enabled ? "&aRecipe-Aware Mode: ON" : "&7Recipe-Aware Mode: OFF",
            "",
            "&fOFF: template items use normal pusher behavior.",
            "&fON: duplicate template slots and template stack amounts",
            "&fdefine the ingredient quantities for one recipe.",
            "&fBatch target: &b" + bufferedBatches,
            "&fThe pusher keeps up to that many recipe batches",
            "&fbuffered in the target's valid input slots.",
            "",
            "&eLeft-click: toggle Recipe-Aware mode",
            "&eRight-click: enter an exact batch target",
            "&eShift-left: -1 batch",
            "&eShift-right: +1 batch");
        control.setAmount(bufferedBatches);
        return control;
    }

    private static boolean isRecipeAware(@NotNull BlockMenu blockMenu) {
        return Boolean.parseBoolean(StorageCacheUtils.getData(blockMenu.getLocation(), RECIPE_AWARE_KEY));
    }

    private int getRecipeBufferBatches(@NotNull Location location) {
        final Integer cached = RECIPE_BUFFER_BATCHES.get(location);
        if (cached != null && cached != INHERIT_RECIPE_BUFFER_BATCHES) {
            return cached;
        }

        final int fallback = RecipeAwarePusherPlanner.sanitizeBufferedBatches(
            recipeBufferBatches.getValue(),
            DEFAULT_RECIPE_BUFFER_BATCHES);
        if (cached != null) {
            return fallback;
        }

        final String raw = StorageCacheUtils.getData(location, RECIPE_BUFFER_BATCHES_KEY);
        int value = INHERIT_RECIPE_BUFFER_BATCHES;
        if (raw != null) {
            try {
                value = RecipeAwarePusherPlanner.sanitizeBufferedBatches(
                    Integer.parseInt(raw),
                    fallback);
            } catch (NumberFormatException ignored) {
                value = fallback;
            }
        }

        // Repair only malformed *stored overrides*. Never persist the inherited server
        // setting; leaving it unset allows future server-wide configuration changes.
        if (raw != null && !Integer.toString(value).equals(raw)) {
            StorageCacheUtils.setData(location, RECIPE_BUFFER_BATCHES_KEY, Integer.toString(value));
        }
        RECIPE_BUFFER_BATCHES.putIfAbsent(location.clone(), value);
        final int effective = RECIPE_BUFFER_BATCHES.getOrDefault(location, value);
        return effective == INHERIT_RECIPE_BUFFER_BATCHES ? fallback : effective;
    }

    private static void setRecipeBufferBatches(@NotNull Location location, int value) {
        final int valid = RecipeAwarePusherPlanner.sanitizeBufferedBatches(
            value,
            DEFAULT_RECIPE_BUFFER_BATCHES);
        // Only advertise the new runtime value after block persistence succeeds.
        StorageCacheUtils.setData(location, RECIPE_BUFFER_BATCHES_KEY, Integer.toString(valid));
        RECIPE_BUFFER_BATCHES.put(location.clone(), valid);
    }

    @Override
    public void onPlace(@NotNull BlockPlaceEvent event) {
        // Reuse of the same location must not inherit an older machine's cached target.
        RECIPE_BUFFER_BATCHES.remove(event.getBlock().getLocation());
        super.onPlace(event);
    }

    @Override
    protected void onBreak(@NotNull BlockBreakEvent event) {
        RECIPE_BUFFER_BATCHES.remove(event.getBlock().getLocation());
        super.onBreak(event);
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

        // Presence must be in a physical input accepted for that exact item. Display
        // and output stacks do not reserve the missing ingredient's input capacity.
        final int[] recipeInputSlots = recipeAware && requestCount > 1
            ? getRecipeInputSlots(targetMenu)
            : null;
        final boolean[] represented = recipeInputSlots != null
            ? findRepresentedRecipeIngredients(targetMenu, requests, recipeInputSlots)
            : null;
        int unrepresented = 0;
        if (represented != null) {
            for (boolean present : represented) {
                if (!present) {
                    unrepresented++;
                }
            }
        }
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
            final int requestIndex = (startIndex + offset) % requestCount;
            final PushRequest request = requests.get(requestIndex);
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
                // The normal transfer discovery can confirm a type left unverified by
                // the bounded presence scan without another item-aware routing call.
                if (represented != null && !represented[requestIndex] && existing > 0
                    && isRecipeIngredientPresent(targetMenu, template, recipeInputSlots, slots)) {
                    represented[requestIndex] = true;
                    unrepresented--;
                }
                final int insertCapacity =
                    NetworkTransferUtils.getMenuInsertCapacity(targetMenu, template, slots);
                final boolean peerMissing = represented != null
                    && unrepresented > (represented[requestIndex] ? 0 : 1);
                transferAmount = RecipeAwarePusherPlanner.calculateFairTransferAmount(
                    request.amount,
                    getRecipeBufferBatches(blockMenu.getLocation()),
                    existing,
                    insertCapacity,
                    peerMissing);

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
                if (represented != null && !represented[requestIndex]) {
                    represented[requestIndex] = true;
                    unrepresented--;
                }
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

            final int perSlotLimit = recipeAware
                ? Math.max(1, testItem.getAmount())
                : Math.max(1, testItem.getMaxStackSize());
            final ItemStack template = testItem.clone();
            template.setAmount(1);

            boolean merged = false;
            for (PushRequest existing : requests) {
                if (StackUtils.itemsMatch(existing.template, template)) {
                    existing.amount = saturatingAdd(existing.amount, perSlotLimit);
                    merged = true;
                    break;
                }
            }

            if (!merged) {
                requests.add(new PushRequest(template, perSlotLimit));
            }
        }

        return requests;
    }

    /**
     * Read the physical input declaration once. Item-aware null queries are not a
     * substitute: Supreme legitimately returns no recipe slots for a null item.
     */
    static int @NotNull [] getRecipeInputSlots(@NotNull BlockMenu targetMenu) {
        try {
            final int[] slots = targetMenu.getPreset()
                .getSlotsAccessedByItemTransport(ItemTransportFlow.INSERT);
            return slots == null ? new int[0] : slots;
        } catch (RuntimeException | AbstractMethodError | NoSuchMethodError ignored) {
            return new int[0];
        }
    }

    /**
     * Confirm at most four physically present types with their item-aware input
     * permission. Unknown types remain missing; never rediscover all 9/12 templates.
     * Every transfer still obtains fresh routing rather than reusing these snapshots.
     */
    static boolean @NotNull [] findRepresentedRecipeIngredients(
        @NotNull BlockMenu targetMenu,
        @NotNull List<PushRequest> requests,
        int @NotNull [] inputSlots) {

        final boolean[] candidates = new boolean[requests.size()];
        for (int slot : inputSlots) {
            if (slot < 0 || slot >= targetMenu.getSize()) {
                continue;
            }
            final ItemStack existing = targetMenu.getItemInSlot(slot);
            if (existing == null || existing.getType() == Material.AIR || existing.getAmount() <= 0) {
                continue;
            }
            for (int i = 0; i < requests.size(); i++) {
                if (!candidates[i] && StackUtils.itemsMatch(requests.get(i).template, existing)) {
                    candidates[i] = true;
                    break;
                }
            }
        }

        final boolean[] represented = new boolean[requests.size()];
        int checked = 0;
        for (int i = 0; i < requests.size() && checked < MAX_UNIQUE_REQUESTS_PER_TICK; i++) {
            if (!candidates[i]) {
                continue;
            }
            checked++;
            final ItemStack template = requests.get(i).template;
            final int[] itemSlots = BlockMenuUtil.getSafeTransportSlots(
                targetMenu, ItemTransportFlow.INSERT, template);
            represented[i] = isRecipeIngredientPresent(targetMenu, template, inputSlots, itemSlots);
        }
        return represented;
    }

    static boolean isRecipeIngredientPresent(
        @NotNull BlockMenu targetMenu,
        @NotNull ItemStack template,
        int @NotNull [] inputSlots,
        int @NotNull [] itemSlots) {

        for (int slot : inputSlots) {
            if (slot < 0 || slot >= targetMenu.getSize()) {
                continue;
            }
            boolean accepted = false;
            for (int itemSlot : itemSlots) {
                if (slot == itemSlot) {
                    accepted = true;
                    break;
                }
            }
            if (!accepted) {
                continue;
            }
            final ItemStack existing = targetMenu.getItemInSlot(slot);
            if (existing != null && existing.getType() != Material.AIR && existing.getAmount() > 0
                && StackUtils.itemsMatch(template, existing)) {
                return true;
            }
        }
        return false;
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

    public static void resetRuntimeState() {
        RECIPE_BUFFER_BATCHES.clear();
        FAILED_REQUEST_BACKOFF.clear();
        BACKOFF_PRUNE_COUNTER.set(0);
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

    static final class PushRequest {
        private final ItemStack template;
        private int amount;

        PushRequest(@NotNull ItemStack template, int amount) {
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

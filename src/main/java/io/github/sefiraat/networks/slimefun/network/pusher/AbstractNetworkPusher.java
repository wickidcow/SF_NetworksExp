package io.github.sefiraat.networks.slimefun.network.pusher;

import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.api.helpers.Icon;
import com.balugaq.netex.api.interfaces.SoftCellBannable;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.slimefun.network.NetworkDirectional;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private static final int NORTH_SLOT = 11;
    private static final int SOUTH_SLOT = 29;
    private static final int EAST_SLOT = 21;
    private static final int WEST_SLOT = 19;
    private static final int UP_SLOT = 14;
    private static final int DOWN_SLOT = 32;

    public AbstractNetworkPusher(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.PUSHER);
        for (int slot : getItemSlots()) {
            this.getSlotsToDrop().add(slot);
        }
    }

    @Override
    protected void onTick(@Nullable BlockMenu blockMenu, @NotNull Block block) {
        super.onTick(blockMenu, block);
        if (blockMenu != null) {
            tryPushItem(blockMenu);
        }
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
        final BlockMenu targetMenu = StorageCacheUtils.getMenu(
            blockMenu.getBlock().getRelative(direction).getLocation());

        if (targetMenu == null) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_TARGET_BLOCK);
            return;
        }

        // Do not mutate a foreign menu off-thread while a player is interacting with it.
        if (!Bukkit.isPrimaryThread() && targetMenu.hasViewer()) {
            return;
        }

        final Map<ItemStack, Integer> pushRequests = collectPushRequests(blockMenu);
        if (pushRequests.isEmpty()) {
            return;
        }

        /*
         * Item-aware destinations such as Supreme machines can perform recipe selection and multiple
         * similarity scans every time transport slots are requested. More/Best Pushers previously did
         * that for every configured template in one tick, which can produce multi-millisecond bursts.
         * Rotate a bounded window through the unique requests instead. Multiplying game time by the
         * window size gives fair coverage without retaining per-block scheduler state.
         */
        final List<Map.Entry<ItemStack, Integer>> requests = new ArrayList<>(pushRequests.entrySet());
        final int requestCount = requests.size();
        final int attempts = Math.min(MAX_UNIQUE_REQUESTS_PER_TICK, requestCount);
        final Block sourceBlock = blockMenu.getBlock();
        final long rotationSeed = sourceBlock.getWorld().getGameTime() * MAX_UNIQUE_REQUESTS_PER_TICK
            + sourceBlock.getX() * 31L
            + sourceBlock.getY() * 17L
            + sourceBlock.getZ();
        final int startIndex = (int) Math.floorMod(rotationSeed, (long) requestCount);

        boolean movedAny = false;
        for (int attempt = 0; attempt < attempts; attempt++) {
            final Map.Entry<ItemStack, Integer> request = requests.get((startIndex + attempt) % requestCount);
            final ItemStack template = request.getKey();
            final int[] slots = BlockMenuUtil.getSafeTransportSlots(
                targetMenu,
                ItemTransportFlow.INSERT,
                template);

            if (slots.length == 0) {
                continue;
            }

            final int moved = NetworkTransferUtils.moveNetworkItemIntoMenu(
                root,
                blockMenu.getLocation(),
                targetMenu,
                template,
                request.getValue(),
                slots);
            movedAny |= moved > 0;
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
     * Builds one request per unique template item. Repeated template slots keep their original aggregate
     * transfer allowance while avoiding duplicate destination-routing and network-withdrawal calls.
     */
    private @NotNull Map<ItemStack, Integer> collectPushRequests(@NotNull BlockMenu blockMenu) {
        final Map<ItemStack, Integer> requests = new LinkedHashMap<>();

        for (int itemSlot : getItemSlots()) {
            final ItemStack testItem = blockMenu.getItemInSlot(itemSlot);
            if (testItem == null || testItem.getType() == Material.AIR) {
                continue;
            }

            final ItemStack template = testItem.clone();
            template.setAmount(1);
            final int perSlotLimit = Math.max(1, template.getMaxStackSize());

            boolean merged = false;
            for (Map.Entry<ItemStack, Integer> existing : requests.entrySet()) {
                if (StackUtils.itemsMatch(existing.getKey(), template)) {
                    existing.setValue(saturatingAdd(existing.getValue(), perSlotLimit));
                    merged = true;
                    break;
                }
            }

            if (!merged) {
                requests.put(template, perSlotLimit);
            }
        }

        return requests;
    }

    private static int saturatingAdd(int left, int right) {
        final long sum = (long) left + right;
        return sum >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) sum;
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

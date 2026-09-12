package io.github.sefiraat.networks.slimefun.network;

import com.balugaq.netex.utils.BlockMenuUtil;
import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.api.interfaces.SoftCellBannable;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
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
public class NetworkGrabber extends NetworkDirectional implements SoftCellBannable {

    public NetworkGrabber(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.GRABBER);
    }

    @Override
    protected void onTick(@Nullable BlockMenu blockMenu, @NotNull Block block) {
        super.onTick(blockMenu, block);
        if (blockMenu != null) {
            tryGrabItem(blockMenu);
        }
    }

    private void tryGrabItem(@NotNull BlockMenu blockMenu) {
        final NodeDefinition definition = NetworkStorage.getNode(blockMenu.getLocation());

        if (definition == null || definition.getNode() == null) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_NETWORK_FOUND);
            return;
        }

        final NetworkRoot root = definition.getNode().getRoot();
        final var accessor = blockMenu.getLocation();
        if (checkSoftCellBan(accessor, root)) {
            return;
        }

        final BlockFace direction = this.getCurrentDirection(blockMenu);
        final BlockMenu targetMenu = StorageCacheUtils.getMenu(
            blockMenu.getBlock().getRelative(direction).getLocation());

        if (targetMenu == null) {
            sendFeedback(accessor, FeedbackType.NO_TARGET_BLOCK);
            return;
        }

        // Legacy does not expose SF5's mutateInventorySafely helper. Preserve the async fast path,
        // but pause the transfer while a foreign menu is actively viewed rather than racing clicks.
        if (!Bukkit.isPrimaryThread() && targetMenu.hasViewer()) {
            return;
        }

        /*
         * The root already has the original transport-miss limiter. Once this accessor is limited,
         * asking an item-aware source for its withdraw slots cannot result in a successful deposit and
         * only repeats routing work. Preserve the existing recovery timer, but skip that dead work.
         */
        if (!root.allowAccessInput(accessor)) {
            sendFeedback(accessor, FeedbackType.ROOT_LIMITING_ACCESS_INPUT);
            return;
        }

        int[] slots = BlockMenuUtil.getSafeTransportSlots(targetMenu, ItemTransportFlow.WITHDRAW);

        for (int slot : slots) {
            final ItemStack itemStack = targetMenu.getItemInSlot(slot);

            if (itemStack != null && itemStack.getType() != Material.AIR) {
                final int moved = NetworkTransferUtils.moveMenuSlotIntoNetwork(
                    root, accessor, targetMenu, slot);
                if (moved > 0) {
                    sendFeedback(accessor, FeedbackType.WORKING);
                    if (root.isDisplayParticles()) {
                        showParticle(accessor, direction);
                    }
                    break;
                }

                // A failed deposit may have crossed the existing miss threshold during this scan.
                // Stop walking more source slots immediately if that happened.
                if (!root.allowAccessInput(accessor)) {
                    sendFeedback(accessor, FeedbackType.ROOT_LIMITING_ACCESS_INPUT);
                    return;
                }
            }
        }
    }

    @Override
    protected Particle.@NotNull DustOptions getDustOptions() {
        return new Particle.DustOptions(Color.FUCHSIA, 1);
    }
}

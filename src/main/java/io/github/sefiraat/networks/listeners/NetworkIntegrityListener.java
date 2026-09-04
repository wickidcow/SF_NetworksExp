package io.github.sefiraat.networks.listeners;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.slimefun.network.NetworkObject;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Closes lifecycle gaps where a Networks node can be changed without the normal Slimefun BlockBreakHandler.
 *
 * <p>World-mutation events either protect live network blocks from destructive physics/explosions, or schedule a
 * next-tick validation after normal break/place events. The delayed validation observes the final physical block
 * and Slimefun identity after all event handlers have completed.</p>
 */
public final class NetworkIntegrityListener implements Listener {

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(@NotNull BlockBreakEvent event) {
        scheduleValidation(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        scheduleValidation(event.getBlockPlaced().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(@NotNull BlockExplodeEvent event) {
        protectExplosionBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(@NotNull EntityExplodeEvent event) {
        protectExplosionBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityChangeBlock(@NotNull EntityChangeBlockEvent event) {
        if (isProtectedNetworkBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonExtend(@NotNull BlockPistonExtendEvent event) {
        if (containsProtectedNetworkBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPistonRetract(@NotNull BlockPistonRetractEvent event) {
        if (containsProtectedNetworkBlock(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(@NotNull BlockPhysicsEvent event) {
        if (isProtectedNetworkBlock(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    private void protectExplosionBlocks(@NotNull List<Block> blocks) {
        final List<Block> protectedBlocks = new ArrayList<>();
        for (Block block : blocks) {
            if (isProtectedNetworkBlock(block)) {
                protectedBlocks.add(block);
            }
        }
        blocks.removeAll(protectedBlocks);
    }

    private boolean containsProtectedNetworkBlock(@NotNull List<Block> blocks) {
        for (Block block : blocks) {
            if (isProtectedNetworkBlock(block)) {
                return true;
            }
        }
        return false;
    }

    private boolean isProtectedNetworkBlock(@NotNull Block block) {
        final SlimefunItem item = StorageCacheUtils.getSfItem(block.getLocation());
        return item != null && ExplosiveToolListener.isProtectedNetworkBlock(item);
    }

    private void scheduleValidation(@NotNull Location location) {
        final Location key = normalize(location);
        final NodeDefinition before = NetworkStorage.getNode(key);
        if (before == null) {
            return;
        }

        Networks.getInstance().getServer().getScheduler().runTask(Networks.getInstance(), () -> {
            NodeDefinition current = NetworkStorage.getNode(key);
            if (current == null) {
                return;
            }

            SlimefunItem liveItem = StorageCacheUtils.getSfItem(key);
            if (key.getBlock().getType().isAir()
                || !(liveItem instanceof NetworkObject networkObject)
                || networkObject.getNodeType() != current.getType()) {
                NetworkStorage.detachNode(key);
            }
        });
    }

    private static @NotNull Location normalize(@NotNull Location location) {
        Location normalized = location.clone();
        normalized.setX(location.getBlockX());
        normalized.setY(location.getBlockY());
        normalized.setZ(location.getBlockZ());
        normalized.setYaw(0.0F);
        normalized.setPitch(0.0F);
        return normalized;
    }
}

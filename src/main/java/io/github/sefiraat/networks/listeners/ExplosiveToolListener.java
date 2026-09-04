package io.github.sefiraat.networks.listeners;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.items.machines.AbstractGridNewStyle;
import com.ytdd9527.networksexpansion.implementation.machines.unit.NetworksDrawer;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.commands.NetworksMain;
import io.github.sefiraat.networks.slimefun.network.NetworkObject;
import io.github.thebusybiscuit.slimefun4.api.events.ExplosiveToolBreakBlocksEvent;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class ExplosiveToolListener implements Listener {

    @SuppressWarnings("deprecation")
    @EventHandler
    public void onExplosiveBlockBreak(@NotNull ExplosiveToolBreakBlocksEvent event) {
        final List<Block> blocksToRemove = new ArrayList<>();
        for (Block block : event.getAdditionalBlocks()) {
            final Location location = block.getLocation();

            final SlimefunItem item = StorageCacheUtils.getSfItem(location);
            if (item != null && isProtectedNetworkBlock(item)) {
                blocksToRemove.add(block);
                Networks.getInstance()
                    .debug("Disabled explosive block: " + NetworksMain.locationToString(block.getLocation()));
            }
        }
        event.getAdditionalBlocks().removeAll(blocksToRemove);
    }

    static boolean isProtectedNetworkBlock(@NotNull SlimefunItem item) {
        return item instanceof NetworkObject
            || item instanceof NetworksDrawer
            || item instanceof AbstractGridNewStyle;
    }
}

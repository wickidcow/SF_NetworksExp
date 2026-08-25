package com.balugaq.netex.api.enums;

import com.balugaq.netex.utils.BlockMenuUtil;
import com.balugaq.netex.utils.Lang;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.utils.ParticleUtil;
import io.github.sefiraat.networks.slimefun.network.NetworkDirectional;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockPlaceEvent;
import org.jspecify.annotations.NullMarked;

import java.util.HashSet;
import java.util.Set;

@NullMarked
public enum FacingPreset {
    NORTH,
    SOUTH,
    WEST,
    EAST,
    DOWN,
    UP,
    AUTO;

    private static final BlockFace[] FACES = new BlockFace[]{
        BlockFace.NORTH,
        BlockFace.SOUTH,
        BlockFace.WEST,
        BlockFace.EAST,
        BlockFace.DOWN,
        BlockFace.UP
    };

    public boolean tryApply(BlockPlaceEvent event, SlimefunBlockData blockData) {
        if (!(SlimefunItem.getById(blockData.getSfId()) instanceof NetworkDirectional)) {
            return false;
        }

        if (this != AUTO) {
            setFace(blockData, this.name());
            return true;
        }

        Player player = event.getPlayer();
        if (isPreferBlock(blockData, event.getBlockAgainst())) {
            setFace(blockData, getFace(event.getBlock(), event.getBlockAgainst()).name());
            return true;
        }

        // try 6 faces
        Set<BlockFace> preferFaces = new HashSet<>();
        for (BlockFace face : FACES) {
            if (isPreferBlock(blockData, event.getBlock().getRelative(face))) {
                preferFaces.add(face);
            }
        }

        var loc = blockData.getLocation();
        boolean isGrabber = blockData.getSfId().contains("TRANSFER") || blockData.getSfId().contains("GRABBER");
        boolean isPusher = blockData.getSfId().contains("TRANSFER") || blockData.getSfId().contains("PUSHER");
        if (preferFaces.isEmpty()) {
            for (BlockFace face : FACES) {
                Block b = event.getBlock().getRelative(face);
                if (isSlimefunBlock(b)) {
                    BlockMenu menu = StorageCacheUtils.getMenu(b.getLocation());
                    if (menu == null) {
                        continue;
                    }
                    if (isGrabber && BlockMenuUtil.getSafeTransportSlots(menu, ItemTransportFlow.WITHDRAW).length == 0) {
                        continue;
                    }
                    if (isPusher && BlockMenuUtil.getSafeTransportSlots(menu, ItemTransportFlow.INSERT).length == 0) {
                        continue;
                    }
                    setFace(blockData, face.name());
                    return true;
                }
            }
            player.sendMessage(String.format(Lang.getString("messages.unsupported-operation.comprehensive.unable_to_auto_set_face"), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            return false;
        } else if (preferFaces.size() == 1) {
            setFace(blockData, preferFaces.stream().findFirst().get().name());
            return true;
        } else {
            // preferFaces >= 2
            setFace(blockData, BlockFace.SELF.name());
            player.sendMessage(String.format(Lang.getString("messages.unsupported-operation.comprehensive.unable_to_auto_set_face"), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
            ParticleUtil.highlightBlock(player, loc, 4);
            return false;
        }
    }

    public void setFace(SlimefunBlockData blockData, String face) {
        blockData.setData(NetworkDirectional.DIRECTION, face);
    }

    public boolean isPreferBlock(SlimefunBlockData from, Block to) {
        if (from.getSfId().startsWith("NTW_") && from.getSfId().contains("VANILLA")) {
            if (!isSlimefunBlock(to) && to.getState() instanceof Container) {
                // prefer vanilla containers
                return true;
            }
        }

        var to_sf = StorageCacheUtils.getSfItem(to.getLocation());
        if (to_sf == null) return false;
        var fid = to_sf.getId();
        if (from.getSfId().contains("NTW_")) {
            var tid = to_sf.getId();
            if (from.getSfId().contains("MONITOR")) {
                // prefer drawer & storages (see BarrelType)
                return tid.contains("DRAWER") || tid.contains("BARREL") || tid.contains("STORAGE");
            }

            // prefer non-network or ntw_vanilla_grabber/pusher blocks
            if (tid.contains("NTW_") && tid.contains("VANILLA") && !tid.contains("LINE")) {
                return tid.contains("GRABBER") && (fid.contains("GRABBER") || fid.contains("TRANSFER"))
                    || tid.contains("PUSHER") && (fid.contains("PUSHER") || fid.contains("TRANSFER"));
            }

            return !tid.contains("NTW_");
        }

        // prefer sf blocks
        return true;
    }

    public boolean isSlimefunBlock(Block block) {
        return StorageCacheUtils.hasSlimefunBlock(block.getLocation());
    }

    public BlockFace getFace(Block from, Block to) {
        var face = from.getFace(to);
        if (face != null) return face;
        return BlockFace.SELF;
    }
}

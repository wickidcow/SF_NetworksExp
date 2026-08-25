package com.balugaq.netex.api.atrributes;

import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.block.BlockState;
import org.bukkit.block.BrewingStand;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface WhitelistedVanillaGrabber extends WhitelistedGrabber {
    default void grabInventory(
        @NotNull BlockMenu blockMenu,
        @NotNull BlockState blockState,
        @NotNull Inventory inventory,
        @NotNull NetworkRoot root,
        @NotNull List<ItemStack> templates) {

        if (inventory instanceof FurnaceInventory) {
            if (inTemplates(templates, inventory.getItem(2))) {
                moveInventorySlot(root, blockMenu, inventory, 2);
            } else if (inTemplates(templates, inventory.getItem(1))) {
                moveInventorySlot(root, blockMenu, inventory, 1);
            }
        } else if (inventory instanceof BrewerInventory) {
            if (!(blockState instanceof BrewingStand brewingStand) || brewingStand.getBrewingTime() > 0) {
                return;
            }

            if (inTemplates(templates, inventory.getItem(4))) {
                moveInventorySlot(root, blockMenu, inventory, 4);
                return;
            }

            for (int slot = 0; slot < 3; slot++) {
                if (inTemplates(templates, inventory.getItem(slot))) {
                    moveInventorySlot(root, blockMenu, inventory, slot);
                    break;
                }
            }
        } else {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                if (inTemplates(templates, inventory.getItem(slot))
                    && moveInventorySlot(root, blockMenu, inventory, slot)) {
                    break;
                }
            }
        }
    }

    private boolean moveInventorySlot(
        @NotNull NetworkRoot root,
        @NotNull BlockMenu blockMenu,
        @NotNull Inventory inventory,
        int slot) {
        return NetworkTransferUtils.moveInventorySlotIntoNetwork(
            root, blockMenu.getLocation(), inventory, slot) > 0;
    }
}

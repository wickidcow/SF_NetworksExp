package com.balugaq.netex.utils;

import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.utils.StackUtils;
import lombok.experimental.UtilityClass;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

/**
 * Inventory helpers with explicit remainder semantics.
 *
 * <p>The original implementation stopped after filling the first matching stack. On inventories with
 * several partial stacks this could report a remainder even though later slots still had capacity. The
 * implementation below always fills every compatible stack before consuming empty slots and never silently
 * discards an input remainder.</p>
 */
@SuppressWarnings("DuplicatedCode")
@UtilityClass
public class InventoryUtil {

    public static @NotNull HashMap<Integer, ItemStack> addItem(@NotNull Player player, ItemStack... toAdd) {
        HashMap<Integer, ItemStack> result = addItem(player.getInventory(), toAdd);
        player.updateInventory();
        return result;
    }

    public static @NotNull HashMap<Integer, ItemStack> addItem(@NotNull InventoryHolder holder, ItemStack... toAdds) {
        return addItem(holder.getInventory(), toAdds);
    }

    public static @NotNull HashMap<Integer, ItemStack> addItem(
        @NotNull Inventory inventory, ItemStack @NotNull ... toAdds) {

        final HashMap<Integer, ItemStack> leftovers = new HashMap<>();
        final ItemStack[] storage = inventory.getStorageContents();
        if (storage == null) {
            return inventory.addItem(toAdds);
        }

        int remainderIndex = 0;
        for (ItemStack incoming : toAdds) {
            if (incoming == null || incoming.getType() == Material.AIR || incoming.getAmount() <= 0) {
                remainderIndex++;
                continue;
            }

            // Fill every matching partial stack, not just the first one.
            for (int slot = 0; slot < storage.length && incoming.getAmount() > 0; slot++) {
                final ItemStack existing = storage[slot];
                if (existing == null
                    || existing.getType() == Material.AIR
                    || existing.getAmount() >= existing.getMaxStackSize()
                    || !StackUtils.itemsMatch(existing, incoming, true, false)) {
                    continue;
                }

                final int handled = Math.min(existing.getMaxStackSize() - existing.getAmount(), incoming.getAmount());
                existing.setAmount(existing.getAmount() + handled);
                incoming.setAmount(incoming.getAmount() - handled);
            }

            // Then split the remainder across empty storage slots.
            for (int slot = 0; slot < storage.length && incoming.getAmount() > 0; slot++) {
                final ItemStack existing = storage[slot];
                if (existing != null && existing.getType() != Material.AIR) {
                    continue;
                }

                final int handled = Math.min(incoming.getAmount(), incoming.getMaxStackSize());
                storage[slot] = incoming.asQuantity(handled);
                incoming.setAmount(incoming.getAmount() - handled);
            }

            if (incoming.getAmount() > 0) {
                leftovers.put(remainderIndex, incoming);
            }
            remainderIndex++;
        }

        inventory.setStorageContents(storage);
        return leftovers;
    }

    public static int firstSimilar(ItemStack @NotNull [] storage, ItemStack item) {
        return firstSimilar(storage, item, true);
    }

    public static int firstSimilar(ItemStack @NotNull [] storage, ItemStack item, boolean withoutAmount) {
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] != null
                && storage[i].getAmount() < storage[i].getMaxStackSize()
                && StackUtils.itemsMatch(storage[i], item, true, !withoutAmount)) {
                return i;
            }
        }
        return -1;
    }

    public static int firstEmpty(ItemStack @NotNull [] storage) {
        for (int i = 0; i < storage.length; i++) {
            if (storage[i] == null || storage[i].getType() == Material.AIR) {
                return i;
            }
        }
        return -1;
    }

    /** Gives an item and drops every remainder rather than losing it. */
    public static void give(@NotNull Player player, ItemStack stack) {
        if (stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0) {
            return;
        }

        final HashMap<Integer, ItemStack> remnants = addItem(player, stack);
        if (remnants.isEmpty()) {
            return;
        }

        final Runnable drop = () -> remnants.values().forEach(remainder -> {
            if (remainder != null && remainder.getType() != Material.AIR && remainder.getAmount() > 0) {
                player.getWorld().dropItemNaturally(player.getLocation(), remainder.clone());
                remainder.setAmount(0);
            }
        });

        if (Bukkit.isPrimaryThread()) {
            drop.run();
        } else {
            Bukkit.getScheduler().runTask(Networks.getInstance(), drop);
        }
    }
}

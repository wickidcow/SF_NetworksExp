package io.github.sefiraat.networks.utils;

import io.github.sefiraat.networks.network.NetworkRoot;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Main-thread-only helpers for moving inventory contents into a network.
 *
 * <p>Older Networks code passed live inventory stacks directly into the network and depended on the
 * destination mutating that same Java object. That is unsafe across newer Paper inventory internals and the
 * different Slimefun Legacy, United and Gugu menu implementations. These helpers insert a clone and explicitly
 * commit only the amount accepted by the network back to the source inventory.</p>
 */
public final class NetworkTransferUtils {

    private NetworkTransferUtils() {
    }

    /**
     * Moves as much as possible from a Slimefun menu slot.
     *
     * @return the number of items successfully committed to the network
     */
    public static int moveMenuSlotIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull BlockMenu sourceMenu,
        int slot) {
        return moveMenuSlotIntoNetwork(root, accessor, sourceMenu, slot, Integer.MAX_VALUE);
    }

    /**
     * Moves up to {@code limit} items from a Slimefun menu slot.
     *
     * @return the number of items successfully committed to the network
     */
    public static int moveMenuSlotIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull BlockMenu sourceMenu,
        int slot,
        int limit) {

        if (slot < 0 || slot >= sourceMenu.getSize()) {
            return 0;
        }

        final ItemStack source = sourceMenu.getItemInSlot(slot);
        final TransferResult result = offer(root, accessor, source, limit);
        if (result.moved() <= 0) {
            return 0;
        }

        sourceMenu.replaceExistingItem(slot, result.remainingStack());
        sourceMenu.markDirty();
        return result.moved();
    }

    /**
     * Moves as much as possible from a Bukkit inventory slot.
     *
     * @return the number of items successfully committed to the network
     */
    public static int moveInventorySlotIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Inventory sourceInventory,
        int slot) {
        return moveInventorySlotIntoNetwork(root, accessor, sourceInventory, slot, Integer.MAX_VALUE);
    }

    /**
     * Moves up to {@code limit} items from a Bukkit inventory slot.
     *
     * @return the number of items successfully committed to the network
     */
    public static int moveInventorySlotIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Inventory sourceInventory,
        int slot,
        int limit) {

        if (slot < 0 || slot >= sourceInventory.getSize()) {
            return 0;
        }

        final ItemStack source = sourceInventory.getItem(slot);
        final TransferResult result = offer(root, accessor, source, limit);
        if (result.moved() <= 0) {
            return 0;
        }

        sourceInventory.setItem(slot, result.remainingStack());
        return result.moved();
    }


    /**
     * Moves as much as possible from the item currently held on a player cursor.
     *
     * @return the number of items successfully committed to the network
     */
    public static int movePlayerCursorIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Player player) {
        return movePlayerCursorIntoNetwork(root, accessor, player, Integer.MAX_VALUE);
    }

    /**
     * Moves up to {@code limit} items from the item currently held on a player cursor.
     *
     * @return the number of items successfully committed to the network
     */
    public static int movePlayerCursorIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Player player,
        int limit) {

        final TransferResult result = offer(root, accessor, player.getItemOnCursor(), limit);
        if (result.moved() <= 0) {
            return 0;
        }

        player.setItemOnCursor(orAir(result.remainingStack()));
        return result.moved();
    }

    /**
     * Moves as much as possible from the player's selected hotbar slot.
     *
     * @return the number of items successfully committed to the network
     */
    public static int movePlayerMainHandIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Player player) {
        return movePlayerMainHandIntoNetwork(root, accessor, player, Integer.MAX_VALUE);
    }

    /**
     * Moves up to {@code limit} items from the player's selected hotbar slot.
     *
     * @return the number of items successfully committed to the network
     */
    public static int movePlayerMainHandIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Player player,
        int limit) {
        return moveInventorySlotIntoNetwork(
            root, accessor, player.getInventory(), player.getInventory().getHeldItemSlot(), limit);
    }

    /**
     * Offers a live stack reference through clone-and-commit semantics. This is retained for legacy
     * Slimefun menu callbacks that do not expose their source inventory slot. Callers that know the
     * slot should use one of the explicit menu, inventory, cursor or hand methods instead.
     *
     * @return the number of items successfully committed to the network
     */
    public static int moveStackReferenceIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        ItemStack source) {
        final TransferResult result = offer(root, accessor, source, Integer.MAX_VALUE);
        if (result.moved() <= 0 || source == null) {
            return 0;
        }

        final ItemStack remaining = result.remainingStack();
        source.setAmount(remaining == null ? 0 : remaining.getAmount());
        return result.moved();
    }

    private static @NotNull TransferResult offer(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        ItemStack source,
        int limit) {

        if (source == null || source.getType() == Material.AIR || source.getAmount() <= 0 || limit <= 0) {
            return TransferResult.NONE;
        }

        final int sourceAmount = source.getAmount();
        final int offeredAmount = Math.min(sourceAmount, limit);
        final ItemStack offered = source.clone();
        offered.setAmount(offeredAmount);
        root.addItemStack0(accessor, offered);

        final int offeredRemaining = Math.max(0, Math.min(offeredAmount, offered.getAmount()));
        final int moved = offeredAmount - offeredRemaining;
        if (moved <= 0) {
            return TransferResult.NONE;
        }

        final int sourceRemaining = sourceAmount - moved;
        if (sourceRemaining <= 0) {
            return new TransferResult(moved, null);
        }

        final ItemStack committed = source.clone();
        committed.setAmount(sourceRemaining);
        return new TransferResult(moved, committed);
    }

    private static @NotNull ItemStack orAir(ItemStack stack) {
        return stack == null ? new ItemStack(Material.AIR) : stack;
    }

    private record TransferResult(int moved, ItemStack remainingStack) {
        private static final TransferResult NONE = new TransferResult(0, null);
    }
}

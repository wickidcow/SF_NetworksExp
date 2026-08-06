package io.github.sefiraat.networks.utils;

import com.balugaq.netex.utils.BlockMenuUtil;
import com.balugaq.netex.utils.InventoryUtil;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Main-thread-only helpers for loss-safe item movement.
 *
 * <p>Every network withdrawal follows reserve, commit and rollback semantics. The network is asked only for
 * the amount the destination appears able to accept. If another plugin or menu implementation accepts less
 * than advertised, the remainder is returned to the source network. A world drop is used only as a final
 * loss-prevention fallback and is logged loudly.</p>
 */
public final class NetworkTransferUtils {

    private NetworkTransferUtils() {
    }

    /** Moves an item from a network into a Slimefun menu without silently losing a remainder. */
    public static int moveNetworkItemIntoMenu(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull BlockMenu destination,
        @NotNull ItemStack template,
        int limit,
        int @NotNull ... slots) {

        if (template.getType() == Material.AIR || limit <= 0 || slots.length == 0) {
            return 0;
        }

        final int requestAmount = Math.min(limit, getMenuInsertCapacity(destination, template, slots));
        final ItemStack retrieved = withdraw(root, accessor, template, requestAmount);
        if (retrieved == null) {
            return 0;
        }
        return commitNetworkWithdrawal(root, accessor, destination, retrieved, slots);
    }

    /** Commits an already-withdrawn stack into a Slimefun menu and rolls back every remainder. */
    public static int commitNetworkWithdrawal(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull BlockMenu destination,
        @NotNull ItemStack withdrawnStack,
        int @NotNull ... slots) {

        if (withdrawnStack.getType() == Material.AIR || withdrawnStack.getAmount() <= 0 || slots.length == 0) {
            return 0;
        }

        final int withdrawn = withdrawnStack.getAmount();
        final ItemStack remainder;
        try {
            remainder = BlockMenuUtil.pushItem(destination, withdrawnStack, slots);
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            rollbackNetworkWithdrawal(
                root, accessor, withdrawnStack, destination.getLocation(), "menu transfer exception");
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks menu transfer failed while committing; the withdrawn stack was rolled back.",
                exception);
            return 0;
        }

        final int remainderAmount = validAmount(remainder);
        final int committed = Math.max(0, withdrawn - remainderAmount);
        if (committed > 0) {
            destination.markDirty();
            TransferAudit.recordWithdrawalCommitted(committed);
        }

        if (remainderAmount > 0) {
            rollbackNetworkWithdrawal(root, accessor, remainder, destination.getLocation(), "menu transfer");
        }
        return committed;
    }

    /** Moves an item from a network into a player's storage inventory. */
    public static int moveNetworkItemIntoPlayerInventory(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Player player,
        @NotNull ItemStack template,
        int limit) {

        if (template.getType() == Material.AIR || limit <= 0) {
            return 0;
        }

        final int requestAmount = Math.min(limit, getInventoryInsertCapacity(player.getInventory(), template));
        final ItemStack retrieved = withdraw(root, accessor, template, requestAmount);
        if (retrieved == null) {
            return 0;
        }

        final int withdrawn = retrieved.getAmount();
        final Map<Integer, ItemStack> remainders;
        try {
            remainders = InventoryUtil.addItem(player, retrieved);
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            rollbackNetworkWithdrawal(
                root, accessor, retrieved, player.getLocation(), "player inventory transfer exception");
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks player-inventory transfer failed while committing; the withdrawn stack was rolled back.",
                exception);
            return 0;
        }

        int remainderAmount = 0;
        for (ItemStack remainder : remainders.values()) {
            remainderAmount += validAmount(remainder);
            if (validAmount(remainder) > 0) {
                rollbackNetworkWithdrawal(root, accessor, remainder, player.getLocation(), "player inventory transfer");
            }
        }
        final int committed = Math.max(0, withdrawn - remainderAmount);
        TransferAudit.recordWithdrawalCommitted(committed);
        return committed;
    }

    /**
     * Withdraws directly onto the player's cursor. This is used by both classic and new-style grids and
     * preserves the existing cursor stack rather than replacing it optimistically.
     */
    public static int moveNetworkItemOntoCursor(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Player player,
        @NotNull ItemStack template,
        int limit) {

        if (template.getType() == Material.AIR || limit <= 0) {
            return 0;
        }

        final ItemStack cursor = player.getItemOnCursor();
        final int capacity;
        if (cursor == null || cursor.getType() == Material.AIR) {
            capacity = template.getMaxStackSize();
        } else if (StackUtils.itemsMatch(cursor, template)) {
            capacity = Math.max(0, cursor.getMaxStackSize() - cursor.getAmount());
        } else {
            return 0;
        }

        final ItemStack retrieved = withdraw(root, accessor, template, Math.min(limit, capacity));
        if (retrieved == null) {
            return 0;
        }

        final int moved = retrieved.getAmount();
        try {
            if (cursor == null || cursor.getType() == Material.AIR) {
                player.setItemOnCursor(retrieved);
            } else {
                final ItemStack combined = cursor.clone();
                combined.setAmount(cursor.getAmount() + moved);
                player.setItemOnCursor(combined);
                retrieved.setAmount(0);
            }
            TransferAudit.recordWithdrawalCommitted(moved);
            return moved;
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            rollbackNetworkWithdrawal(root, accessor, retrieved, player.getLocation(), "cursor transfer exception");
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks cursor transfer failed while committing; the withdrawn stack was rolled back.",
                exception);
            return 0;
        }
    }

    /** Moves items between two independent network roots with rollback to the source root. */
    public static int moveNetworkItemBetweenRoots(
        @NotNull NetworkRoot source,
        @NotNull Location sourceAccessor,
        @NotNull NetworkRoot target,
        @NotNull Location targetAccessor,
        @NotNull ItemStack template,
        int limit) {

        if (source == target || template.getType() == Material.AIR || limit <= 0) {
            return 0;
        }

        final ItemStack retrieved = withdraw(source, sourceAccessor, template, limit);
        if (retrieved == null) {
            return 0;
        }

        final int withdrawn = retrieved.getAmount();
        // A rollback/forwarding operation must not be rejected merely because the target was accessed earlier
        // in the same tick by another transport node.
        try {
            target.uncontrolAccessInput(targetAccessor);
            target.addItemStack0(targetAccessor, retrieved);
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            rollbackNetworkWithdrawal(
                source, sourceAccessor, retrieved, sourceAccessor, "network-to-network transfer exception");
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks root-to-root transfer failed while committing; the withdrawn stack was rolled back.",
                exception);
            return 0;
        }
        final int remainder = validAmount(retrieved);
        final int committed = Math.max(0, withdrawn - remainder);
        TransferAudit.recordWithdrawalCommitted(committed);
        if (remainder > 0) {
            rollbackNetworkWithdrawal(source, sourceAccessor, retrieved, sourceAccessor, "network-to-network transfer");
        }
        return committed;
    }

    /**
     * Returns a previously withdrawn stack to its source network. Any final remainder is dropped in-world so
     * a full or externally modified network cannot silently destroy items.
     */
    public static void rollbackNetworkWithdrawal(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull ItemStack remainder,
        @NotNull Location fallbackLocation,
        @NotNull String operation) {

        if (remainder.getType() == Material.AIR || remainder.getAmount() <= 0) {
            return;
        }

        final int beforeRollback = remainder.getAmount();
        try {
            root.uncontrolAccessInput(accessor);
            root.addItemStack0(accessor, remainder);
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.SEVERE,
                "A Networks rollback failed while reinserting into its source network; using the safety-drop path.",
                exception);
        }
        final int afterRollback = validAmount(remainder);
        TransferAudit.recordRollback(beforeRollback, Math.max(0, beforeRollback - afterRollback));
        if (afterRollback <= 0) {
            return;
        }

        dropLossPreventionRemainder(remainder, fallbackLocation, operation, beforeRollback);
    }

    /** Returns the number of matching items a menu can accept in the supplied transport slots. */
    public static int getMenuInsertCapacity(
        @NotNull BlockMenu destination,
        @NotNull ItemStack template,
        int @NotNull ... slots) {

        if (template.getType() == Material.AIR || slots.length == 0) {
            return 0;
        }

        long capacity = 0;
        for (int slot : slots) {
            if (slot < 0 || slot >= destination.getSize()) {
                continue;
            }
            final ItemStack existing = destination.getItemInSlot(slot);
            if (existing == null || existing.getType() == Material.AIR) {
                capacity += template.getMaxStackSize();
            } else if (StackUtils.itemsMatch(template, existing)) {
                capacity += Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            }
            if (capacity >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) capacity;
    }

    /** Returns the number of matching items a Bukkit storage inventory can accept. */
    public static int getInventoryInsertCapacity(@NotNull Inventory inventory, @NotNull ItemStack template) {
        if (template.getType() == Material.AIR) {
            return 0;
        }

        final ItemStack[] storage = inventory.getStorageContents();
        if (storage == null) {
            return 0;
        }

        long capacity = 0;
        for (ItemStack existing : storage) {
            if (existing == null || existing.getType() == Material.AIR) {
                capacity += template.getMaxStackSize();
            } else if (StackUtils.itemsMatch(existing, template)) {
                capacity += Math.max(0, existing.getMaxStackSize() - existing.getAmount());
            }
            if (capacity >= Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
        }
        return (int) capacity;
    }

    /** Moves as much as possible from a Slimefun menu slot. */
    public static int moveMenuSlotIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull BlockMenu sourceMenu,
        int slot) {
        return moveMenuSlotIntoNetwork(root, accessor, sourceMenu, slot, Integer.MAX_VALUE);
    }

    /** Moves up to {@code limit} items from a Slimefun menu slot. */
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
        final ItemStack originalSource = source == null ? null : source.clone();
        final TransferResult result = offer(root, accessor, source, limit);
        if (result.moved() <= 0) {
            return 0;
        }

        try {
            sourceMenu.replaceExistingItem(slot, result.remainingStack());
            sourceMenu.markDirty();
            return result.moved();
        } catch (RuntimeException | LinkageError exception) {
            try {
                sourceMenu.replaceExistingItem(slot, originalSource);
                sourceMenu.markDirty();
            } catch (RuntimeException | LinkageError restorationFailure) {
                exception.addSuppressed(restorationFailure);
            }
            compensateCommittedDeposit(
                root, accessor, originalSource, result.moved(), sourceMenu.getLocation(), "menu source commit", exception);
            return 0;
        }
    }

    /** Moves as much as possible from a Bukkit inventory slot. */
    public static int moveInventorySlotIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Inventory sourceInventory,
        int slot) {
        return moveInventorySlotIntoNetwork(root, accessor, sourceInventory, slot, Integer.MAX_VALUE);
    }

    /** Moves up to {@code limit} items from a Bukkit inventory slot. */
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
        final ItemStack originalSource = source == null ? null : source.clone();
        final TransferResult result = offer(root, accessor, source, limit);
        if (result.moved() <= 0) {
            return 0;
        }

        try {
            sourceInventory.setItem(slot, result.remainingStack());
            return result.moved();
        } catch (RuntimeException | LinkageError exception) {
            try {
                sourceInventory.setItem(slot, originalSource);
            } catch (RuntimeException | LinkageError restorationFailure) {
                exception.addSuppressed(restorationFailure);
            }
            compensateCommittedDeposit(
                root, accessor, originalSource, result.moved(), accessor, "inventory source commit", exception);
            return 0;
        }
    }

    /** Moves as much as possible from the item currently held on a player cursor. */
    public static int movePlayerCursorIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Player player) {
        return movePlayerCursorIntoNetwork(root, accessor, player, Integer.MAX_VALUE);
    }

    /** Moves up to {@code limit} cursor items into the network. */
    public static int movePlayerCursorIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Player player,
        int limit) {

        final ItemStack cursorSource = player.getItemOnCursor();
        final ItemStack originalSource = cursorSource == null ? null : cursorSource.clone();
        final TransferResult result = offer(root, accessor, cursorSource, limit);
        if (result.moved() <= 0) {
            return 0;
        }

        try {
            player.setItemOnCursor(orAir(result.remainingStack()));
            return result.moved();
        } catch (RuntimeException | LinkageError exception) {
            try {
                player.setItemOnCursor(orAir(originalSource));
            } catch (RuntimeException | LinkageError restorationFailure) {
                exception.addSuppressed(restorationFailure);
            }
            compensateCommittedDeposit(
                root, accessor, originalSource, result.moved(), player.getLocation(),
                "cursor source commit", exception);
            return 0;
        }
    }

    /** Moves as much as possible from the player's selected hotbar slot. */
    public static int movePlayerMainHandIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Player player) {
        return movePlayerMainHandIntoNetwork(root, accessor, player, Integer.MAX_VALUE);
    }

    /** Moves up to {@code limit} items from the player's selected hotbar slot. */
    public static int movePlayerMainHandIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Player player,
        int limit) {
        return moveInventorySlotIntoNetwork(
            root, accessor, player.getInventory(), player.getInventory().getHeldItemSlot(), limit);
    }

    /**
     * Offers a live stack reference through clone-and-commit semantics. Callers that know the source slot
     * should prefer one of the explicit menu, inventory, cursor or hand methods.
     */
    public static int moveStackReferenceIntoNetwork(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        ItemStack source) {
        final ItemStack originalSource = source == null ? null : source.clone();
        final TransferResult result = offer(root, accessor, source, Integer.MAX_VALUE);
        if (result.moved() <= 0 || source == null) {
            return 0;
        }

        final ItemStack remaining = result.remainingStack();
        try {
            source.setAmount(remaining == null ? 0 : remaining.getAmount());
            return result.moved();
        } catch (RuntimeException | LinkageError exception) {
            try {
                source.setAmount(originalSource == null ? 0 : originalSource.getAmount());
            } catch (RuntimeException | LinkageError restorationFailure) {
                exception.addSuppressed(restorationFailure);
            }
            compensateCommittedDeposit(
                root, accessor, originalSource, result.moved(), accessor, "stack-reference source commit", exception);
            return 0;
        }
    }

    private static ItemStack withdraw(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull ItemStack template,
        int amount) {

        if (amount <= 0) {
            return null;
        }
        TransferAudit.recordWithdrawalAttempt(amount);
        final ItemStack requestTemplate = template.clone();
        requestTemplate.setAmount(1);
        try {
            final ItemStack retrieved = root.getItemStack0(accessor, new ItemRequest(requestTemplate, amount));
            final int withdrawn = validAmount(retrieved);
            TransferAudit.recordWithdrawn(withdrawn);
            return withdrawn <= 0 ? null : retrieved;
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks withdrawal failed before commit.",
                exception);
            return null;
        }
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
        TransferAudit.recordDepositAttempt(offeredAmount);
        try {
            root.addItemStack0(accessor, offered);
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks deposit failed before the source stack was committed.",
                exception);
            return TransferResult.NONE;
        }

        final int offeredRemaining = Math.max(0, Math.min(offeredAmount, offered.getAmount()));
        final int moved = offeredAmount - offeredRemaining;
        TransferAudit.recordDepositCommitted(moved);
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

    /**
     * Removes an item amount that was accepted by a network before the source-side commit failed.
     *
     * @return the number of equivalent items removed from the network
     */
    public static int compensateCommittedDeposit(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        ItemStack template,
        int committed,
        @NotNull Location fallbackLocation,
        @NotNull String operation,
        @NotNull Throwable commitFailure
    ) {
        TransferAudit.recordFailure();
        int recovered = 0;
        if (template != null && template.getType() != Material.AIR && committed > 0) {
            final ItemStack requestTemplate = template.clone();
            requestTemplate.setAmount(1);
            try {
                root.uncontrolAccessOutput(accessor);
                ItemStack compensation = root.getItemStack0(accessor, new ItemRequest(requestTemplate, committed));
                recovered = validAmount(compensation);
                if (compensation != null) {
                    compensation.setAmount(0);
                }
            } catch (RuntimeException | LinkageError compensationFailure) {
                commitFailure.addSuppressed(compensationFailure);
            }
        }
        TransferAudit.recordDepositCompensation(committed, recovered);
        java.util.logging.Level level = recovered >= committed
            ? java.util.logging.Level.WARNING
            : java.util.logging.Level.SEVERE;
        Networks.getInstance().getLogger().log(
            level,
            "A Networks " + operation + " failed after the network accepted " + committed + " item(s). "
                + recovered + " item(s) were removed from the network as compensation at " + fallbackLocation + '.',
            commitFailure);
        return recovered;
    }

    private static int validAmount(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR ? 0 : Math.max(0, stack.getAmount());
    }

    private static void dropLossPreventionRemainder(
        @NotNull ItemStack remainder,
        @NotNull Location fallbackLocation,
        @NotNull String operation,
        int beforeRollback) {

        Location dropLocation = fallbackLocation.clone().add(0.5, 0.5, 0.5);
        World world = dropLocation.getWorld();
        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
            dropLocation = world.getSpawnLocation().clone().add(0.5, 0.5, 0.5);
        }

        if (world != null) {
            final int dropped = remainder.getAmount();
            world.dropItemNaturally(dropLocation, remainder.clone());
            TransferAudit.recordSafetyDrop(dropped);
            remainder.setAmount(0);
            Networks.getInstance().getLogger().severe(
                "A Networks " + operation + " changed while committing. " + dropped + " of "
                    + beforeRollback + " rollback item(s) could not be reinserted at " + fallbackLocation
                    + " and were dropped at " + dropLocation + " to prevent silent loss.");
        } else {
            Networks.getInstance().getLogger().severe(
                "A Networks " + operation + " changed while committing. " + remainder.getAmount() + " of "
                    + beforeRollback + " rollback item(s) could not be reinserted, and no loaded world was "
                    + "available for a safety drop. The live remainder was deliberately left untouched.");
        }
    }

    private static @NotNull ItemStack orAir(ItemStack stack) {
        return stack == null ? new ItemStack(Material.AIR) : stack;
    }

    private record TransferResult(int moved, ItemStack remainingStack) {
        private static final TransferResult NONE = new TransferResult(0, null);
    }
}

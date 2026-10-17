package io.github.sefiraat.networks.utils;

import com.balugaq.netex.utils.BlockMenuUtil;
import com.balugaq.netex.utils.InventoryUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
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
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
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

    /**
     * Commits a network withdrawal into a Slimefun menu. The offered ItemStack is mutable and
     * BlockMenuUtil.pushItem() can write a slot before its setter throws. Never use the offered
     * remainder alone to decide how much to restore to the source network: that can duplicate
     * the items already installed in a destination slot.
     *
     * <p>Instead, snapshot the exact destination before writing and count only verified
     * additions after the write, including write-then-throw and silent setter rejection.
     * A changed/unreadable destination fails closed: do not blindly re-deposit items whose
     * destination state is unknown.</p>
     */
    public static int commitNetworkWithdrawal(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull BlockMenu destination,
        @NotNull ItemStack withdrawnStack,
        int @NotNull ... slots) {

        if (withdrawnStack.getType() == Material.AIR || withdrawnStack.getAmount() <= 0) {
            return 0;
        }

        final int withdrawn = withdrawnStack.getAmount();
        final ItemStack exactSource;
        final Map<Integer, ItemStack> before = new java.util.LinkedHashMap<>();

        try {
            exactSource = snapshotMenuSource(withdrawnStack);
            for (int slot : slots) {
                if (slot < 0 || slot >= destination.getSize() || before.containsKey(slot)) {
                    continue;
                }
                final ItemStack current = destination.getItemInSlot(slot);
                before.put(slot, validAmount(current) == 0 ? null : snapshotMenuSource(current));
            }
        } catch (RuntimeException | LinkageError snapshotFailure) {
            TransferAudit.recordFailure();
            rollbackNetworkWithdrawal(root, accessor, withdrawnStack,
                destination.getLocation(), "menu transfer preflight");
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks menu destination could not be snapshotted; no destination writes were attempted.",
                snapshotFailure);
            return 0;
        }

        if (before.isEmpty()) {
            rollbackNetworkWithdrawal(root, accessor, exactSource, destination.getLocation(), "menu transfer no slots");
            return 0;
        }

        Throwable writeFailure = null;
        int claimedRemainder = -1;
        try {
            final ItemStack remainder = BlockMenuUtil.pushItem(destination, withdrawnStack, slots);
            claimedRemainder = validAmount(remainder);
        } catch (RuntimeException | LinkageError exception) {
            writeFailure = exception;
        }

        final int committed;
        try {
            committed = countVerifiedMenuAddition(destination, before, exactSource, withdrawn);
        } catch (RuntimeException | LinkageError reconciliationFailure) {
            TransferAudit.recordFailure();
            if (writeFailure != null) {
                reconciliationFailure.addSuppressed(writeFailure);
            }
            /*
             * An asynchronous producer or a broken menu setter may have replaced one of the
             * destination slots. Restoring an unproven amount here can mint a second copy.
             * Keep the transfer fail-closed and surface a severe, actionable alert.
             */
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.SEVERE,
                "A Networks menu withdrawal cannot prove the destination state at "
                    + destination.getLocation() + ". The ambiguous withdrawal of " + withdrawn
                    + " item(s) was NOT deposited back into the network to avoid duplication.",
                reconciliationFailure);
            return 0;
        }

        if (writeFailure != null) {
            TransferAudit.recordFailure();
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks menu insertion threw after accepting " + committed + " of " + withdrawn
                    + " item(s); only the verified remainder will be rolled back.",
                writeFailure);
        } else if (claimedRemainder != withdrawn - committed) {
            TransferAudit.recordFailure();
            Networks.getInstance().getLogger().warning(
                "A Networks menu insertion reported a different remainder than its destination holds at "
                    + destination.getLocation() + "; restoring only the verified remainder.");
        }

        if (committed > 0) {
            try {
                destination.markDirty();
            } catch (RuntimeException | LinkageError dirtyFailure) {
                TransferAudit.recordFailure();
                Networks.getInstance().getLogger().log(
                    java.util.logging.Level.WARNING,
                    "A Networks destination could not be marked dirty after its "
                        + committed + " accepted item(s). The committed items will not be duplicated.",
                    dirtyFailure);
            }
            TransferAudit.recordWithdrawalCommitted(committed);
        }

        final int uncommitted = withdrawn - committed;
        if (uncommitted > 0) {
            exactSource.setAmount(uncommitted);
            rollbackNetworkWithdrawal(root, accessor, exactSource,
                destination.getLocation(), "menu transfer verified remainder");
        }

        return committed;
    }

    /**
     * Counts only items proven to have been added to the offered input slots.
     * Any externally replaced or reduced slot is ambiguous: it is unsafe to
     * credit it as our insertion or blindly return the whole source withdrawal.
     */
    private static int countVerifiedMenuAddition(
        @NotNull BlockMenu destination,
        @NotNull Map<Integer, ItemStack> before,
        @NotNull ItemStack offered,
        int withdrawn) {

        long added = 0;
        for (Map.Entry<Integer, ItemStack> entry : before.entrySet()) {
            final int slot = entry.getKey();
            final ItemStack previous = entry.getValue();
            final ItemStack current = destination.getItemInSlot(slot);
            final int priorAmount = validAmount(previous);
            final int currentAmount = validAmount(current);

            if (priorAmount == 0) {
                if (currentAmount > 0) {
                    if (!offered.isSimilar(current)) {
                        throw new IllegalStateException("A menu insertion replaced an empty slot with another item: "
                            + slot);
                    }
                    added += currentAmount;
                }
            } else {
                if (currentAmount == 0 || !previous.isSimilar(current) || currentAmount < priorAmount) {
                    throw new IllegalStateException("A menu input changed while a withdrawal was being committed: "
                        + slot);
                }
                if (currentAmount > priorAmount) {
                    // Slimefun transport can intentionally merge stacks using the configured
                    // StackUtils matching policy (for example, lore-insensitive recipes).
                    // The pre-existing stack must retain its *exact* identity across the write,
                    // but the offer need only match the same policy pushItem itself used.
                    if (!StackUtils.itemsMatch(offered, previous)) {
                        throw new IllegalStateException("An unrelated menu input increased during insertion: " + slot);
                    }
                    added += currentAmount - priorAmount;
                }
            }

            if (added > withdrawn) {
                throw new IllegalStateException("Destination gained more items than the network withdrew");
            }
        }
        return (int) added;
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
        return depositMenuSlot(root, accessor, sourceMenu, slot, limit, false).moved();
    }

    /**
     * Deposits a menu slot and intentionally discards its remainder only after a clean offer and
     * source check. A failed deposit still reconciles accepted items, but never authorizes voiding.
     * Callers must stop their menu pass when {@link MenuDepositResult#failed()} is true.
     */
    public static @NotNull MenuDepositResult moveMenuSlotIntoNetworkAndVoidRemainder(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull BlockMenu sourceMenu,
        int slot,
        int limit) {
        return depositMenuSlot(root, accessor, sourceMenu, slot, limit, true);
    }

    /** A failure is distinct from an ordinary zero-capacity result, even after partial acceptance. */
    public record MenuDepositResult(int moved, boolean failed) {
        private static final MenuDepositResult NONE = new MenuDepositResult(0, false);
        private static final MenuDepositResult FAILED = new MenuDepositResult(0, true);
    }

    private static @NotNull MenuDepositResult depositMenuSlot(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull BlockMenu sourceMenu,
        int slot,
        int limit,
        boolean voidRemainder) {

        if (slot < 0 || slot >= sourceMenu.getSize() || limit <= 0) {
            return MenuDepositResult.NONE;
        }

        final ItemStack source = sourceMenu.getItemInSlot(slot);
        if (source == null || source.getType().isAir() || source.getAmount() <= 0) {
            return MenuDepositResult.NONE;
        }
        final ItemStack originalSource;
        try {
            originalSource = snapshotMenuSource(source);
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks menu source could not be copied exactly at " + sourceMenu.getLocation()
                    + " (slot " + slot + "). No items were offered to the network.",
                exception);
            return MenuDepositResult.FAILED;
        }
        // Offer and derive the remainder from the verified snapshot, not from a live addon
        // subclass whose clone may rebuild its template or reapply configured model data.
        final TransferResult result = offer(root, accessor, originalSource, limit);
        if (result.moved() <= 0 && (!voidRemainder || result.failed())) {
            return result.failed() ? MenuDepositResult.FAILED : MenuDepositResult.NONE;
        }

        // Keep the clear inside the source-commit boundary. A second, unconditional caller-side
        // write could erase an external replacement after compensation or a failed source write.
        final ItemStack committedSource = voidRemainder && !result.failed() ? null : result.remainingStack();
        try {
            // The detached network offer can trigger callbacks before we commit this source slot.
            // Check even zero-acceptance offers before intentionally discarding their remainder.
            final ItemStack currentSource = sourceMenu.getItemInSlot(slot);
            if (!sourceStillMatches(originalSource, currentSource)) {
                final String sourceContext =
                    describeMenuSourceChange(sourceMenu.getLocation(), slot, originalSource, currentSource);
                compensateCommittedDeposit(root, accessor, originalSource, result.moved(),
                    sourceMenu.getLocation(), "changed menu source " + sourceContext,
                    new IllegalStateException(
                        "The menu input changed during the network deposit " + sourceContext));
                return MenuDepositResult.FAILED;
            }
            if (voidRemainder && !result.failed()) {
                sourceMenu.replaceExistingItem(slot, null);
            } else {
                sourceMenu.replaceExistingItem(slot, result.remainingStack());
            }
            sourceMenu.markDirty();
            return new MenuDepositResult(result.moved(), result.failed());
        } catch (RuntimeException | LinkageError exception) {
            try {
                ItemStack current = sourceMenu.getItemInSlot(slot);
                // A failed write may have already installed our remainder or VOID clear. Restore
                // only if the slot still contains our original or our partially committed state.
                if (sourceStillMatches(originalSource, current)
                    || sourceStillMatches(committedSource, current)) {
                    sourceMenu.replaceExistingItem(slot, originalSource);
                    sourceMenu.markDirty();
                }
            } catch (RuntimeException | LinkageError restorationFailure) {
                exception.addSuppressed(restorationFailure);
            }
            compensateCommittedDeposit(
                root, accessor, originalSource, result.moved(), sourceMenu.getLocation(), "menu source commit", exception);
            return MenuDepositResult.FAILED;
        }
    }

    /**
     * Keeps the normal native clone fast path, but verifies it before any network mutation.
     * SlimefunItemStack implementations may reconstruct a template in clone(), reapplying
     * model mappings and making an unchanged source fail the post-deposit identity check.
     *
     * <p>The fallback copies existing metadata without invoking that subclass constructor.
     * It is accepted only when the strict full-stack comparison succeeds. A component that
     * cannot survive this copy causes a pre-deposit failure, never silent normalization.
     * The ItemStack copy constructor is unsuitable: on Paper it invokes source.clone().</p>
     */
    private static @NotNull ItemStack snapshotMenuSource(@NotNull ItemStack source) {
        final ItemStack cloned = source.clone();
        if (cloned != source && sourceStillMatches(cloned, source)) {
            return cloned;
        }

        final ItemStack snapshot = ItemStack.of(source.getType(), source.getAmount());
        if (source.hasItemMeta() && !snapshot.setItemMeta(source.getItemMeta())) {
            throw new IllegalStateException("The menu source metadata could not be copied exactly");
        }
        if (!sourceStillMatches(snapshot, source)) {
            throw new IllegalStateException("The menu source copy changed its item data or amount");
        }
        return snapshot;
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
            if (!sourceStillMatches(originalSource, sourceInventory.getItem(slot))) {
                compensateCommittedDeposit(root, accessor, originalSource, result.moved(), accessor,
                    "changed inventory source",
                    new IllegalStateException("The inventory input changed during the network deposit"));
                return 0;
            }
            sourceInventory.setItem(slot, result.remainingStack());
            return result.moved();
        } catch (RuntimeException | LinkageError exception) {
            try {
                ItemStack current = sourceInventory.getItem(slot);
                if (sourceStillMatches(originalSource, current)
                    || sourceStillMatches(result.remainingStack(), current)) {
                    sourceInventory.setItem(slot, originalSource);
                }
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
            if (!sourceStillMatches(originalSource, player.getItemOnCursor())) {
                compensateCommittedDeposit(root, accessor, originalSource, result.moved(), player.getLocation(),
                    "changed cursor source",
                    new IllegalStateException("The player cursor changed during the network deposit"));
                return 0;
            }
            player.setItemOnCursor(orAir(result.remainingStack()));
            return result.moved();
        } catch (RuntimeException | LinkageError exception) {
            try {
                ItemStack current = player.getItemOnCursor();
                if (sourceStillMatches(originalSource, current)
                    || sourceStillMatches(result.remainingStack(), current)) {
                    player.setItemOnCursor(orAir(originalSource));
                }
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
            if (!sourceStillMatches(originalSource, source)) {
                compensateCommittedDeposit(root, accessor, originalSource, result.moved(), accessor,
                    "changed stack-reference source",
                    new IllegalStateException("The source stack changed during the network deposit"));
                return 0;
            }
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

    /**
     * Offers a live stack reference through clone-and-commit semantics. Callers that know the source slot
     * should prefer one of the explicit menu, inventory, cursor or hand methods.
     */
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
        boolean failed = false;
        try {
            root.addItemStack0(accessor, offered);
        } catch (RuntimeException | LinkageError exception) {
            failed = true;
            TransferAudit.recordFailure();
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks deposit failed; reconciling the source stack with any items already accepted.",
                exception);
            // A destination can accept items before a later destination (or bookkeeping) fails.
            // The mutated offer is the remaining amount, so reconcile accepted items on failure too.
        }

        final int offeredRemaining = Math.max(0, Math.min(offeredAmount, offered.getAmount()));
        final int moved = offeredAmount - offeredRemaining;
        TransferAudit.recordDepositCommitted(moved);
        if (moved <= 0) {
            return failed ? TransferResult.FAILED : TransferResult.NONE;
        }

        final int sourceRemaining = sourceAmount - moved;
        if (sourceRemaining <= 0) {
            return new TransferResult(moved, null, failed);
        }

        final ItemStack committed = source.clone();
        committed.setAmount(sourceRemaining);
        return new TransferResult(moved, committed, failed);
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

    /**
     * Compares source identity and quantity at the network commit boundary. This check is
     * intentionally stricter than recipe/item-routing equivalence: the source may have been
     * replaced with another player's item or updated by an addon while the offer was running.
     * It also treats null, AIR and zero-count stacks as the same empty state for failed-write recovery.
     */
    private static boolean sourceStillMatches(ItemStack expected, ItemStack current) {
        if (expected == null || expected.getType().isAir() || expected.getAmount() <= 0) {
            return current == null || current.getType().isAir() || current.getAmount() <= 0;
        }
        return current != null
            && current.getAmount() == expected.getAmount()
            && expected.isSimilar(current);
    }

    static @NotNull String describeMenuSourceChange(
        @NotNull Location location,
        int slot,
        @NotNull ItemStack before,
        ItemStack after) {

        /*
         * This method runs only after the strict source guard has already detected a conflict.
         * Diagnostics are never allowed to interfere with the compensation path, including when a
         * third-party ItemStack subclass throws from ordinary identity/metadata accessors.
         */
        try {
            String machineId = "unknown";
            try {
                final SlimefunItem machine = StorageCacheUtils.getSlimefunItem(location);
                if (machine != null) {
                    machineId = machine.getId();
                }
            } catch (RuntimeException | LinkageError ignored) {
                // Keep the generic machine id.
            }

            return "[machine=" + machineId
                + ", slot=" + slot
                + ", change=" + describeStackChange(before, after)
                + ", before=" + describeStack(before)
                + ", after=" + describeStack(after)
                + ']';
        } catch (RuntimeException | LinkageError diagnosticFailure) {
            return "[machine=unknown, slot=" + slot + ", change=diagnostic-error]";
        }
    }

    private static @NotNull String describeStackChange(@NotNull ItemStack before, ItemStack after) {
        if (after == null || after.getType().isAir() || after.getAmount() <= 0) {
            return "removed";
        }

        String change = "";
        if (before.getType() != after.getType()) {
            change = "type";
        }
        if (before.getAmount() != after.getAmount()) {
            change += change.isEmpty() ? "amount" : "+amount";
        }
        if (before.getType() == after.getType() && !before.isSimilar(after)) {
            change += change.isEmpty() ? "metadata" : "+metadata";
        }
        return change.isEmpty() ? "unknown" : change;
    }

    private static @NotNull String describeStack(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return "empty";
        }

        String identity = "material:" + stack.getType();
        try {
            final SlimefunItem item = SlimefunItem.getByItem(stack);
            if (item != null) {
                identity = "sf:" + item.getId();
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Keep the material fallback if an optional addon cannot resolve the stack.
        }
        return identity + 'x' + stack.getAmount();
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

    private record TransferResult(int moved, ItemStack remainingStack, boolean failed) {
        private static final TransferResult NONE = new TransferResult(0, null, false);
        private static final TransferResult FAILED = new TransferResult(0, null, true);
    }
}

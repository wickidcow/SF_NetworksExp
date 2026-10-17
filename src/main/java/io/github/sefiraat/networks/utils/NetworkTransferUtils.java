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

import java.util.List;
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

    /** Clears safety holds only as part of controlled plugin lifecycle cleanup. */
    public static void resetRuntimeSafety() {
        MenuTransferSafetyHold.reset();
        RootRollbackSafetyHold.reset();
    }

    /** Direct root APIs must observe the same controller quarantine as helper withdrawals. */
    public static boolean isNetworkRootHeld(
        @NotNull NetworkRoot root, @NotNull Location accessor) {
        return RootRollbackSafetyHold.isHeld(root, accessor);
    }

    /**
     * Storage adapters can write items before throwing without providing a receipt.
     * Block all further network access after an unverified write or withdrawal.
     * A controller rebuild is not proof that storage has been reconciled.
     */
    public static void quarantineUnverifiedNetworkRoot(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Material material,
        int possiblyAffected,
        @NotNull Throwable cause) {
        RootRollbackSafetyHold.hold(root, accessor, material, possiblyAffected);
        TransferAudit.recordFailure();
        Networks.getInstance().getLogger().log(
            java.util.logging.Level.SEVERE,
            "Networks quarantined a controller after an unverified storage operation at "
                + accessor + ". Up to " + possiblyAffected + " " + material
                + " item(s) may require reconciliation. No automatic refund or world drop"
                + " is authorized by this exception. Save logs and inspect storage before restarting.",
            cause);
    }

    /** Source-network rollback holds are read-only until a controlled restart. */
    public static int getHeldWithdrawalSourceCount() {
        return RootRollbackSafetyHold.count();
    }

    /** Coarse root-accessor details intentionally omit players, lore and PDC data. */
    public static @NotNull List<String> getHeldWithdrawalSourceSummaries(int limit) {
        return RootRollbackSafetyHold.describe(limit);
    }

    /** Read-only visibility for Networks Doctor. Holds must not be cleared by auto-repair. */
    public static int getHeldMenuDestinationCount() {
        return MenuTransferSafetyHold.count();
    }

    /** Details are intentionally coarse: no player data, lore, or PDC values. */
    public static @NotNull List<String> getHeldMenuDestinationSummaries(int limit) {
        return MenuTransferSafetyHold.describe(limit);
    }

    /** Moves an item from a network into a Slimefun menu without silently losing a remainder. */
    public static int moveNetworkItemIntoMenu(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull BlockMenu destination,
        @NotNull ItemStack template,
        int limit,
        int @NotNull ... slots) {

        if (template.getType() == Material.AIR || limit <= 0 || slots.length == 0
            || MenuTransferSafetyHold.isHeld(destination)) {
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
        // A second withdrawal may already be in flight when an earlier one trips
        // the hold. Return it to the network without touching an unsafe menu.
        if (MenuTransferSafetyHold.isHeld(destination)) {
            rollbackNetworkWithdrawal(root, accessor, withdrawnStack,
                destination.getLocation(), "menu transfer safety hold");
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
            MenuTransferSafetyHold.hold(destination, exactSource.getType(), withdrawn);
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.SEVERE,
                "A Networks menu withdrawal cannot prove the destination state at "
                    + destination.getLocation() + ". The ambiguous withdrawal of " + withdrawn
                    + " item(s) was NOT deposited back into the network to avoid duplication. "
                    + "Further transfers into this menu are suspended until its menu is replaced "
                    + "or Networks is restarted; inspect the machine and the addon that owns it.",
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
                // The in-memory insertion was verified, but a failed dirty mark
                // may prevent persistence. Do not expose this destination to
                // further withdrawals until its state is inspected.
                MenuTransferSafetyHold.hold(destination, exactSource.getType(), committed);
                Networks.getInstance().getLogger().log(
                    java.util.logging.Level.SEVERE,
                    "A Networks destination could not be marked dirty after its "
                        + committed + " accepted item(s) at " + destination.getLocation()
                        + ". The accepted items were not refunded (to prevent duplication),"
                        + " and further transfers into this menu have been suspended.",
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

    /**
     * Moves items from network storage into a player's storage inventory, accounting only for
     * verifiable destination additions. The existing InventoryUtil keeps the same partial-stack
     * and empty-slot insertion order; its mutable offer/remainder is not trusted as proof.
     *
     * <p>Some Inventory implementations write contents and then throw, or mutate an incoming
     * ItemStack before failing. Refunding that mutable offer directly could duplicate items
     * that already reached a player's inventory. Conversely, pre-commit array mutations
     * followed by a rejected setter could silently destroy withdrawn items.</p>
     */
    public static int moveNetworkItemIntoPlayerInventory(
        @NotNull NetworkRoot root,
        @NotNull Location accessor,
        @NotNull Player player,
        @NotNull ItemStack template,
        int limit) {

        if (template.getType() == Material.AIR || limit <= 0) {
            return 0;
        }

        final Inventory inventory = player.getInventory();
        final int requestAmount = Math.min(limit, getInventoryInsertCapacity(inventory, template));
        if (requestAmount <= 0) {
            return 0;
        }

        // Snapshot BEFORE withdrawing: storage callbacks can modify the player's inventory
        // during the network operation. If that happens, refuse a stale destination commit.
        final ItemStack[] before;
        try {
            before = snapshotInventoryStorage(inventory);
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks player inventory could not be snapshotted; no items were withdrawn.",
                exception);
            return 0;
        }

        final ItemStack retrieved = withdraw(root, accessor, template, requestAmount);
        if (retrieved == null) {
            return 0;
        }

        final ItemStack sourceSnapshot;
        final ItemStack offered;
        final int withdrawn = validAmount(retrieved);
        try {
            if (withdrawn > requestAmount) {
                throw new IllegalStateException("A network withdrew more than the requested inventory capacity");
            }
            sourceSnapshot = snapshotMenuSource(retrieved);
            if (!StackUtils.itemsMatch(template, sourceSnapshot, true, false)) {
                throw new IllegalStateException("The network returned an unexpected inventory item identity");
            }
            offered = snapshotMenuSource(sourceSnapshot);
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            rollbackNetworkWithdrawal(root, accessor, retrieved, player.getLocation(),
                "player inventory withdrawal preparation");
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks player-inventory withdrawal could not be prepared without changing the item.",
                exception);
            return 0;
        }

        try {
            final ItemStack[] current = inventory.getStorageContents();
            if (!sameInventoryStorage(before, current)) {
                TransferAudit.recordFailure();
                if (mayContainWithrawnItemIncrease(before, current, sourceSnapshot)) {
                    // A network withdrawal callback may itself have inserted the same item.
                    // Refunding all withdrawn items without checking that possibility can mint
                    // an identical second copy before we ever call InventoryUtil.addItem.
                    Networks.getInstance().getLogger().severe(
                        "A Networks player inventory gained a potentially withdrawn item before "
                            + "its normal insertion at " + player.getLocation()
                            + ". The ambiguous withdrawal was NOT refunded; inspect this inventory.");
                } else {
                    rollbackNetworkWithdrawal(root, accessor, sourceSnapshot, player.getLocation(),
                        "player inventory changed before commit");
                    Networks.getInstance().getLogger().warning(
                        "A Networks player inventory changed during withdrawal without gaining "
                            + "the requested item; the uncommitted stack was returned.");
                }
                return 0;
            }
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            // We cannot prove whether a callback installed the requested item
            // when even its pre-commit destination readback fails.
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.SEVERE,
                "A Networks player inventory became unreadable after withdrawal at "
                    + player.getLocation() + ". No potentially duplicated item was refunded.",
                exception);
            return 0;
        }

        Throwable insertionFailure = null;
        try {
            InventoryUtil.addItem(player, offered);
        } catch (RuntimeException | LinkageError exception) {
            insertionFailure = exception;
        }

        final int committed;
        try {
            committed = countVerifiedInventoryAddition(
                before, inventory.getStorageContents(), sourceSnapshot, withdrawn);
        } catch (RuntimeException | LinkageError verificationFailure) {
            TransferAudit.recordFailure();
            if (insertionFailure != null) {
                verificationFailure.addSuppressed(insertionFailure);
            }
            // Destination state is ambiguous. Refunding all withdrawn items here might
            // duplicate a stack already accepted by a third-party inventory writer.
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.SEVERE,
                "A Networks player-inventory withdrawal of " + withdrawn + " item(s) could not be "
                    + "reconciled at " + player.getLocation() + ". No ambiguous amount was "
                    + "refunded. Inspect this inventory before manually recovering items.",
                verificationFailure);
            return 0;
        }

        if (insertionFailure != null) {
            TransferAudit.recordFailure();
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks player inventory insertion failed after accepting " + committed
                    + " of " + withdrawn + " item(s); returning only the verified remainder.",
                insertionFailure);
        }

        if (committed > 0) {
            TransferAudit.recordWithdrawalCommitted(committed);
        }
        final int uncommitted = withdrawn - committed;
        if (uncommitted > 0) {
            sourceSnapshot.setAmount(uncommitted);
            rollbackNetworkWithdrawal(root, accessor, sourceSnapshot, player.getLocation(),
                "player inventory observed remainder");
        }
        return committed;
    }

    /**
     * Copy each storage slot exactly without retaining live Bukkit ItemStack references.
     * This also works around SlimefunItemStack subclasses whose clone() rebuilds metadata.
     */
    private static @NotNull ItemStack[] snapshotInventoryStorage(@NotNull Inventory inventory) {
        final ItemStack[] live = inventory.getStorageContents();
        if (live == null) {
            throw new IllegalStateException("Storage inventory contents were unavailable");
        }
        final ItemStack[] snapshot = new ItemStack[live.length];
        for (int i = 0; i < live.length; i++) {
            if (validAmount(live[i]) > 0) {
                snapshot[i] = snapshotMenuSource(live[i]);
            }
        }
        return snapshot;
    }

    /** A withdrawal callback must not replace or change existing player stacks before insertion. */
    private static boolean sameInventoryStorage(
        ItemStack @NotNull [] before,
        ItemStack[] current) {
        if (current == null || current.length != before.length) {
            return false;
        }
        for (int i = 0; i < before.length; i++) {
            if (!sourceStillMatches(before[i], current[i])) {
                return false;
            }
        }
        return true;
    }

    /**
     * Detect a source-identical gain before our own inventory write. A missing/reshaped
     * inventory is also ambiguous, so no optimistic refund is authorized.
     */
    private static boolean mayContainWithrawnItemIncrease(
        ItemStack @NotNull [] before,
        ItemStack[] current,
        @NotNull ItemStack withdrawn) {
        if (current == null || current.length != before.length) {
            return true;
        }
        for (int slot = 0; slot < before.length; slot++) {
            final ItemStack after = current[slot];
            if (validAmount(after) <= 0) {
                continue;
            }
            if (StackUtils.itemsMatch(withdrawn, after, true, false)
                && (validAmount(after) > validAmount(before[slot])
                    || (validAmount(before[slot]) > 0 && !before[slot].isSimilar(after)))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Count real added items, not mutable offer remainder. A replaced/reduced/normalized
     * unrelated slot is ambiguous and must not authorize an optimistic network refund.
     */
    private static int countVerifiedInventoryAddition(
        ItemStack @NotNull [] before,
        ItemStack[] current,
        @NotNull ItemStack offered,
        int withdrawn) {
        if (current == null || current.length != before.length) {
            throw new IllegalStateException("The player's inventory layout changed during transfer");
        }

        long inserted = 0L;
        for (int slot = 0; slot < before.length; slot++) {
            final ItemStack original = before[slot];
            final ItemStack observed = current[slot];
            final int originalAmount = validAmount(original);
            final int observedAmount = validAmount(observed);

            if (originalAmount == 0) {
                if (observedAmount > 0) {
                    if (!offered.isSimilar(observed)) {
                        throw new IllegalStateException(
                            "An empty inventory slot received another item during transfer: " + slot);
                    }
                    inserted += observedAmount;
                }
            } else {
                if (observedAmount == 0
                    || observedAmount < originalAmount
                    || !original.isSimilar(observed)) {
                    throw new IllegalStateException("An existing inventory slot changed identity: " + slot);
                }
                if (observedAmount > originalAmount) {
                    // InventoryUtil.addItem uses this exact matching policy for partial stacks.
                    if (!StackUtils.itemsMatch(original, offered, true, false)) {
                        throw new IllegalStateException(
                            "A different item was added to an unrelated inventory slot: " + slot);
                    }
                    inserted += observedAmount - originalAmount;
                }
            }

            if (inserted > withdrawn) {
                throw new IllegalStateException("Player inventory gained more than the network withdrew");
            }
        }
        return (int) inserted;
    }

    /**
     * Withdraws onto a player's cursor with observed-destination accounting.
     *
     * <p>A Bukkit cursor setter may apply an item and then throw. The old exception
     * handler refunded the mutable withdrawal without checking the cursor, which
     * could mint an additional network copy. Keep a detached withdrawal snapshot,
     * write a separate cursor stack, and reconcile only additions observed after
     * the setter returns or fails.</p>
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

        final ItemStack originalCursor;
        final int capacity;
        try {
            final ItemStack current = player.getItemOnCursor();
            if (validAmount(current) == 0) {
                originalCursor = null;
                capacity = template.getMaxStackSize();
            } else {
                if (!StackUtils.itemsMatch(current, template)) {
                    return 0;
                }
                originalCursor = snapshotMenuSource(current);
                capacity = Math.max(0, current.getMaxStackSize() - current.getAmount());
            }
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks cursor could not be read before withdrawal. No items were withdrawn.",
                exception);
            return 0;
        }

        if (capacity <= 0) {
            return 0;
        }
        final ItemStack retrieved = withdraw(root, accessor, template, Math.min(limit, capacity));
        if (retrieved == null) {
            return 0;
        }

        final int withdrawn = validAmount(retrieved);
        final ItemStack frozenWithdrawal;
        final ItemStack desiredCursor;
        final ItemStack toWrite;
        try {
            if (withdrawn > capacity) {
                throw new IllegalStateException("A network returned more items than the cursor can hold");
            }
            frozenWithdrawal = snapshotMenuSource(retrieved);
            if (originalCursor == null) {
                desiredCursor = snapshotMenuSource(frozenWithdrawal);
            } else {
                if (!StackUtils.itemsMatch(originalCursor, frozenWithdrawal)) {
                    throw new IllegalStateException("A network returned an incompatible cursor item");
                }
                desiredCursor = snapshotMenuSource(originalCursor);
                desiredCursor.setAmount(originalCursor.getAmount() + withdrawn);
            }
            toWrite = snapshotMenuSource(desiredCursor);
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            rollbackNetworkWithdrawal(root, accessor, retrieved, player.getLocation(),
                "cursor withdrawal preparation");
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks cursor withdrawal could not be prepared exactly. The item was returned to storage.",
                exception);
            return 0;
        }

        // Root callbacks may interact with the player inventory before source withdrawal finishes.
        // Never overwrite another plugin's cursor replacement with our stale snapshot.
        try {
            if (!sourceStillMatches(originalCursor, player.getItemOnCursor())) {
                TransferAudit.recordFailure();
                rollbackNetworkWithdrawal(root, accessor, frozenWithdrawal, player.getLocation(),
                    "cursor changed before destination write");
                Networks.getInstance().getLogger().warning(
                    "A Networks cursor changed during withdrawal; the uncommitted items were returned.");
                return 0;
            }
        } catch (RuntimeException | LinkageError exception) {
            TransferAudit.recordFailure();
            rollbackNetworkWithdrawal(root, accessor, frozenWithdrawal, player.getLocation(),
                "cursor pre-commit read failure");
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks cursor changed or became unreadable before its write; items were returned.",
                exception);
            return 0;
        }

        Throwable writeFailure = null;
        try {
            player.setItemOnCursor(toWrite);
        } catch (RuntimeException | LinkageError exception) {
            writeFailure = exception;
        }

        final int committed;
        try {
            final ItemStack actual = player.getItemOnCursor();
            final int afterCount = validAmount(actual);
            final int beforeCount = validAmount(originalCursor);
            if (sourceStillMatches(desiredCursor, actual)) {
                committed = withdrawn;
            } else if (sourceStillMatches(originalCursor, actual)) {
                committed = 0;
            } else if (actual != null
                && afterCount >= beforeCount
                && afterCount <= beforeCount + withdrawn
                && (originalCursor == null
                    ? frozenWithdrawal.isSimilar(actual)
                    : originalCursor.isSimilar(actual))) {
                committed = afterCount - beforeCount;
            } else {
                throw new IllegalStateException("Cursor identity or amount changed during a network withdrawal");
            }
        } catch (RuntimeException | LinkageError verificationFailure) {
            TransferAudit.recordFailure();
            if (writeFailure != null) {
                verificationFailure.addSuppressed(writeFailure);
            }
            // This is deliberately fail-closed: the setter might have installed
            // the item, so returning an unverified withdrawal could duplicate it.
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.SEVERE,
                "A Networks cursor withdrawal of " + withdrawn + " item(s) could not be reconciled for "
                    + player.getUniqueId() + ". No ambiguous amount was refunded to storage. "
                    + "Inspect the player's cursor and inventory before attempting recovery.",
                verificationFailure);
            return 0;
        }

        if (writeFailure != null) {
            TransferAudit.recordFailure();
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks cursor setter threw after accepting " + committed + " of " + withdrawn
                    + " item(s); only the verified remainder will be returned.",
                writeFailure);
        }

        if (committed > 0) {
            TransferAudit.recordWithdrawalCommitted(committed);
        }
        final int remainder = withdrawn - committed;
        if (remainder > 0) {
            frozenWithdrawal.setAmount(remainder);
            rollbackNetworkWithdrawal(root, accessor, frozenWithdrawal, player.getLocation(),
                "cursor observed remainder");
        }
        return committed;
    }

    /**
     * Moves items between independent network roots. A target storage adapter can install
     * some/all items and then throw before decrementing the offered stack. That exception
     * is NOT evidence the destination accepted nothing: never refund an unverified offer.
     */
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

        final int withdrawn = validAmount(retrieved);
        final ItemStack exactSource;
        final ItemStack offered;
        try {
            exactSource = snapshotMenuSource(retrieved);
            offered = snapshotMenuSource(exactSource);
        } catch (RuntimeException | LinkageError copyFailure) {
            // No destination write has occurred; a source refund is unambiguous.
            TransferAudit.recordFailure();
            rollbackNetworkWithdrawal(source, sourceAccessor, retrieved,
                sourceAccessor, "network-to-network offer preflight");
            Networks.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                "A Networks root-to-root offer could not be copied exactly. No target write was attempted.",
                copyFailure);
            return 0;
        }

        try {
            // The target accessor may be rate-limited by another transport in the same tick.
            // This access reset occurs BEFORE the target storage write, so failure here
            // cannot mean that this transaction was accepted by target storage.
            target.uncontrolAccessInput(targetAccessor);
        } catch (RuntimeException | LinkageError accessFailure) {
            TransferAudit.recordFailure();
            rollbackNetworkWithdrawal(source, sourceAccessor, exactSource, sourceAccessor,
                "network-to-network target access preflight");
            Networks.getInstance().getLogger().log(java.util.logging.Level.WARNING,
                "A Networks target accessor could not be reset. No target item insertion was attempted.",
                accessFailure);
            return 0;
        }

        try {
            target.addItemStack0(targetAccessor, offered);
        } catch (RuntimeException | LinkageError writeFailure) {
            // The target could have already accepted the entire offer even if 'offered'
            // still reports the original count. An automatic source refund can duplicate it.
            TransferAudit.recordFailure();
            RootRollbackSafetyHold.hold(source, sourceAccessor, exactSource.getType(), withdrawn);
            Networks.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                "A Networks root-to-root destination write at " + targetAccessor
                    + " threw after withdrawing up to " + withdrawn + " item(s) at " + sourceAccessor
                    + ". Destination acceptance is UNKNOWN. NO item was refunded or world-dropped."
                    + " All source-controller withdrawals are held until this incident is investigated.",
                writeFailure);
            return 0;
        }

        final int remaining;
        try {
            remaining = validAmount(offered);
            if (remaining > withdrawn
                || (remaining > 0 && !exactSource.isSimilar(offered))) {
                throw new IllegalStateException("Target altered the returned item identity or inflated the remainder");
            }
        } catch (RuntimeException | LinkageError readbackFailure) {
            TransferAudit.recordFailure();
            RootRollbackSafetyHold.hold(source, sourceAccessor, exactSource.getType(), withdrawn);
            Networks.getInstance().getLogger().log(java.util.logging.Level.SEVERE,
                "A Networks root-to-root destination returned an unverified remainder at "
                    + targetAccessor + ". NO unverified amount was refunded or dropped."
                    + " All source-controller withdrawals are held until investigation.",
                readbackFailure);
            return 0;
        }

        final int committed = withdrawn - remaining;
        TransferAudit.recordWithdrawalCommitted(committed);
        if (remaining > 0) {
            // The target completed normally and provided an identity-preserving remainder.
            // Refund from the detached original item, not from its mutable target offer.
            exactSource.setAmount(remaining);
            rollbackNetworkWithdrawal(source, sourceAccessor, exactSource,
                sourceAccessor, "network-to-network verified remainder");
        }
        return committed;
    }

    /**
     * Returns a withdrawn stack to its source network. Only a normally completed, identity-preserving
     * network insertion can authorize a world drop of the unaccepted remainder.
     *
     * <p>A storage adapter may accept an item and then throw without decrementing the offered
     * ItemStack. The previous catch clause proceeded to the safety-drop path, minting another
     * physical copy of an item that might already be in network storage. Such failures are
     * fundamentally ambiguous without a storage-level receipt: fail closed, preserve the live
     * offer for diagnostics and record an outstanding rollback instead of dropping it.</p>
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
        // An earlier ambiguous refund may have suspended this root/accessor.
        // No network insertion is attempted here, so this *new* remainder can
        // still use the known-unaccepted world-drop fallback without duplication.
        if (RootRollbackSafetyHold.isHeld(root, accessor)) {
            TransferAudit.recordRollback(beforeRollback, 0);
            dropLossPreventionRemainder(remainder, fallbackLocation,
                operation + " (source withdrawal hold)", beforeRollback);
            return;
        }

        final ItemStack beforeIdentity;
        try {
            beforeIdentity = snapshotMenuSource(remainder);
        } catch (RuntimeException | LinkageError snapshotFailure) {
            TransferAudit.recordFailure();
            // No destination write was attempted. Preserve existing safe-drop fallback
            // for a source item that cannot be copied, rather than silently discarding it.
            TransferAudit.recordRollback(beforeRollback, 0);
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.SEVERE,
                "A Networks " + operation + " rollback could not snapshot its source item before"
                    + " network insertion at " + fallbackLocation + ". No network write was attempted.",
                snapshotFailure);
            dropLossPreventionRemainder(remainder, fallbackLocation, operation, beforeRollback);
            return;
        }

        try {
            root.uncontrolAccessInput(accessor);
        } catch (RuntimeException | LinkageError accessFailure) {
            TransferAudit.recordFailure();
            // Access-reset failed before the storage insertion was invoked, so no
            // ambiguous write could have happened in this rollback attempt.
            TransferAudit.recordRollback(beforeRollback, 0);
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.SEVERE,
                "A Networks " + operation + " rollback could not reset its network access at "
                    + fallbackLocation + ". No network item insertion was attempted.",
                accessFailure);
            dropLossPreventionRemainder(remainder, fallbackLocation, operation, beforeRollback);
            return;
        }

        try {
            root.addItemStack0(accessor, remainder);
        } catch (RuntimeException | LinkageError storageFailure) {
            TransferAudit.recordFailure();
            RootRollbackSafetyHold.hold(root, accessor, beforeIdentity.getType(), beforeRollback);
            // A throwing adapter may have already written all, some, or none of
            // the offered stack. The mutated ItemStack amount is NOT a receipt.
            TransferAudit.recordRollback(beforeRollback, 0);
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.SEVERE,
                "A Networks " + operation + " rollback of up to " + beforeRollback
                    + " " + beforeIdentity.getType() + " item(s) at " + fallbackLocation
                    + " encountered a source-network write exception. The accepted amount"
                    + " is unverified; NO item was world-dropped or automatically refunded."
                    + " Preserve this location, server log and storage state for investigation.",
                storageFailure);
            return;
        }

        final int afterRollback;
        try {
            afterRollback = validAmount(remainder);
            if (afterRollback > beforeRollback
                || (afterRollback > 0 && !beforeIdentity.isSimilar(remainder))) {
                throw new IllegalStateException("Network rollback changed the offered item identity or increased it");
            }
        } catch (RuntimeException | LinkageError verificationFailure) {
            TransferAudit.recordFailure();
            RootRollbackSafetyHold.hold(root, accessor, beforeIdentity.getType(), beforeRollback);
            TransferAudit.recordRollback(beforeRollback, 0);
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.SEVERE,
                "A Networks " + operation + " rollback could not verify the offered item's"
                    + " identity/count after a network insertion at " + fallbackLocation
                    + ". No unverified remainder was world-dropped; investigate storage.",
                verificationFailure);
            return;
        }

        TransferAudit.recordRollback(beforeRollback, beforeRollback - afterRollback);
        if (afterRollback > 0) {
            dropLossPreventionRemainder(remainder, fallbackLocation, operation, beforeRollback);
        }
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

        // A producing machine may add more of the SAME item during addItemStack0's callbacks.
        // Its newly generated output is not part of our detached offer. Preserve that increase
        // while removing exactly the amount already accepted by the network. A changed identity
        // or a decrease remains ambiguous and fails closed without touching the source menu.
        final ItemStack sourceBeforeCommit;
        final ItemStack committedSource;
        ItemStack observedSource = null;
        try {
            observedSource = sourceMenu.getItemInSlot(slot);
            if (observedSource == null
                || observedSource.getAmount() < originalSource.getAmount()
                || !originalSource.isSimilar(observedSource)) {
                throw new IllegalStateException("The menu input changed during the network deposit "
                    + describeMenuSourceChange(sourceMenu.getLocation(), slot, originalSource, observedSource));
            }

            sourceBeforeCommit = snapshotMenuSource(observedSource);
            // A clean VOID offer discards only the ORIGINAL output, never new production.
            // Failed offers may have accepted some items but may not void the remainder.
            final int remaining = voidRemainder && !result.failed()
                ? sourceBeforeCommit.getAmount() - originalSource.getAmount()
                : sourceBeforeCommit.getAmount() - result.moved();
            if (remaining < 0) {
                throw new IllegalStateException("Negative reconciled menu source amount");
            }
            if (remaining == 0) {
                committedSource = null;
            } else {
                committedSource = snapshotMenuSource(sourceBeforeCommit);
                committedSource.setAmount(remaining);
            }
        } catch (RuntimeException | LinkageError sourceFailure) {
            final String sourceContext =
                describeMenuSourceChange(sourceMenu.getLocation(), slot, originalSource, observedSource);
            compensateCommittedDeposit(root, accessor, originalSource, result.moved(),
                sourceMenu.getLocation(), "changed menu source " + sourceContext, sourceFailure);
            return MenuDepositResult.FAILED;
        }

        // Source updates and dirty marking form one commit boundary. A write-then-throw
        // must restore the latest verified source (including any newly generated output),
        // not the stale pre-offer snapshot. Never restore over another plugin's replacement.
        try {
            sourceMenu.replaceExistingItem(slot, committedSource);
            sourceMenu.markDirty();
            return new MenuDepositResult(result.moved(), result.failed());
        } catch (RuntimeException | LinkageError exception) {
            try {
                ItemStack current = sourceMenu.getItemInSlot(slot);
                if (sourceStillMatches(sourceBeforeCommit, current)
                    || sourceStillMatches(committedSource, current)) {
                    sourceMenu.replaceExistingItem(slot, sourceBeforeCommit);
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

        if (amount <= 0 || RootRollbackSafetyHold.isHeld(root, accessor)) {
            // An earlier ambiguous refund through this accessor must be resolved
            // before any further network withdrawals can be attempted.
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
            // The actual NetworkRoot entry point already recorded the failed
            // adapter operation when it quarantined this controller. Do not
            // count the same failure twice; mock/foreign roots still count here.
            if (!RootRollbackSafetyHold.isHeld(root, accessor)) {
                TransferAudit.recordFailure();
            }
            Networks.getInstance().getLogger().log(
                java.util.logging.Level.WARNING,
                "A Networks deposit failed; its reported remainder may be unverified and the root must remain quarantined.",
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
        if (committed > recovered) {
            // This rollback could not prove that the entire network-side
            // deposit was undone. Leaving this controller live would allow
            // repeatedly contested source writes to continue indefinitely.
            final Material material = template == null ? Material.BARRIER : template.getType();
            RootRollbackSafetyHold.hold(root, accessor, material, committed - recovered);
        }
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

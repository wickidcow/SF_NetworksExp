package io.github.sefiraat.networks.slimefun.network;

import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.api.interfaces.SoftCellBannable;
import com.balugaq.netex.utils.Lang;
import com.bgsoftware.wildchests.api.WildChestsAPI;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.libraries.paperlib.PaperLib;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.CrafterInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

@SuppressWarnings({"DuplicatedCode", "GrazieInspection"})
public class NetworkVanillaPusher extends NetworkDirectional implements SoftCellBannable {

    private static final int[] BACKGROUND_SLOTS = new int[]{
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 15, 16, 17, 18, 20, 22, 23, 24, 26, 27, 28, 30, 31, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int INPUT_SLOT = 25;
    private static final int NORTH_SLOT = 11;
    private static final int SOUTH_SLOT = 29;
    private static final int EAST_SLOT = 21;
    private static final int WEST_SLOT = 19;
    private static final int UP_SLOT = 14;
    private static final int DOWN_SLOT = 32;

    public NetworkVanillaPusher(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack @NotNull [] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.PUSHER);
        this.getSlotsToDrop().add(INPUT_SLOT);
    }

    @Override
    protected void onTick(@Nullable BlockMenu blockMenu, @NotNull Block block) {
        super.onTick(blockMenu, block);
        if (blockMenu != null) {
            tryPushItem(blockMenu);
        }
    }

    private void tryPushItem(@NotNull BlockMenu blockMenu) {
        final NodeDefinition definition = NetworkStorage.getNode(blockMenu.getLocation());

        if (definition == null || definition.getNode() == null) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_NETWORK_FOUND);
            return;
        }

        if (checkSoftCellBan(blockMenu.getLocation(), definition.getNode().getRoot())) {
            return;
        }

        final BlockFace direction = getCurrentDirection(blockMenu);
        final Block block = blockMenu.getBlock();
        final Block targetBlock = blockMenu.getBlock().getRelative(direction);
        // Fix for early vanilla pusher release
        /* Netex - #293
        // No longer check permission
        final String ownerUUID = StorageCacheUtils.getData(block.getLocation(), OWNER_KEY);
        if (ownerUUID == null) {
            sendFeedback(block.getLocation(), FeedbackType.NO_OWNER_FOUND);
            return;
        }
        final UUID uuid = UUID.fromString(ownerUUID);
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

        // dirty fix
        try {
            if (!Slimefun.getProtectionManager()
                .hasPermission(offlinePlayer, targetBlock, Interaction.INTERACT_BLOCK)) {
                sendFeedback(block.getLocation(), FeedbackType.NO_PERMISSION);
                return;
            }
        } catch (NullPointerException ex) {
            sendFeedback(block.getLocation(), FeedbackType.ERROR_OCCURRED);
            return;
        }

         */
        // Netex start - #287
        if (StorageCacheUtils.getMenu(targetBlock.getLocation()) != null) {
            return;
        }
        // Netex end - #287

        final BlockState blockState = PaperLib.getBlockState(targetBlock, false).getState();

        if (!(blockState instanceof InventoryHolder holder)) {
            sendFeedback(block.getLocation(), FeedbackType.NO_INVENTORY_FOUND);
            return;
        }

        final Inventory inventory = holder.getInventory();
        if (Networks.getInstance().getMCVersion().isAtLeast(MinecraftVersion.V1_21)
            && inventory instanceof CrafterInventory) {
            sendFeedback(block.getLocation(), FeedbackType.NOT_ALLOWED_BLOCK);
            return;
        }
        final ItemStack stack = blockMenu.getItemInSlot(INPUT_SLOT);

        if (stack == null || stack.getType() == Material.AIR) {
            sendFeedback(block.getLocation(), FeedbackType.NO_ITEM_FOUND);
            return;
        }

        boolean wildChests = Networks.getSupportedPluginManager().isWildChests();
        boolean isChest = wildChests && WildChestsAPI.getChest(targetBlock.getLocation()) != null;

        sendDebugMessage(block.getLocation(), String.format(Lang.getString("messages.debug.wildchests"), wildChests));
        sendDebugMessage(block.getLocation(), String.format(Lang.getString("messages.debug.ischest"), isChest));

        boolean moved = false;
        if (inventory instanceof FurnaceInventory furnace) {
            moved = handleFurnace(stack, furnace, blockMenu.getLocation());
        } else if (inventory instanceof BrewerInventory brewer) {
            moved = handleBrewingStand(stack, brewer, blockMenu.getLocation());
        } else if (wildChests && isChest) {
            sendDebugMessage(block.getLocation(), Lang.getString("messages.debug.wildchests-trigger-failed"));
        } else {
            // Ordinary inventories may accept only part of a stack. Build the destination state on detached
            // snapshots, commit it, verify exact readback, then reduce the live source by only the accepted amount.
            sendDebugMessage(block.getLocation(), Lang.getString("messages.debug.wildchests-trigger-success"));
            moved = finishDestinationCommit(
                commitIntoStorageContents(
                    stack,
                    inventory::getStorageContents,
                    inventory::setStorageContents),
                blockMenu.getLocation(),
                "vanilla inventory");
        }

        if (moved) {
            blockMenu.markDirty();
            sendFeedback(blockMenu.getLocation(), FeedbackType.WORKING);
        }
    }

    private boolean handleFurnace(
        @NotNull ItemStack stack,
        @NotNull FurnaceInventory furnace,
        @NotNull org.bukkit.Location location) {

        if (stack.getType().isFuel()
            && isEmpty(furnace.getFuel())) {
            return finishDestinationCommit(
                commitIntoEmptyDestination(stack, furnace::getFuel, furnace::setFuel),
                location,
                "furnace fuel");
        }

        if (furnace.canSmelt(stack)
            && isEmpty(furnace.getSmelting())) {
            return finishDestinationCommit(
                commitIntoEmptyDestination(stack, furnace::getSmelting, furnace::setSmelting),
                location,
                "furnace input");
        }

        return false;
    }

    private boolean handleBrewingStand(
        @NotNull ItemStack stack,
        @NotNull BrewerInventory brewer,
        @NotNull org.bukkit.Location location) {

        if (stack.getType() == Material.BLAZE_POWDER) {
            if (isEmpty(brewer.getFuel())) {
                return finishDestinationCommit(
                    commitIntoEmptyDestination(stack, brewer::getFuel, brewer::setFuel),
                    location,
                    "brewing fuel");
            }

            if (isEmpty(brewer.getIngredient())) {
                return finishDestinationCommit(
                    commitIntoEmptyDestination(stack, brewer::getIngredient, brewer::setIngredient),
                    location,
                    "brewing ingredient");
            }
        } else if (stack.getType() == Material.POTION
            || stack.getType() == Material.SPLASH_POTION
            || stack.getType() == Material.LINGERING_POTION) {
            for (int i = 0; i < 3; i++) {
                final int bottleSlot = i;
                if (isEmpty(brewer.getItem(bottleSlot))) {
                    final DestinationCommitResult result = commitIntoEmptyDestination(
                        stack,
                        () -> brewer.getItem(bottleSlot),
                        item -> brewer.setItem(bottleSlot, item));
                    if (finishDestinationCommit(result, location, "brewing bottle slot " + bottleSlot)) {
                        return true;
                    }
                }
            }
        } else if (isEmpty(brewer.getIngredient())) {
            return finishDestinationCommit(
                commitIntoEmptyDestination(stack, brewer::getIngredient, brewer::setIngredient),
                location,
                "brewing ingredient");
        }

        return false;
    }

    /**
     * Commits as much of a live pusher stack as fits into ordinary inventory storage using copy-on-write
     * destination snapshots.
     *
     * <p>The planner preserves the historical ordering: fill every compatible partial stack first, then
     * split the remainder across empty storage slots. Neither the live source nor the destination is mutated
     * while planning. A detached desired snapshot is written, exact destination readback is required, and
     * only then is the live source reduced by the accepted amount.</p>
     *
     * <p>If the destination write throws or normalizes data, the original storage snapshot is restored before
     * preserving the source. If rollback cannot be verified, the attempted accepted amount is removed from the
     * source conservatively so the same logical items are not knowingly left on both sides.</p>
     */
    static @NotNull DestinationCommitResult commitIntoStorageContents(
        @NotNull ItemStack source,
        @NotNull Supplier<ItemStack[]> contentsReader,
        @NotNull Consumer<ItemStack[]> contentsWriter) {

        if (isEmpty(source)) {
            return DestinationCommitResult.NONE;
        }

        final ItemStack sourceSnapshot;
        final ItemStack[] beforeSnapshot;
        final ItemStack[] desiredSnapshot;
        try {
            sourceSnapshot = detachedExactClone(source, "Vanilla Pusher source");
            final ItemStack[] before = contentsReader.get();
            if (before == null) {
                return new DestinationCommitResult(
                    false,
                    false,
                    new IllegalStateException("Vanilla Pusher destination returned null storage contents"));
            }
            beforeSnapshot = detachedStorageClone(before, "Vanilla Pusher destination baseline");
            desiredSnapshot = detachedStorageClone(beforeSnapshot, "Vanilla Pusher destination plan");
        } catch (RuntimeException | LinkageError setupFailure) {
            return new DestinationCommitResult(false, false, setupFailure);
        }

        final int originalAmount = sourceSnapshot.getAmount();
        int remaining = originalAmount;

        try {
            // Preserve InventoryUtil's ordering: fill all matching partial stacks first.
            for (int slot = 0; slot < desiredSnapshot.length && remaining > 0; slot++) {
                final ItemStack existing = desiredSnapshot[slot];
                if (isEmpty(existing)
                    || existing.getAmount() >= existing.getMaxStackSize()
                    || !StackUtils.itemsMatch(existing, sourceSnapshot, true, false)) {
                    continue;
                }

                final int handled = Math.min(existing.getMaxStackSize() - existing.getAmount(), remaining);
                existing.setAmount(existing.getAmount() + handled);
                remaining -= handled;
            }

            // Then split the remainder across empty storage slots.
            for (int slot = 0; slot < desiredSnapshot.length && remaining > 0; slot++) {
                if (!isEmpty(desiredSnapshot[slot])) {
                    continue;
                }

                final int handled = Math.min(remaining, sourceSnapshot.getMaxStackSize());
                final ItemStack placed = detachedExactClone(sourceSnapshot, "Vanilla Pusher empty-slot transfer");
                placed.setAmount(handled);
                desiredSnapshot[slot] = placed;
                remaining -= handled;
            }
        } catch (RuntimeException | LinkageError planningFailure) {
            return new DestinationCommitResult(false, false, planningFailure);
        }

        final int accepted = originalAmount - remaining;
        if (accepted <= 0) {
            return DestinationCommitResult.NONE;
        }

        final ItemStack[] writeContents;
        try {
            // Never give the destination our immutable expected readback snapshot.
            writeContents = detachedStorageClone(desiredSnapshot, "Vanilla Pusher destination write");
        } catch (RuntimeException | LinkageError cloneFailure) {
            return new DestinationCommitResult(false, false, cloneFailure);
        }

        Throwable failure = null;
        try {
            contentsWriter.accept(writeContents);
        } catch (RuntimeException | LinkageError writeFailure) {
            failure = writeFailure;
        }

        boolean destinationKnown = true;
        ItemStack[] current = null;
        try {
            current = contentsReader.get();
        } catch (RuntimeException | LinkageError readFailure) {
            failure = appendFailure(failure, readFailure);
            destinationKnown = false;
        }

        if (destinationKnown && storageExactlyMatches(desiredSnapshot, current)) {
            try {
                source.setAmount(remaining);
            } catch (RuntimeException | LinkageError sourceCommitFailure) {
                failure = appendFailure(failure, sourceCommitFailure);
            }

            try {
                if (sourceHasExpectedRemainder(sourceSnapshot, source, remaining)) {
                    // A source setter may report failure after applying the amount. Exact readback makes
                    // that a committed transaction, matching the destination write.
                    return new DestinationCommitResult(true, false, failure);
                }
                failure = appendFailure(
                    failure,
                    new IllegalStateException("Vanilla Pusher source did not retain the expected remainder"));
            } catch (RuntimeException | LinkageError sourceReadFailure) {
                failure = appendFailure(failure, sourceReadFailure);
            }
            // Destination committed but source could not be reconciled. Restore the destination and then
            // restore the original source amount before reporting that no move occurred.
        } else if (destinationKnown && storageExactlyMatches(beforeSnapshot, current)) {
            return new DestinationCommitResult(false, false, failure);
        }

        try {
            contentsWriter.accept(detachedStorageClone(beforeSnapshot, "Vanilla Pusher rollback write"));
        } catch (RuntimeException | LinkageError rollbackFailure) {
            failure = appendFailure(failure, rollbackFailure);
        }

        boolean rollbackKnown = true;
        ItemStack[] afterRollback = null;
        try {
            afterRollback = contentsReader.get();
        } catch (RuntimeException | LinkageError readFailure) {
            failure = appendFailure(failure, readFailure);
            rollbackKnown = false;
        }

        if (rollbackKnown && storageExactlyMatches(beforeSnapshot, afterRollback)) {
            try {
                if (sourceHasExpectedRemainder(sourceSnapshot, source, originalAmount)) {
                    return new DestinationCommitResult(false, false, failure);
                }

                if (sourceSnapshot.isSimilar(source)) {
                    source.setAmount(originalAmount);
                    if (sourceHasExpectedRemainder(sourceSnapshot, source, originalAmount)) {
                        return new DestinationCommitResult(false, false, failure);
                    }
                }

                failure = appendFailure(
                    failure,
                    new IllegalStateException(
                        "Vanilla Pusher rolled back the destination but could not restore the original source"));
            } catch (RuntimeException | LinkageError sourceRestoreFailure) {
                failure = appendFailure(failure, sourceRestoreFailure);
            }
            return new DestinationCommitResult(false, true, failure);
        }

        // The attempted destination state may still have survived. Reduce only the amount we attempted
        // to commit; preserve any source remainder that never fit.
        boolean sourceReconciled = false;
        try {
            if (sourceSnapshot.isSimilar(source) && source.getAmount() == originalAmount) {
                source.setAmount(remaining);
                sourceReconciled = sourceHasExpectedRemainder(sourceSnapshot, source, remaining);
            }
        } catch (RuntimeException | LinkageError sourceFailure) {
            failure = appendFailure(failure, sourceFailure);
        }

        if (!sourceReconciled) {
            failure = appendFailure(
                failure,
                new IllegalStateException(
                    "Vanilla Pusher could not reconcile the source after an unresolved inventory commit"));
        }
        if (failure == null) {
            failure = new IllegalStateException("Vanilla Pusher inventory destination could not be rolled back");
        }
        return new DestinationCommitResult(sourceReconciled, true, failure);
    }

    private static @NotNull ItemStack detachedExactClone(
        @NotNull ItemStack source,
        @NotNull String context) {

        final ItemStack clone = source.clone();
        if (clone == source || !exactlyMatches(source, clone)) {
            throw new IllegalStateException(context + " clone changed item data or aliased its source");
        }
        return clone;
    }

    private static ItemStack @NotNull [] detachedStorageClone(
        ItemStack @NotNull [] source,
        @NotNull String context) {

        final ItemStack[] clone = new ItemStack[source.length];
        for (int slot = 0; slot < source.length; slot++) {
            final ItemStack stack = source[slot];
            if (!isEmpty(stack)) {
                clone[slot] = detachedExactClone(stack, context + " slot " + slot);
            }
        }
        return clone;
    }

    private static boolean storageExactlyMatches(
        ItemStack @NotNull [] expected,
        ItemStack[] actual) {

        if (actual == null || actual.length != expected.length) {
            return false;
        }
        for (int slot = 0; slot < expected.length; slot++) {
            final ItemStack expectedStack = expected[slot];
            final ItemStack actualStack = actual[slot];
            if (isEmpty(expectedStack) && isEmpty(actualStack)) {
                continue;
            }
            if (isEmpty(expectedStack) || isEmpty(actualStack) || !exactlyMatches(expectedStack, actualStack)) {
                return false;
            }
        }
        return true;
    }

    private static boolean sourceHasExpectedRemainder(
        @NotNull ItemStack original,
        @NotNull ItemStack current,
        int expectedAmount) {

        if (expectedAmount <= 0) {
            return isEmpty(current);
        }
        return current.getAmount() == expectedAmount && original.isSimilar(current);
    }

    /**
     * Commits a live pusher stack into an empty special inventory slot without leaving a duplicate
     * when a Bukkit/third-party setter writes, normalizes, or throws before returning.
     *
     * <p>The destination is written from a detached transfer copy. A separate immutable snapshot is
     * retained for exact amount+metadata readback so an in-place destination normalization cannot redefine
     * what counts as a successful commit. The source is consumed only after that exact readback. A mismatched
     * destination is rolled back to empty. If rollback cannot be verified and
     * the destination remains non-empty (or cannot be read), the source is consumed conservatively:
     * once a write may have survived, retaining the source would create a duplicate.</p>
     */
    static @NotNull DestinationCommitResult commitIntoEmptyDestination(
        @NotNull ItemStack source,
        @NotNull Supplier<ItemStack> destinationReader,
        @NotNull Consumer<ItemStack> destinationWriter) {

        if (isEmpty(source)) {
            return DestinationCommitResult.NONE;
        }

        final ItemStack before;
        try {
            before = destinationReader.get();
        } catch (RuntimeException | LinkageError readFailure) {
            return new DestinationCommitResult(false, false, readFailure);
        }
        if (!isEmpty(before)) {
            return DestinationCommitResult.NONE;
        }

        final ItemStack snapshot;
        final ItemStack transfer;
        try {
            snapshot = source.clone();
            if (snapshot == source || !exactlyMatches(source, snapshot)) {
                return new DestinationCommitResult(
                    false,
                    false,
                    new IllegalStateException(
                        "Vanilla Pusher source clone changed item data or aliased the live source"));
            }

            /*
             * Keep a second, detached expected snapshot. Some InventoryHolder implementations mutate
             * the ItemStack object passed to their setter in place. Comparing readback with that same
             * mutable object can falsely accept a normalized amount/metadata value and then clear the
             * complete source stack. The destination receives transfer; snapshot remains immutable
             * from the destination's perspective and is the commit identity.
             */
            transfer = snapshot.clone();
            if (transfer == snapshot || !exactlyMatches(snapshot, transfer)) {
                return new DestinationCommitResult(
                    false,
                    false,
                    new IllegalStateException(
                        "Vanilla Pusher transfer clone changed item data or aliased the commit snapshot"));
            }
        } catch (RuntimeException | LinkageError cloneFailure) {
            return new DestinationCommitResult(false, false, cloneFailure);
        }

        Throwable failure = null;
        try {
            destinationWriter.accept(transfer);
        } catch (RuntimeException | LinkageError writeFailure) {
            failure = writeFailure;
        }

        boolean destinationKnown = true;
        ItemStack current = null;
        try {
            current = destinationReader.get();
        } catch (RuntimeException | LinkageError readFailure) {
            failure = appendFailure(failure, readFailure);
            destinationKnown = false;
        }

        if (destinationKnown && exactlyMatches(snapshot, current)) {
            try {
                source.setAmount(0);
                return new DestinationCommitResult(true, false, failure);
            } catch (RuntimeException | LinkageError sourceCommitFailure) {
                failure = appendFailure(failure, sourceCommitFailure);
                try {
                    if (isEmpty(source)) {
                        // The source mutation completed before the setter reported failure.
                        return new DestinationCommitResult(true, false, failure);
                    }
                } catch (RuntimeException | LinkageError sourceReadFailure) {
                    failure = appendFailure(failure, sourceReadFailure);
                }
                // Source is still present or unreadable. Fall through and roll the destination back.
            }
        }

        if (destinationKnown && isEmpty(current)) {
            return new DestinationCommitResult(false, false, failure);
        }

        // The setter left an unexpected/unknown state. Try to undo our write before touching the source.
        try {
            destinationWriter.accept(null);
        } catch (RuntimeException | LinkageError rollbackFailure) {
            failure = appendFailure(failure, rollbackFailure);
        }

        boolean rollbackKnown = true;
        ItemStack afterRollback = null;
        try {
            afterRollback = destinationReader.get();
        } catch (RuntimeException | LinkageError readFailure) {
            failure = appendFailure(failure, readFailure);
            rollbackKnown = false;
        }

        if (rollbackKnown && isEmpty(afterRollback)) {
            return new DestinationCommitResult(false, false, failure);
        }

        // A non-empty/unknown destination may still contain the attempted transfer. Consume the source
        // rather than knowingly allowing the same logical item to survive on both sides.
        boolean sourceConsumed = false;
        try {
            source.setAmount(0);
            sourceConsumed = true;
        } catch (RuntimeException | LinkageError sourceCommitFailure) {
            failure = appendFailure(failure, sourceCommitFailure);
            try {
                if (isEmpty(source)) {
                    sourceConsumed = true;
                }
            } catch (RuntimeException | LinkageError sourceReadFailure) {
                failure = appendFailure(failure, sourceReadFailure);
            }

            if (!sourceConsumed) {
                try {
                    // Fallback for unusual ItemStack subclasses that reject amount writes.
                    source.setType(Material.AIR);
                    sourceConsumed = isEmpty(source);
                } catch (RuntimeException | LinkageError sourceFallbackFailure) {
                    failure = appendFailure(failure, sourceFallbackFailure);
                }
            }
        }
        if (failure == null) {
            failure = new IllegalStateException("Vanilla Pusher destination could not be rolled back to empty");
        }
        if (!sourceConsumed) {
            failure = appendFailure(
                failure,
                new IllegalStateException(
                    "Vanilla Pusher could not consume the source after an unresolved destination commit"));
        }
        return new DestinationCommitResult(sourceConsumed, true, failure);
    }

    private boolean finishDestinationCommit(
        @NotNull DestinationCommitResult result,
        @NotNull org.bukkit.Location location,
        @NotNull String destination) {

        if (result.failure() != null) {
            Networks.getInstance().getLogger().log(
                result.ambiguous() ? Level.SEVERE : Level.WARNING,
                result.ambiguous()
                    ? "A Network Vanilla Pusher could not verify rollback for " + destination + " at " + location
                        + "; the source was consumed to prevent a duplicate."
                    : "A Network Vanilla Pusher encountered a destination write failure for " + destination + " at "
                        + location + "; item conservation was reconciled.",
                result.failure());
        }
        return result.moved();
    }

    private static boolean exactlyMatches(@NotNull ItemStack expected, @Nullable ItemStack actual) {
        return actual != null
            && actual.getAmount() == expected.getAmount()
            && expected.isSimilar(actual);
    }

    private static boolean isEmpty(@Nullable ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    private static @NotNull Throwable appendFailure(@Nullable Throwable existing, @NotNull Throwable next) {
        if (existing == null) {
            return next;
        }
        existing.addSuppressed(next);
        return existing;
    }

    record DestinationCommitResult(boolean moved, boolean ambiguous, @Nullable Throwable failure) {
        static final DestinationCommitResult NONE = new DestinationCommitResult(false, false, null);
    }

    @Override
    protected int @NotNull [] getBackgroundSlots() {
        return BACKGROUND_SLOTS;
    }

    @Override
    public int getNorthSlot() {
        return NORTH_SLOT;
    }

    @Override
    public int getSouthSlot() {
        return SOUTH_SLOT;
    }

    @Override
    public int getEastSlot() {
        return EAST_SLOT;
    }

    @Override
    public int getWestSlot() {
        return WEST_SLOT;
    }

    @Override
    public int getUpSlot() {
        return UP_SLOT;
    }

    @Override
    public int getDownSlot() {
        return DOWN_SLOT;
    }

    @Override
    public boolean runSync() {
        return true;
    }

    @Override
    public int[] getInputSlots() {
        return new int[]{INPUT_SLOT};
    }

    @Override
    protected Particle.@NotNull DustOptions getDustOptions() {
        return new Particle.DustOptions(Color.MAROON, 1);
    }
}

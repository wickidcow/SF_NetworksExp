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
import org.bukkit.block.BrewingStand;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionData;
import org.bukkit.potion.PotionType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;

@SuppressWarnings({"DuplicatedCode", "GrazieInspection"})
public class NetworkVanillaGrabber extends NetworkDirectional implements SoftCellBannable {

    private static final int[] BACKGROUND_SLOTS = new int[]{
        0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 12, 13, 15, 16, 17, 18, 20, 22, 23, 24, 26, 27, 28, 30, 31, 33, 34, 35, 36,
        37, 38, 39, 40, 41, 42, 43, 44
    };
    private static final int OUTPUT_SLOT = 25;
    private static final int NORTH_SLOT = 11;
    private static final int SOUTH_SLOT = 29;
    private static final int EAST_SLOT = 21;
    private static final int WEST_SLOT = 19;
    private static final int UP_SLOT = 14;
    private static final int DOWN_SLOT = 32;

    public NetworkVanillaGrabber(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack @NotNull [] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.GRABBER);
        this.getSlotsToDrop().add(OUTPUT_SLOT);
    }

    @Override
    protected void onTick(@Nullable BlockMenu blockMenu, @NotNull Block block) {
        super.onTick(blockMenu, block);
        if (blockMenu != null) {
            tryGrabItem(blockMenu);
        }
    }

    @SuppressWarnings("removal")
    private void tryGrabItem(@NotNull BlockMenu blockMenu) {

        final ItemStack itemInSlot = blockMenu.getItemInSlot(OUTPUT_SLOT);

        if (itemInSlot != null && itemInSlot.getType() != Material.AIR) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.ALREADY_HAS_ITEM);
            return;
        }

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
        final Block targetBlock = block.getRelative(direction);

        /* Netex - #293
        // No longer check permission
        // Fix for early vanilla pusher release
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

        boolean wildChests = Networks.getSupportedPluginManager().isWildChests();
        boolean isChest = wildChests && WildChestsAPI.getChest(targetBlock.getLocation()) != null;

        sendDebugMessage(block.getLocation(), String.format(Lang.getString("messages.debug.wildchests"), wildChests));
        sendDebugMessage(block.getLocation(), String.format(Lang.getString("messages.debug.ischest"), isChest));

        if (wildChests && isChest) {
            sendDebugMessage(block.getLocation(), Lang.getString("messages.debug.wildchests-trigger-failed"));
            sendFeedback(block.getLocation(), FeedbackType.PROTECTED_BLOCK);
            return;
        }

        sendDebugMessage(block.getLocation(), Lang.getString("messages.debug.wildchests-trigger-success"));
        final Inventory inventory = holder.getInventory();

        if (inventory instanceof FurnaceInventory) {
            if (!grabInventoryItem(blockMenu, inventory, 2)) {
                final ItemStack fuel = inventory.getItem(1);
                if (fuel != null && fuel.getType() == Material.BUCKET) {
                    grabInventoryItem(blockMenu, inventory, 1);
                }
            }
        } else if (inventory instanceof BrewerInventory) {
            if (!(blockState instanceof BrewingStand brewingStand) || brewingStand.getBrewingTime() > 0) {
                return;
            }

            for (int slot = 0; slot < 3; slot++) {
                final ItemStack stack = inventory.getItem(slot);
                if (stack == null || stack.getType() == Material.AIR) {
                    continue;
                }

                if (stack.getItemMeta() instanceof PotionMeta potionMeta) {
                    if (Networks.getInstance().getMCVersion().isAtLeast(MinecraftVersion.V1_20_5)) {
                        if (potionMeta.getBasePotionType() != PotionType.WATER) {
                            grabInventoryItem(blockMenu, inventory, slot);
                            break;
                        }
                    } else {
                        PotionData bpd = potionMeta.getBasePotionData();
                        if (bpd != null && bpd.getType() != PotionType.WATER) {
                            grabInventoryItem(blockMenu, inventory, slot);
                            break;
                        }
                    }
                } else {
                    grabInventoryItem(blockMenu, inventory, slot);
                    break;
                }
            }
        } else {
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                if (grabInventoryItem(blockMenu, inventory, slot)) {
                    break;
                }
            }
        }
    }

    private boolean grabInventoryItem(
        @NotNull BlockMenu blockMenu, @NotNull Inventory inventory, int sourceSlot) {
        if (sourceSlot < 0 || sourceSlot >= inventory.getSize()) {
            return false;
        }

        final ItemStack stack = inventory.getItem(sourceSlot);
        if (isEmpty(stack)) {
            return false;
        }

        final GrabCommitResult result = commitInventorySourceToOutput(
            stack,
            () -> inventory.getItem(sourceSlot),
            item -> inventory.setItem(sourceSlot, item),
            () -> blockMenu.getItemInSlot(OUTPUT_SLOT),
            item -> blockMenu.replaceExistingItem(OUTPUT_SLOT, item));

        if (result.failure() != null) {
            Networks.getInstance().getLogger().log(
                result.ambiguous() ? Level.SEVERE : Level.WARNING,
                result.ambiguous()
                    ? "A Network Vanilla Grabber could not fully reconcile source/destination state at "
                        + blockMenu.getLocation() + "; source removal was attempted to prevent a duplicate."
                    : "A Network Vanilla Grabber encountered a transfer failure at " + blockMenu.getLocation()
                        + "; item conservation was reconciled.",
                result.failure());
        }

        if (result.moved() || result.failure() != null) {
            blockMenu.markDirty();
        }
        if (!result.moved()) {
            return false;
        }

        sendFeedback(blockMenu.getLocation(), FeedbackType.WORKING);
        return true;
    }

    /**
     * Moves one complete vanilla-inventory stack into the grabber output as a verified two-sided transaction.
     *
     * <p>The source and destination are snapshotted before mutation. The destination receives a second detached
     * copy so in-place normalization cannot redefine the expected item. The vanilla source is cleared only after
     * exact destination readback. If source removal fails, the destination is rolled back before the source is
     * preserved/restored. If destination rollback itself cannot be verified, source removal is retried
     * conservatively so the same logical stack is not knowingly left on both sides.</p>
     */
    static @NotNull GrabCommitResult commitInventorySourceToOutput(
        @NotNull ItemStack liveSource,
        @NotNull Supplier<ItemStack> sourceReader,
        @NotNull Consumer<ItemStack> sourceWriter,
        @NotNull Supplier<ItemStack> destinationReader,
        @NotNull Consumer<ItemStack> destinationWriter) {

        final ItemStack sourceSnapshot;
        final ItemStack transfer;
        try {
            final ItemStack currentSource = sourceReader.get();
            if (isEmpty(currentSource) || !exactlyMatches(liveSource, currentSource)) {
                return GrabCommitResult.NONE;
            }
            if (!isEmpty(destinationReader.get())) {
                return GrabCommitResult.NONE;
            }

            sourceSnapshot = detachedExactClone(currentSource, "Vanilla Grabber source");
            transfer = detachedExactClone(sourceSnapshot, "Vanilla Grabber transfer");
        } catch (RuntimeException | LinkageError setupFailure) {
            return new GrabCommitResult(false, false, setupFailure);
        }

        Throwable failure = null;
        try {
            destinationWriter.accept(transfer);
        } catch (RuntimeException | LinkageError writeFailure) {
            failure = writeFailure;
        }

        boolean destinationKnown = true;
        ItemStack destinationCurrent = null;
        try {
            destinationCurrent = destinationReader.get();
        } catch (RuntimeException | LinkageError readFailure) {
            failure = appendFailure(failure, readFailure);
            destinationKnown = false;
        }

        if (destinationKnown && exactlyMatches(sourceSnapshot, destinationCurrent)) {
            try {
                sourceWriter.accept(null);
            } catch (RuntimeException | LinkageError sourceWriteFailure) {
                failure = appendFailure(failure, sourceWriteFailure);
            }

            try {
                if (isEmpty(sourceReader.get())) {
                    // Source removers may report failure after applying the removal.
                    return new GrabCommitResult(true, false, failure);
                }
            } catch (RuntimeException | LinkageError sourceReadFailure) {
                failure = appendFailure(failure, sourceReadFailure);
            }
            // Destination is committed but source removal was not verified. Roll back destination first.
        } else if (destinationKnown && isEmpty(destinationCurrent)) {
            return new GrabCommitResult(false, false, failure);
        }

        try {
            destinationWriter.accept(null);
        } catch (RuntimeException | LinkageError rollbackFailure) {
            failure = appendFailure(failure, rollbackFailure);
        }

        boolean destinationRolledBack = false;
        try {
            destinationRolledBack = isEmpty(destinationReader.get());
        } catch (RuntimeException | LinkageError readFailure) {
            failure = appendFailure(failure, readFailure);
        }

        if (destinationRolledBack) {
            try {
                final ItemStack sourceCurrent = sourceReader.get();
                if (exactlyMatches(sourceSnapshot, sourceCurrent)) {
                    return new GrabCommitResult(false, false, failure);
                }

                sourceWriter.accept(detachedExactClone(sourceSnapshot, "Vanilla Grabber source restore"));
                if (exactlyMatches(sourceSnapshot, sourceReader.get())) {
                    return new GrabCommitResult(false, false, failure);
                }
                failure = appendFailure(
                    failure,
                    new IllegalStateException(
                        "Vanilla Grabber rolled back the destination but could not restore the source"));
            } catch (RuntimeException | LinkageError sourceRestoreFailure) {
                failure = appendFailure(failure, sourceRestoreFailure);
            }
            return new GrabCommitResult(false, true, failure);
        }

        // Destination may still contain our attempted transfer. Remove the source conservatively to avoid
        // knowingly retaining the same logical stack in both places.
        boolean sourceRemoved = false;
        try {
            sourceWriter.accept(null);
        } catch (RuntimeException | LinkageError sourceWriteFailure) {
            failure = appendFailure(failure, sourceWriteFailure);
        }
        try {
            sourceRemoved = isEmpty(sourceReader.get());
        } catch (RuntimeException | LinkageError sourceReadFailure) {
            failure = appendFailure(failure, sourceReadFailure);
        }

        if (!sourceRemoved) {
            try {
                liveSource.setAmount(0);
                sourceRemoved = isEmpty(sourceReader.get());
            } catch (RuntimeException | LinkageError sourceFallbackFailure) {
                failure = appendFailure(failure, sourceFallbackFailure);
            }
        }

        if (failure == null) {
            failure = new IllegalStateException("Vanilla Grabber destination could not be rolled back");
        }
        if (!sourceRemoved) {
            failure = appendFailure(
                failure,
                new IllegalStateException(
                    "Vanilla Grabber could not remove the source after an unresolved destination commit"));
        }
        return new GrabCommitResult(sourceRemoved, true, failure);
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

    private static boolean exactlyMatches(@NotNull ItemStack expected, ItemStack actual) {
        return actual != null
            && actual.getAmount() == expected.getAmount()
            && expected.isSimilar(actual);
    }

    private static boolean isEmpty(ItemStack stack) {
        return stack == null || stack.getType() == Material.AIR || stack.getAmount() <= 0;
    }

    private static @NotNull Throwable appendFailure(Throwable existing, @NotNull Throwable next) {
        if (existing == null) {
            return next;
        }
        existing.addSuppressed(next);
        return existing;
    }

    record GrabCommitResult(boolean moved, boolean ambiguous, Throwable failure) {
        static final GrabCommitResult NONE = new GrabCommitResult(false, false, null);
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
    public int[] getOutputSlots() {
        return new int[]{OUTPUT_SLOT};
    }

    @Override
    protected Particle.@NotNull DustOptions getDustOptions() {
        return new Particle.DustOptions(Color.MAROON, 1);
    }
}

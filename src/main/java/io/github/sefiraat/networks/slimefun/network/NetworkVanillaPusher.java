package io.github.sefiraat.networks.slimefun.network;

import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.api.interfaces.SoftCellBannable;
import com.balugaq.netex.utils.InventoryUtil;
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

        final int before = stack.getAmount();
        boolean moved = false;
        if (inventory instanceof FurnaceInventory furnace) {
            moved = handleFurnace(stack, furnace, blockMenu.getLocation());
        } else if (inventory instanceof BrewerInventory brewer) {
            moved = handleBrewingStand(stack, brewer, blockMenu.getLocation());
        } else if (wildChests && isChest) {
            sendDebugMessage(block.getLocation(), Lang.getString("messages.debug.wildchests-trigger-failed"));
        } else {
            // Bukkit inventories may accept only part of a stack. Commit exactly the accepted amount and
            // leave the live source remainder in the Networks pusher menu.
            sendDebugMessage(block.getLocation(), Lang.getString("messages.debug.wildchests-trigger-success"));
            InventoryUtil.addItem(holder.getInventory(), stack);
            moved = stack.getAmount() < before;
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
     * Commits a live pusher stack into an empty special inventory slot without leaving a duplicate
     * when a Bukkit/third-party setter writes, normalizes, or throws before returning.
     *
     * <p>The destination is written first. The source is consumed only after an exact amount+metadata
     * readback. A mismatched destination is rolled back to empty. If rollback cannot be verified and
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

        final ItemStack transfer;
        try {
            transfer = source.clone();
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

        if (destinationKnown && exactlyMatches(transfer, current)) {
            source.setAmount(0);
            return new DestinationCommitResult(true, false, failure);
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
        source.setAmount(0);
        if (failure == null) {
            failure = new IllegalStateException("Vanilla Pusher destination could not be rolled back to empty");
        }
        return new DestinationCommitResult(true, true, failure);
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

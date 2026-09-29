package com.ytdd9527.networksexpansion.implementation.machines.cargo.transfer.line.basic;

import com.balugaq.netex.api.atrributes.WhitelistedVanillaGrabber;
import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.api.enums.TransferType;
import com.balugaq.netex.api.factories.TransferConfigFactory;
import com.balugaq.netex.api.interfaces.SoftCellBannable;
import com.balugaq.netex.api.transfer.TransferConfiguration;
import com.balugaq.netex.utils.Lang;
import com.bgsoftware.wildchests.api.WildChestsAPI;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.slimefun.network.NetworkDirectional;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.paperlib.PaperLib;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings({"DuplicatedCode", "GrazieInspection"})
public class WhitelistedLineTransferVanillaGrabber extends NetworkDirectional implements RecipeDisplayItem, SoftCellBannable, WhitelistedVanillaGrabber {
    private static final TransferConfiguration config =
        TransferConfigFactory.getTransferConfiguration(TransferType.WHITELISTED_LINE_TRANSFER_VANILLA_GRABBER);
    private static final int maxDistance = config.maxDistance;
    private static final int grabItemTick = config.defaultGrabTick;
    private final HashMap<Location, Integer> TICKER_MAP = new HashMap<>();

    public WhitelistedLineTransferVanillaGrabber(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack @NotNull [] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.LINE_TRANSFER_VANILLA_GRABBER);
    }

    @Override
    protected void onTick(@Nullable BlockMenu blockMenu, @NotNull Block block) {
        super.onTick(blockMenu, block);

        if (blockMenu == null) {
            sendFeedback(block.getLocation(), FeedbackType.INVALID_BLOCK);
            return;
        }
        final Location location = blockMenu.getLocation();
        if (grabItemTick != 1) {
            int tickCounter = getTickCounter(location);
            tickCounter = (tickCounter + 1) % grabItemTick;

            if (tickCounter == 0) {
                tryGrabItem(blockMenu);
            }

            updateTickCounter(location, tickCounter);
        } else {
            tryGrabItem(blockMenu);
        }
    }

    private int getTickCounter(Location location) {
        final Integer ticker = TICKER_MAP.get(location);
        if (ticker == null) {
            TICKER_MAP.put(location, 0);
            return 0;
        }
        return ticker;
    }

    private void updateTickCounter(Location location, int tickCounter) {
        TICKER_MAP.put(location, tickCounter);
    }

    private void tryGrabItem(@NotNull BlockMenu blockMenu) {
        final NodeDefinition definition = NetworkStorage.getNode(blockMenu.getLocation());

        if (definition == null || definition.getNode() == null) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_NETWORK_FOUND);
            return;
        }

        final NetworkRoot root = definition.getNode().getRoot();
        if (checkSoftCellBan(blockMenu.getLocation(), root)) {
            return;
        }

        final BlockFace direction = getCurrentDirection(blockMenu);

        // Fix for early vanilla pusher release
        final Block block = blockMenu.getBlock();
        /* Netex - #293
        // No longer check permission
        final String ownerUUID = StorageCacheUtils.getData(block.getLocation(), OWNER_KEY);
        if (ownerUUID == null) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_OWNER_FOUND);
            return;
        }
        final UUID uuid = UUID.fromString(ownerUUID);
        final OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

         */

        // dirty fix
        Block targetBlock = block.getRelative(direction);
        for (int d = 0; d <= maxDistance; d++) {
            /* Netex - #293
            // No longer check permission
            try {
                if (!Slimefun.getProtectionManager()
                    .hasPermission(offlinePlayer, targetBlock, Interaction.INTERACT_BLOCK)) {
                    sendFeedback(blockMenu.getLocation(), FeedbackType.NO_PERMISSION);
                    break;
                }
            } catch (NullPointerException ex) {
                sendFeedback(blockMenu.getLocation(), FeedbackType.ERROR_OCCURRED);
                break;
            }

             */
            // Netex start - #287
            if (StorageCacheUtils.getMenu(targetBlock.getLocation()) != null) {
                targetBlock = targetBlock.getRelative(direction); // call skip function ahead of time
                continue;
            }
            // Netex end - #287

            final BlockState blockState = PaperLib.getBlockState(targetBlock, false).getState();

            if (!(blockState instanceof InventoryHolder holder)) {
                sendFeedback(blockMenu.getLocation(), FeedbackType.NO_INVENTORY_FOUND);
                return;
            }

            boolean wildChests = Networks.getSupportedPluginManager().isWildChests();
            boolean isChest = wildChests && WildChestsAPI.getChest(targetBlock.getLocation()) != null;

            if (wildChests && isChest) {
                sendFeedback(blockMenu.getLocation(), FeedbackType.PROTECTED_BLOCK);
                continue;
            }

            final List<ItemStack> templates = getClonedTemplateItems(blockMenu);
            final Inventory inventory = holder.getInventory();

            grabInventory(blockMenu, blockState, inventory, root, templates);
            targetBlock = targetBlock.getRelative(direction);
        }
        sendFeedback(blockMenu.getLocation(), FeedbackType.WORKING);
    }

    @Override
    public boolean runSync() {
        return true;
    }

    @Override
    protected Particle.@NotNull DustOptions getDustOptions() {
        return new Particle.DustOptions(Color.MAROON, 1);
    }

    public @NotNull List<ItemStack> getDisplayRecipes() {
        List<ItemStack> displayRecipes = new ArrayList<>(6);
        displayRecipes.add(new CustomItemStack(
            Material.BOOK,
            Lang.getString("icons.mechanism.transfers.data_title"),
            "",
            String.format(Lang.getString("icons.mechanism.transfers.max_distance"), maxDistance),
            String.format(Lang.getString("icons.mechanism.transfers.grab_item_tick"), grabItemTick)));
        return displayRecipes;
    }

    @Override
    public int[] getTemplateSlots() {
        return config.getTemplateSlots();
    }
}

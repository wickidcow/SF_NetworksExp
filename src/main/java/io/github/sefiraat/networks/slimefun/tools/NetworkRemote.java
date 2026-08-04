package io.github.sefiraat.networks.slimefun.tools;

import com.balugaq.netex.utils.Lang;
import com.jeff_media.morepersistentdatatypes.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.items.SpecialSlimefunItem;
import com.ytdd9527.networksexpansion.implementation.machines.networks.advanced.NetworkCraftingGridNewStyle;
import com.ytdd9527.networksexpansion.implementation.machines.networks.advanced.NetworkGridNewStyle;
import io.github.sefiraat.networks.slimefun.network.grid.NetworkCraftingGrid;
import io.github.sefiraat.networks.slimefun.network.grid.NetworkGrid;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.Theme;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import lombok.Getter;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.Optional;

@Getter
public class NetworkRemote extends SpecialSlimefunItem {

    private static final String WIKI_PAGE = "tools/network-remote";

    private static final NamespacedKey KEY = Keys.newKey("location");
    private static final int[] RANGES = new int[]{150, 500, 0, -1};

    private final int range;

    public NetworkRemote(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack @NotNull [] recipe,
        int range) {
        super(itemGroup, item, recipeType, recipe);
        this.range = range;
        addItemHandler((ItemUseHandler) e -> {
            final Player player = e.getPlayer();
            if (player.isSneaking()) {
                final Optional<Block> optional = e.getClickedBlock();
                if (optional.isPresent()) {
                    final Block block = optional.get();
                    final SlimefunItem slimefunItem = StorageCacheUtils.getSfItem(block.getLocation());
                    if (Slimefun.getProtectionManager().hasPermission(player, block, Interaction.INTERACT_BLOCK)
                        && (slimefunItem instanceof NetworkGrid
                        || slimefunItem instanceof NetworkCraftingGrid
                        || slimefunItem instanceof NetworkGridNewStyle
                        || slimefunItem instanceof NetworkCraftingGridNewStyle)) {
                        setGrid(e.getItem(), block, player);
                    } else {
                        player.sendMessage(
                            Lang.getString("messages.unsupported-operation.remote.must_connect_to_grid"));
                    }
                }
            } else {
                tryOpenGrid(e.getItem(), player, NetworkRemote.this.range);
            }
            e.cancel();
        });
    }

    public static void setGrid(@NotNull ItemStack itemStack, @NotNull Block block, @NotNull Player player) {
        final ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        DataTypeMethods.setCustom(itemMeta, KEY, DataType.LOCATION, block.getLocation());
        itemStack.setItemMeta(itemMeta);
        player.sendMessage(Lang.getString("messages.completed-operation.remote.bound_to_grid"));
    }

    public static void tryOpenGrid(@NotNull ItemStack itemStack, @NotNull Player player, int range) {
        final ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta == null) {
            return;
        }
        final Location location = DataTypeMethods.getCustom(itemMeta, KEY, DataType.LOCATION);

        if (location != null) {
            final World world = location.getWorld();
            if (world == null) {
                player.sendMessage(Lang.getString("messages.unsupported-operation.remote.grid_not_loaded"));
                return;
            }

            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                player.sendMessage(Lang.getString("messages.unsupported-operation.remote.grid_not_loaded"));
                return;
            }

            final boolean sameDimension = world.equals(player.getWorld());

            if (range == -1
                || range == 0 && sameDimension
                || sameDimension && player.getLocation().distanceSquared(location) <= (double) range * range) {
                openGrid(location, player);
            } else {
                player.sendMessage(Lang.getString("messages.unsupported-operation.remote.grid_not_in_range"));
            }
        } else {
            player.sendMessage(Lang.getString("messages.unsupported-operation.remote.no_grid_bound"));
        }
    }

    public static void openGrid(@NotNull Location location, @NotNull Player player) {
        final World world = location.getWorld();
        if (world == null || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.remote.grid_not_loaded"));
            return;
        }

        final SlimefunItem liveItem = StorageCacheUtils.getSfItem(location);
        if (!isGrid(liveItem)) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.remote.not_a_grid_found"));
            return;
        }

        final SlimefunBlockData blockData = StorageCacheUtils.getBlock(location);
        if (blockData == null) {
            player.sendMessage(Theme.ERROR + "Unable to find the bound Network Grid");
            return;
        }

        StorageCacheUtils.executeAfterLoad(
            blockData,
            () -> {
                // Revalidate after the deferred load callback. A grid can be broken or replaced between the
                // remote click and this callback, especially around chunk unload/reload boundaries.
                final SlimefunItem currentItem = StorageCacheUtils.getSfItem(location);
                final boolean allowed = player.hasPermission("slimefun.inventory.bypass")
                    || Slimefun.getProtectionManager()
                    .hasPermission(player, location, Interaction.INTERACT_BLOCK);

                if (isGrid(currentItem) && allowed) {
                    final SlimefunBlockData currentData = StorageCacheUtils.getBlock(location);
                    final BlockMenu blockMenu = currentData == null ? null : currentData.getBlockMenu();
                    if (blockMenu != null && blockMenu.getLocation().equals(location)) {
                        blockMenu.open(player);
                        return;
                    }
                }
                player.sendMessage(Lang.getString("messages.unsupported-operation.remote.not_a_grid_found"));
            },
            false);
    }

    private static boolean isGrid(SlimefunItem item) {
        return item instanceof NetworkGrid
            || item instanceof NetworkCraftingGrid
            || item instanceof NetworkGridNewStyle
            || item instanceof NetworkCraftingGridNewStyle;
    }

    public static int[] getRanges() {
        return RANGES;
    }

    @Override
    public void postRegister() {
        addWikiPage(WIKI_PAGE);
    }
}

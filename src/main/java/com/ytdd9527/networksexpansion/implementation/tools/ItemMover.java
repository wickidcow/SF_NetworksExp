package com.ytdd9527.networksexpansion.implementation.tools;

import com.balugaq.netex.utils.Lang;
import com.jeff_media.morepersistentdatatypes.DataType;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.items.SpecialSlimefunItem;
import com.ytdd9527.networksexpansion.implementation.ExpansionItemStacks;
import com.ytdd9527.networksexpansion.implementation.machines.unit.NetworksDrawer;
import io.github.mooy1.infinityexpansion.items.storage.StorageCache;
import io.github.mooy1.infinityexpansion.items.storage.StorageUnit;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.network.barrel.FluffyBarrel;
import io.github.sefiraat.networks.network.barrel.InfinityBarrel;
import io.github.sefiraat.networks.network.barrel.NetworkStorage;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.DistinctiveItem;
import io.github.thebusybiscuit.slimefun4.core.handlers.ItemUseHandler;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.ncbpfluffybear.fluffymachines.items.Barrel;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import io.github.sefiraat.networks.utils.DisplayNameUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataHolder;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ItemMover extends SpecialSlimefunItem implements DistinctiveItem {
    private static final Set<UUID> ACTIVE_PLAYERS = ConcurrentHashMap.newKeySet();
    @NotNull
    public static final Interaction[] CHECK_INTERACTIONS =
        new Interaction[]{Interaction.PLACE_BLOCK, Interaction.BREAK_BLOCK, Interaction.INTERACT_BLOCK};

    @SuppressWarnings("deprecation")
    @NotNull
    public static final List<String> DEFAULT_LORE = ExpansionItemStacks.ITEM_MOVER.getItemMeta() == null
        ? new ArrayList<>()
        : (ExpansionItemStacks.ITEM_MOVER.getItemMeta().hasLore()
        && ExpansionItemStacks.ITEM_MOVER.getItemMeta().getLore() != null
        ? ExpansionItemStacks.ITEM_MOVER.getItemMeta().getLore()
        : new ArrayList<>());

    public ItemMover(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        @NotNull ItemStack @NotNull [] recipe) {
        super(itemGroup, item, recipeType, recipe);
        addItemHandler((ItemUseHandler) e -> {
            try {
                if (this.isDisabled()) {
                    return;
                }
            } catch (Exception ex) {
                return;
            }

            final Player player = e.getPlayer();
            final Optional<Block> optional = e.getClickedBlock();
            if (optional.isPresent()) {
                final ItemStack itemStack = e.getItem();
                if (itemStack.getType() != Material.DEBUG_STICK) {
                    player.sendMessage(Lang.getString("messages.unsupported-operation.item_mover.invalid_item_mover"));
                    return;
                }
                if (itemStack.getAmount() != 1) {
                    player.sendMessage(
                        Lang.getString("messages.unsupported-operation.item_mover.invalid_item_mover_amount"));
                    return;
                }
                if (!itemStack.hasItemMeta() || itemStack.getItemMeta() == null) {
                    player.sendMessage(
                        Lang.getString("messages.unsupported-operation.item_mover.invalid_item_mover_meta"));
                    return;
                }
                e.cancel();
                final EquipmentSlot hand = e.getHand();
                final Location location = optional.get().getLocation();
                if ((hand != EquipmentSlot.HAND && hand != EquipmentSlot.OFF_HAND)
                    || !Bukkit.isOwnedByCurrentRegion(player) || !Bukkit.isOwnedByCurrentRegion(location)
                    || !ACTIVE_PLAYERS.add(player.getUniqueId())) return;
                try {
                    // PlayerInteractEvent may retain an old/detached ItemStack. Bind the
                    // operation to its real hand before any barrel or inventory mutation.
                    ItemStack actual = player.getInventory().getItem(hand);
                    if (!itemStack.equals(actual)) return;
                    ItemStack expected = ItemMoverStorage.snapshot(actual);
                    if (hasPermission(player, location)) {
                        if (!player.isSneaking()) {
                            tryDepositIntoMover(player, hand, expected, location);
                        } else {
                            tryWithdrawFromMover(player, hand, expected, location);
                        }
                    }
                } finally {
                    ACTIVE_PLAYERS.remove(player.getUniqueId());
                }
            }
            e.cancel();
        });
    }

    @Nullable
    public static ItemStack getStoredItemStack(@Nullable PersistentDataHolder holder) {
        if (holder == null) {
            return null;
        }

        return DataTypeMethods.getCustom(holder, Keys.ITEM_MOVER_ITEM, DataType.ITEM_STACK);
    }

    @Nullable
    public static ItemStack getStoredItemStack(@Nullable ItemStack mover) {
        if (mover == null || mover.getType() == Material.AIR) {
            return null;
        }

        final ItemMeta itemMeta = mover.getItemMeta();
        if (!mover.hasItemMeta() || itemMeta == null) {
            return null;
        }

        return getStoredItemStack(itemMeta);
    }

    public static long getStoredAmount(@Nullable PersistentDataHolder holder) {
        if (holder == null) {
            return 0;
        }

        final Integer amountInt = DataTypeMethods.getCustom(holder, Keys.ITEM_MOVER_AMOUNT, PersistentDataType.INTEGER);
        final Long amountLong = DataTypeMethods.getCustom(holder, Keys.ITEM_MOVER_AMOUNT_LONG, PersistentDataType.LONG);

        return amountInt == null ? amountLong == null ? 0 : amountLong : amountInt;
    }

    public static long getStoredAmount(@Nullable ItemStack mover) {
        if (mover == null || mover.getType() == Material.AIR) {
            return 0;
        }

        final ItemMeta itemMeta = mover.getItemMeta();
        if (!mover.hasItemMeta() || itemMeta == null) {
            return 0;
        }

        return getStoredAmount(itemMeta);
    }

    public static void setStoredItemStack(@Nullable ItemStack mover, @Nullable ItemStack itemStack) {
        if (mover == null || mover.getType().isAir()) return;
        ItemStack before = ItemMoverStorage.snapshot(mover);
        ItemMoverStorage.commit(mover, before, ItemMoverStorage.prepareTemplate(before, itemStack));
    }

    public static void setStoredAmount(@Nullable ItemStack mover, long amount) {
        if (mover == null || mover.getType().isAir()) return;
        ItemStack before = ItemMoverStorage.snapshot(mover);
        ItemMoverStorage.commit(mover, before, ItemMoverStorage.prepareAmount(before, amount));
    }

    public static void depositItem(ItemStack mover, @Nullable ItemStack incoming) {
        ItemMoverStorage.deposit(mover, incoming);
    }

    public static void updateLore(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || !itemStack.hasItemMeta()) return;
        ItemStack before = ItemMoverStorage.snapshot(itemStack);
        final ItemMoverStorage.State state;
        try {
            state = ItemMoverStorage.inspect(before);
        } catch (IllegalArgumentException | IllegalStateException malformed) {
            // Display refresh must never erase or relabel an unresolved balance.
            return;
        }
        List<String> lore = cloneDefaultLore();
        if (state.template() != null && state.amount() > 0) {
            lore.add(String.format(
                Lang.getString("messages.normal-operation.item_mover.stored_item"),
                DisplayNameUtils.getDisplayName(state.template())));
            lore.add(String.format(Lang.getString("messages.normal-operation.item_mover.stored_amount"), state.amount()));
        }
        ItemMoverStorage.commit(itemStack, before, ItemMoverStorage.prepareLore(before, lore));
    }

    @Nullable
    public static BarrelIdentity getBarrel(@NotNull Player player, @NotNull Location location) {
        final SlimefunItem sfitem = StorageCacheUtils.getSlimefunItem(location);

        if (sfitem == null) {
            return null;
        }

        final boolean infinityEnabled = SupportedPluginManager.getInstance().isInfinityExpansion();
        final boolean fluffyEnabled = SupportedPluginManager.getInstance().isFluffyMachines();

        /*if (infinityEnabled && sfitem instanceof StorageUnit unit) {
            return getInfinityBarrel(location, unit);
        } else */
        if (fluffyEnabled && sfitem instanceof Barrel barrel) {
            return getFluffyBarrel(location, barrel);
        } else if (sfitem instanceof NetworkQuantumStorage) {
            return getNetworkStorage(location);
        } else if (sfitem instanceof NetworksDrawer) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.item_mover.suggest_use_drawers"));
            return null;
        }

        return null;
    }

    @Nullable
    public static InfinityBarrel getInfinityBarrel(@NotNull Location location, @NotNull StorageUnit unit) {
        StorageCache cache = unit.getCache(location);
        if (cache == null) {
            return null;
        }

        BlockMenu blockMenu = StorageCacheUtils.getMenu(location);
        if (blockMenu == null) {
            return null;
        }

        final SlimefunBlockData data = StorageCacheUtils.getBlock(blockMenu.getLocation());
        if (data == null) {
            return null;
        }

        final String storedString = data.getData("stored");
        if (storedString == null || storedString.isEmpty()) {
            return null;
        }

        final int stored;
        try {
            stored = Integer.parseInt(storedString);
        } catch (NumberFormatException e) {
            return null;
        }

        if (stored <= 0) {
            return null;
        }



        return new InfinityBarrel(location, InfinityBarrel.getActualItemStack(blockMenu), stored, cache);
    }

    @Nullable
    private static FluffyBarrel getFluffyBarrel(@NotNull Location location, @NotNull Barrel barrel) {
        BlockMenu menu = StorageCacheUtils.getMenu(location);
        // Use the same read-only reserve/output discovery as network operations.
        // The old constructor refreshed the live menu while merely inspecting it.
        return menu == null ? null : FluffyBarrel.fromMenu(menu, barrel, false);
    }

    @Nullable
    public static NetworkStorage getNetworkStorage(@NotNull Location location) {
        QuantumCache cache = NetworkQuantumStorage.getCaches().get(location);
        if (cache == null) {
            return null;
        }

        final ItemStack stored = cache.getItemStack();
        if (stored == null || stored.getType() == Material.AIR) {
            return null;
        }

        final long storedAmount = cache.getAmountLong();
        if (storedAmount < 0) {
            return null;
        }

        return new NetworkStorage(location, stored, storedAmount, cache.getLimitLong());
    }

    private static void tryDepositIntoMover(
        @NotNull Player player, @NotNull EquipmentSlot hand, ItemStack mover, @NotNull Location clickedLocation) {
        if (!hasPermission(player, clickedLocation)) return;
        BarrelIdentity barrel = getBarrel(player, clickedLocation);
        if (barrel == null || barrel.getItemStack() == null || barrel.getAmount() <= 0) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.item_mover.invalid_storage"));
            return;
        }
        int available = (int) Math.min(Integer.MAX_VALUE, barrel.getAmount());
        ItemStack template = ItemMoverStorage.snapshot(barrel.getItemStack());
        String name = DisplayNameUtils.getDisplayName(template);
        int moved = ItemMoverStorage.transferFromSlot(mover,
            () -> player.getInventory().getItem(hand), candidate -> player.getInventory().setItem(hand, candidate),
            template, available,
            requested -> barrel.requestItem(new ItemRequest(template, requested)));
        player.sendMessage(String.format(
            Lang.getString("messages.completed-operation.item_mover.deposit_success"), name, moved));
        updateHeldLore(player, hand);
    }

    private static void tryWithdrawFromMover(
        @NotNull Player player, @NotNull EquipmentSlot hand, ItemStack mover, @NotNull Location clickedLocation) {
        if (!hasPermission(player, clickedLocation)) return;
        ItemMoverStorage.State state = ItemMoverStorage.inspect(mover);
        BarrelIdentity barrel = getBarrel(player, clickedLocation);
        if (barrel == null) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.item_mover.invalid_storage"));
            return;
        }
        if (state.template() == null || state.amount() <= 0) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.item_mover.empty_mover"));
            return;
        }
        long occupied = barrel.getAmount();
        long limit = barrel.getLimit();
        if (occupied < 0 || limit < occupied || occupied == limit) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.item_mover.full_storage"));
            return;
        }
        int maximum = (int) Math.min(Integer.MAX_VALUE, limit - occupied);
        String name = DisplayNameUtils.getDisplayName(state.template());
        int moved = ItemMoverStorage.transferToSlot(mover,
            () -> player.getInventory().getItem(hand), candidate -> player.getInventory().setItem(hand, candidate),
            maximum, barrel::depositItemStack);
        player.sendMessage(String.format(
            Lang.getString("messages.completed-operation.item_mover.withdraw_success"), name, moved));
        updateHeldLore(player, hand);
    }

    private static void updateHeldLore(Player player, EquipmentSlot hand) {
        ItemStack before = ItemMoverStorage.snapshot(player.getInventory().getItem(hand));
        if (!(SlimefunItem.getByItem(before) instanceof ItemMover)) return;
        ItemStack prepared = ItemMoverStorage.snapshot(before);
        updateLore(prepared);
        if (!before.equals(prepared)) {
            ItemMoverStorage.commitSlot(() -> player.getInventory().getItem(hand),
                candidate -> player.getInventory().setItem(hand, candidate), before, prepared);
        }
    }

    private static @NotNull List<String> cloneDefaultLore() {
        return new ArrayList<>(DEFAULT_LORE);
    }

    public static boolean hasPermission(@NotNull Player player, @NotNull Location location) {
        for (Interaction interaction : CHECK_INTERACTIONS) {
            if (!Slimefun.getProtectionManager().hasPermission(player, location, interaction)) {
                return false;
            }
        }

        SlimefunItem sfitem = StorageCacheUtils.getSlimefunItem(location);
        return Slimefun.getPermissionsService().hasPermission(player, sfitem);
    }

    @Override
    public boolean canStack(@NotNull ItemMeta itemMeta, @NotNull ItemMeta itemMeta1) {
        return itemMeta.getPersistentDataContainer().equals(itemMeta1.getPersistentDataContainer());
    }
}

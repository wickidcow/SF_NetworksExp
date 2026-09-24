package io.github.sefiraat.networks.network.barrel;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.sefiraat.networks.utils.StackUtils;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class NetworkStorage extends BarrelIdentity {
    public NetworkStorage(@NotNull Location location, ItemStack itemStack, long amount) {
        super(location, itemStack, amount, amount, BarrelType.NETWORKS);
    }

    public NetworkStorage(@NotNull Location location, ItemStack itemStack, long amount, long limit) {
        super(location, itemStack, amount, limit, BarrelType.NETWORKS);
    }

    /**
     * NetworkStorage is backed by a live QuantumCache. An empty Quantum Storage can become assigned after this
     * BarrelIdentity was created, so matching must consult the live cache instead of the constructor-time template.
     */
    @Override
    public boolean canAccept(@NotNull ItemStack incoming) {
        final BlockMenu blockMenu = StorageCacheUtils.getMenu(this.getLocation());
        if (blockMenu == null) {
            return false;
        }

        final QuantumCache cache = NetworkQuantumStorage.getCaches().get(blockMenu.getLocation());
        return cache != null && StackUtils.itemsMatch(cache, incoming);
    }

    @Override
    @Nullable
    public ItemStack requestItem(@NotNull ItemRequest itemRequest) {
        final BlockMenu blockMenu = StorageCacheUtils.getMenu(this.getLocation());

        if (blockMenu == null) {
            return null;
        }

        final QuantumCache cache = NetworkQuantumStorage.getCaches().get(blockMenu.getLocation());

        if (cache == null) {
            return null;
        }

        return NetworkQuantumStorage.getItemStack(cache, blockMenu, itemRequest.getAmount());
    }

    /**
     * Returns whether this Network Quantum Storage is currently empty and has no item type assigned.
     * Such a storage can be used as a final structured-storage destination before Network Cells.
     */
    public boolean isUnassigned() {
        final BlockMenu blockMenu = StorageCacheUtils.getMenu(this.getLocation());
        if (blockMenu == null) {
            return false;
        }

        final QuantumCache cache = NetworkQuantumStorage.getCaches().get(blockMenu.getLocation());
        if (cache == null || cache.getAmountLong() > 0) {
            return false;
        }

        final ItemStack stored = cache.getItemStack();
        return stored == null || stored.getType().isAir();
    }

    @Override
    public void depositItemStack(ItemStack @NotNull [] itemsToDeposit) {
        if (!(StorageCacheUtils.getSfItem(this.getLocation()) instanceof NetworkQuantumStorage)) {
            return;
        }

        final BlockMenu blockMenu = StorageCacheUtils.getMenu(this.getLocation());
        if (blockMenu == null) {
            return;
        }

        final QuantumCache cache = NetworkQuantumStorage.getCaches().get(this.getLocation());
        if (cache == null) {
            return;
        }

        /*
         * Network Quantum Storage historically only accepted network deposits after its item type
         * had already been assigned manually. When the cache is empty and unassigned, seed only the
         * item template here, then use the normal input path so capacity/void/remainder handling stays
         * exactly the same. This lets structured storage claim an item before Network Cells without
         * deleting overflow when the quantum storage is full.
         */
        if (isUnassigned()) {
            for (ItemStack item : itemsToDeposit) {
                if (item == null || item.getType().isAir()) {
                    continue;
                }

                NetworkQuantumStorage.setItem(blockMenu, item, 0L);

                /*
                 * setItem can reject blacklisted/nested items. Only stop searching the supplied array after the
                 * live cache was actually assigned, and refresh this BarrelIdentity's template so any legacy
                 * caller that still inspects it does not retain the old null template.
                 */
                final ItemStack assigned = cache.getItemStack();
                if (assigned != null && !assigned.getType().isAir()) {
                    setItemStack(assigned.clone());
                    break;
                }
            }
        }

        NetworkQuantumStorage.tryInputItem(blockMenu.getLocation(), itemsToDeposit, cache);
    }

    @Override
    public int[] getInputSlot() {
        return new int[]{NetworkQuantumStorage.INPUT_SLOT};
    }

    @Override
    public int[] getOutputSlot() {
        return new int[]{NetworkQuantumStorage.OUTPUT_SLOT};
    }
}

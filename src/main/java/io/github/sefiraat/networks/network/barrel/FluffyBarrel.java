package io.github.sefiraat.networks.network.barrel;

import com.balugaq.netex.utils.BlockMenuUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.utils.StackUtils;
import io.ncbpfluffybear.fluffymachines.items.Barrel;
import lombok.Getter;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public class FluffyBarrel extends BarrelIdentity {
    private final boolean voidExcess;

    public FluffyBarrel(@NotNull Location location, ItemStack itemStack, int amount, int limit, boolean voidExcess) {
        this(location, itemStack, (long) amount, limit, voidExcess);
    }

    public FluffyBarrel(@NotNull Location location, ItemStack itemStack, long amount, int limit, boolean voidExcess) {
        this(location, itemStack, amount, limit, voidExcess, true);
    }

    private FluffyBarrel(
        @NotNull Location location,
        @Nullable ItemStack itemStack,
        long amount,
        int limit,
        boolean voidExcess,
        boolean refreshMenu
    ) {
        super(location, itemStack, amount, limit, BarrelType.FLUFFY);
        this.voidExcess = voidExcess;
        if (refreshMenu) {
            BlockMenu menu = StorageCacheUtils.getMenu(getLocation());
            Barrel barrel = (Barrel) StorageCacheUtils.getSlimefunItem(getLocation());
            if (barrel != null) {
                barrel.updateMenu(getLocation().getBlock(), menu, true, (int) getLimit());
            }
        }
    }

    /**
     * Reads the reserve and the real output buffers without ticking or changing the barrel.
     *
     * <p>Fluffy's stored amount excludes its output slots. Older builds also replace the registered
     * display with a barrier as soon as that reserve reaches zero, while output still contains items.
     * A real output can identify those remaining items, but cannot identify an unknown positive reserve.</p>
     *
     * @param includeEmpty retained for the common storage-discovery contract; entirely empty Fluffy
     *                     barrels remain unassigned, as in the existing Fluffy integration
     */
    public static @Nullable FluffyBarrel fromMenu(
        @NotNull BlockMenu menu,
        @NotNull Barrel barrel,
        boolean includeEmpty
    ) {
        final Block block = menu.getBlock();
        final int stored;
        try {
            stored = barrel.getStored(block);
        } catch (NumberFormatException exception) {
            // An unavailable or malformed reserve is not permission to invent its quantity.
            return null;
        }
        if (stored < 0) {
            return null;
        }

        ItemStack registered;
        try {
            registered = barrel.getStoredItem(block);
        } catch (NullPointerException exception) {
            // Older Fluffy APIs dereference a missing display instead of returning null.
            registered = null;
        }
        ItemStack template = isUsable(registered) && registered.getType() != Material.BARRIER ? registered : null;
        if (stored > 0 && template == null) {
            return null;
        }

        long total = stored;
        for (int slot : BlockMenuUtil.getSafeTransportSlots(menu, ItemTransportFlow.WITHDRAW)) {
            final ItemStack output = menu.getItemInSlot(slot);
            if (!isUsable(output)) {
                continue;
            }
            if (template == null) {
                template = output;
            } else if (!template.isSimilar(output)) {
                // A single-type barrel cannot safely attribute mixed output to one stored identity.
                return null;
            }
            total += output.getAmount();
        }

        if (template == null || total <= 0L) {
            return null;
        }
        final ItemStack identity = template.clone();
        identity.setAmount(1);
        final boolean voidExcess = Boolean.parseBoolean(StorageCacheUtils.getData(menu.getLocation(), "trash"));
        return new FluffyBarrel(menu.getLocation(), identity, total, barrel.getCapacity(block), voidExcess, false);
    }

    private static boolean isUsable(@Nullable ItemStack item) {
        return item != null && item.getType() != Material.AIR && item.getAmount() > 0;
    }

    @Nullable
    @Override
    public ItemStack requestItem(@NotNull ItemRequest itemRequest) {
        BlockMenu menu = StorageCacheUtils.getMenu(getLocation());
        if (menu == null) {
            return null;
        }

        int received = 0;
        ItemStack targetItem = itemRequest.getItemStack();
        for (int slot : getOutputSlot()) {
            ItemStack item = menu.getItemInSlot(slot);
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }

            if (StackUtils.itemsMatch(item, targetItem)) {
                int max = Math.min(item.getAmount(), itemRequest.getAmount() - received);
                BlockMenuUtil.consumeItem(menu, slot, max);
                received += max;
            }
        }

        if (received <= 0) {
            return null;
        }

        return StackUtils.getAsQuantity(targetItem, received);
    }

    @Override
    public void depositItemStack(ItemStack @NotNull [] itemsToDeposit) {
        BlockMenu menu = StorageCacheUtils.getMenu(getLocation());
        if (menu == null) {
            return;
        }

        BlockMenuUtil.pushItem(menu, itemsToDeposit, getInputSlot());
    }

    @Override
    public int[] getInputSlot() {
        BlockMenu menu = StorageCacheUtils.getMenu(getLocation());
        if (menu == null) {
            return new int[0];
        }
        return BlockMenuUtil.getSafeTransportSlots(menu, ItemTransportFlow.INSERT);
    }

    @Override
    public int[] getOutputSlot() {
        BlockMenu menu = StorageCacheUtils.getMenu(getLocation());
        if (menu == null) {
            return new int[0];
        }
        return BlockMenuUtil.getSafeTransportSlots(menu, ItemTransportFlow.WITHDRAW);
    }
}

package io.github.sefiraat.networks.network.barrel;

import com.balugaq.netex.utils.BlockMenuUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.integrations.infinityexpansion2.InfinityExpansion2Integration;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Networks view of any Infinity Expansion 2 StorageUnit tier.
 *
 * <p>All writes flow through the unit's actual menu slots. IE2 remains responsible for moving items between
 * those slots and its internal cache, preserving its blacklist, void-excess and persistence behavior.</p>
 */
public final class InfinityExpansion2Barrel extends BarrelIdentity {

    private final InfinityExpansion2Integration integration;
    private final SlimefunItem storageUnit;

    public InfinityExpansion2Barrel(
        @NotNull InfinityExpansion2Integration integration,
        @NotNull SlimefunItem storageUnit,
        @NotNull Location location,
        @Nullable ItemStack itemStack,
        long amount,
        long limit
    ) {
        super(location, itemStack, amount, limit, BarrelType.INFINITY_2);
        this.integration = integration;
        this.storageUnit = storageUnit;
    }

    @Override
    public @Nullable ItemStack requestItem(@NotNull ItemRequest itemRequest) {
        try {
            final BlockMenu menu = StorageCacheUtils.getMenu(getLocation());
            if (menu == null || itemRequest.getAmount() <= 0) {
                return null;
            }

            int received = 0;
            final ItemStack target = itemRequest.getItemStack();
            for (int slot : getOutputSlot()) {
                final ItemStack output = menu.getItemInSlot(slot);
                if (output == null || output.getType() == Material.AIR || !StackUtils.itemsMatch(output, target)) {
                    continue;
                }

                final int moved = Math.min(output.getAmount(), itemRequest.getAmount() - received);
                if (moved <= 0) {
                    break;
                }
                BlockMenuUtil.consumeItem(menu, slot, moved);
                received += moved;
            }

            return received <= 0 ? null : StackUtils.getAsQuantity(target, received);
        } catch (RuntimeException | LinkageError exception) {
            Networks.getSupportedPluginManager().disableOptionalIntegration("InfinityExpansion2", exception);
            return null;
        }
    }

    @Override
    public void depositItemStack(ItemStack @NotNull [] itemsToDeposit) {
        try {
            final BlockMenu menu = StorageCacheUtils.getMenu(getLocation());
            if (menu == null) {
                return;
            }

            final int[] inputSlots = getInputSlot();
            if (inputSlots.length == 0) {
                return;
            }

            for (ItemStack item : itemsToDeposit) {
                if (item == null || item.getType() == Material.AIR || item.getAmount() <= 0 || !canAccept(item)) {
                    continue;
                }
                BlockMenuUtil.pushItem(menu, item, inputSlots);
            }
        } catch (RuntimeException | LinkageError exception) {
            Networks.getSupportedPluginManager().disableOptionalIntegration("InfinityExpansion2", exception);
        }
    }

    @Override
    public boolean canAccept(@NotNull ItemStack incoming) {
        try {
            if (StackUtils.isBlacklisted(incoming) || integration.isStorageUnitItem(incoming)) {
                return false;
            }
            final ItemStack liveTemplate = integration.getLiveTemplate(getLocation(), storageUnit);
            return liveTemplate == null || StackUtils.itemsMatch(liveTemplate, incoming);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Networks.getSupportedPluginManager().disableOptionalIntegration("InfinityExpansion2", exception);
            return false;
        }
    }

    @Override
    public int @NotNull [] getInputSlot() {
        try {
            return integration.getInputSlots(storageUnit);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Networks.getSupportedPluginManager().disableOptionalIntegration("InfinityExpansion2", exception);
            return new int[0];
        }
    }

    @Override
    public int @NotNull [] getOutputSlot() {
        try {
            return integration.getOutputSlots(storageUnit);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Networks.getSupportedPluginManager().disableOptionalIntegration("InfinityExpansion2", exception);
            return new int[0];
        }
    }
}

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

    /** A local cache incident is not grounds for disabling the whole IE2 adapter. */
    private static final class IE2HeldStorageException extends IllegalStateException {
        private IE2HeldStorageException(String reason) {
            super(reason);
        }
    }

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
            // Existing barrel handles remain unsafe even when cache discovery
            // happened before an IE2 incident hold was installed.
            if (itemRequest.getAmount() <= 0 || integration.isTransferBlocked(getLocation(), storageUnit)) {
                return null;
            }
            final BlockMenu menu = StorageCacheUtils.getMenu(getLocation());
            if (menu == null) {
                return null;
            }

            final ItemStack withdrawn = InfinityExpansion2Withdrawal.withdraw(
                menu, getOutputSlot(), itemRequest.getItemStack(), itemRequest.getAmount(),
                (slot, amount) -> BlockMenuUtil.consumeItem(menu, slot, amount));
            if (withdrawn != null && integration.isTransferBlocked(getLocation(), storageUnit)) {
                // Source may already have been debited. Never expose uncertain
                // stock or manufacture a speculative restoration to the network.
                throw new IE2HeldStorageException("IE2 hold changed during physical output debit");
            }
            return withdrawn;
        } catch (IE2HeldStorageException exception) {
            throw exception; // Quarantine this transaction, NOT every IE2 unit.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Networks.getSupportedPluginManager().disableOptionalIntegration("InfinityExpansion2", exception);
            throw new IllegalStateException("InfinityExpansion2 output withdrawal is unverified", exception);
        }
    }

    @Override
    public void depositItemStack(ItemStack @NotNull [] itemsToDeposit) {
        try {
            if (integration.isTransferBlocked(getLocation(), storageUnit)) {
                return;
            }
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
                if (integration.isTransferBlocked(getLocation(), storageUnit)) {
                    return;
                }
                BlockMenuUtil.pushItem(menu, item, inputSlots);
                if (integration.isTransferBlocked(getLocation(), storageUnit)) {
                    // A setter callback could have changed IE2 cache ownership.
                    // Leave its physical slot for operator reconciliation.
                    throw new IE2HeldStorageException("IE2 hold changed during physical input insertion");
                }
            }
        } catch (IE2HeldStorageException exception) {
            throw exception; // Do not globally unregister otherwise healthy storage.
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            Networks.getSupportedPluginManager().disableOptionalIntegration("InfinityExpansion2", exception);
            throw new IllegalStateException("InfinityExpansion2 deposit acceptance is unverified", exception);
        }
    }

    @Override
    public boolean canAccept(@NotNull ItemStack incoming) {
        try {
            if (StackUtils.isBlacklisted(incoming) || integration.isStorageUnitItem(incoming)
                || integration.isTransferBlocked(getLocation(), storageUnit)) {
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

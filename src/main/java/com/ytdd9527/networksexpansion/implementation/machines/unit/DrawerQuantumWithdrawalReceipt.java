package com.ytdd9527.networksexpansion.implementation.machines.unit;

import com.balugaq.netex.api.data.ItemContainer;
import com.balugaq.netex.api.data.StorageUnitData;
import com.ytdd9527.networksexpansion.utils.itemstacks.ItemStackUtil;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentQuantumStorageType;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.BooleanSupplier;

/** Destination-slot receipts for the Drawer-to-Quantum GUI transfer. */
final class DrawerQuantumWithdrawalReceipt {
    private DrawerQuantumWithdrawalReceipt() {
    }

    static int transfer(BlockMenu menu, int destinationSlot, Location location, StorageUnitData drawer,
        ItemContainer source, ItemStack observedQuantum, long defaultLimit, BooleanSupplier attached) {
        ItemStack original = observedQuantum.clone();
        if (original.getAmount() != 1 || !attached.getAsBoolean()
            || !original.equals(menu.getItemInSlot(destinationSlot))) {
            return 0;
        }
        QuantumCache before = Keys.getQuantumCache(original.getItemMeta());
        ItemStack template = source.getSample();
        if (before != null && (before.getItemStack() == null
            || !StackUtils.itemsMatch(before.getItemStack(), template))) {
            return 0;
        }
        long balance = before == null ? 0 : before.getAmountLong();
        long limit = before == null ? defaultLimit : before.getLimitLong();
        int requested = DrawerQuantumTransferMath.toQuantum(source.getAmount(), balance, limit);
        if (requested <= 0) {
            return 0;
        }
        // Retain the established typed-entry policy: assigned Quantum keeps a zero
        // Drawer entry; first assignment can remove its exhausted source entry.
        return drawer.withdrawToVerifiedDestination(location, source, requested, before != null,
            (offered, sourceUnchanged) -> {
                int accepted = DrawerQuantumTransferMath.verifiedReceipt(template, offered, requested);
                if (accepted <= 0) {
                    return 0;
                }
                long credited = DrawerQuantumTransferMath.creditedAfterReceipt(balance, limit, requested, accepted);
                ItemStack exactTemplate = before == null ? template.clone() : before.getItemStack().clone();
                QuantumCache revised = before == null
                    ? new QuantumCache(exactTemplate, credited, limit, false, false)
                    : DrawerQuantumTransferMath.rebalanced(before, credited);
                ItemStack updated;
                try {
                    updated = original.clone();
                    ItemMeta meta = updated.getItemMeta();
                    DataTypeMethods.setCustom(meta, Keys.QUANTUM_STORAGE_INSTANCE,
                        PersistentQuantumStorageType.TYPE, revised);
                    revised.updateMetaLore(meta);
                    if (!updated.setItemMeta(meta)) {
                        return refuseUnchangedDestination(menu, destinationSlot, original);
                    }
                    updated = ItemStackUtil.getCleanItem(updated);
                    if (!sameQuantum(Keys.getQuantumCache(updated.getItemMeta()), revised)
                        || updated.getAmount() != 1 || !attached.getAsBoolean()
                        || !sourceUnchanged.getAsBoolean()
                        || !original.equals(menu.getItemInSlot(destinationSlot))) {
                        return refuseUnchangedDestination(menu, destinationSlot, original);
                    }
                } catch (RuntimeException | LinkageError preparationFailure) {
                    if (original.equals(menu.getItemInSlot(destinationSlot))) {
                        return 0;
                    }
                    throw preparationFailure;
                }
                Throwable writeFailure = null;
                try {
                    // Retain the full expected identity even if the setter mutates its argument.
                    menu.replaceExistingItem(destinationSlot, ItemStackUtil.getCleanItem(updated));
                } catch (RuntimeException | LinkageError failure) {
                    writeFailure = failure;
                }
                ItemStack actual = menu.getItemInSlot(destinationSlot);
                if (original.equals(actual)) {
                    return 0;
                }
                if (!updated.equals(actual) || !attached.getAsBoolean()
                    || !sourceUnchanged.getAsBoolean()
                    || !sameQuantum(Keys.getQuantumCache(actual.getItemMeta()), revised)) {
                    throw new IllegalStateException("Quantum destination credit has no exact live-slot receipt", writeFailure);
                }
                return accepted;
            });
    }

    private static int refuseUnchangedDestination(BlockMenu menu, int slot, ItemStack original) {
        if (!original.equals(menu.getItemInSlot(slot))) {
            throw new IllegalStateException("Quantum destination changed during detached credit preparation");
        }
        return 0;
    }

    private static boolean sameQuantum(QuantumCache actual, QuantumCache expected) {
        return actual != null && actual.getAmountLong() == expected.getAmountLong()
            && actual.getLimitLong() == expected.getLimitLong()
            && actual.isVoidExcess() == expected.isVoidExcess()
            && actual.supportsCustomMaxAmount() == expected.supportsCustomMaxAmount()
            && actual.getItemStack() != null && expected.getItemStack().equals(actual.getItemStack());
    }
}

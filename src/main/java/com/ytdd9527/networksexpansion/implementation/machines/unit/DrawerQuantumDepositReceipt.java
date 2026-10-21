package com.ytdd9527.networksexpansion.implementation.machines.unit;

import com.balugaq.netex.api.data.ItemContainer;
import com.balugaq.netex.api.data.StorageUnitData;
import com.ytdd9527.networksexpansion.utils.itemstacks.ItemStackUtil;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentQuantumStorageType;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.function.BooleanSupplier;

/** Source-slot receipts for the Quantum-to-Drawer GUI transfer. */
final class DrawerQuantumDepositReceipt {
    private DrawerQuantumDepositReceipt() {
    }

    static int transfer(
        BlockMenu menu,
        int sourceSlot,
        Location location,
        StorageUnitData drawer,
        ItemContainer destination,
        ItemStack observedSource,
        BooleanSupplier attached
    ) {
        ItemStack original = observedSource.clone();
        if (original.getAmount() != 1 || !attached.getAsBoolean()
            || !original.equals(menu.getItemInSlot(sourceSlot))) {
            return 0;
        }
        QuantumCache before = Keys.getQuantumCache(original.getItemMeta());
        if (before == null || before.getItemStack() == null || before.getAmountLong() <= 0) {
            return 0;
        }
        ItemStack exactTemplate = before.getItemStack().clone();
        long originalAmount = before.getAmountLong();
        return drawer.depositFromVerifiedSource(location, destination, exactTemplate, originalAmount,
            (moved, destinationUnchanged) -> {
                // Finish serialization/lore on a detached item before touching the
                // physical slot. A callback changing capacity here leaves stock owned
                // by its original Quantum item and cannot cause a partial debit.
                ItemStack updated;
                long remaining = DrawerQuantumTransferMath.remainingAfterDebit(originalAmount, moved);
                try {
                    updated = original.clone();
                    ItemMeta meta = updated.getItemMeta();
                    QuantumCache revised = DrawerQuantumTransferMath.rebalanced(before, remaining);
                    DataTypeMethods.setCustom(meta, Keys.QUANTUM_STORAGE_INSTANCE,
                        PersistentQuantumStorageType.TYPE, revised);
                    revised.updateMetaLore(meta);
                    if (!updated.setItemMeta(meta)) {
                        return refuseUnchangedSource(menu, sourceSlot, original);
                    }
                    QuantumCache prepared = Keys.getQuantumCache(updated.getItemMeta());
                    if (!sameQuantum(prepared, before, exactTemplate, remaining)) {
                        return refuseUnchangedSource(menu, sourceSlot, original);
                    }
                    // CraftInventory rejects some SlimefunItemStack delegates.
                    // Detach only after preparation callbacks and retain exact PDC.
                    updated = ItemStackUtil.getCleanItem(updated);
                    if (updated.getAmount() != 1 || !destinationUnchanged.getAsBoolean()
                        || !attached.getAsBoolean() || !original.equals(menu.getItemInSlot(sourceSlot))) {
                        return refuseUnchangedSource(menu, sourceSlot, original);
                    }
                } catch (RuntimeException | LinkageError preparationFailure) {
                    // No physical write has been attempted. Confirm the source is
                    // still untouched before reporting an ordinary refusal.
                    if (original.equals(menu.getItemInSlot(sourceSlot))) {
                        return false;
                    }
                    throw preparationFailure;
                }

                Throwable writeFailure = null;
                try {
                    menu.replaceExistingItem(sourceSlot, updated);
                } catch (RuntimeException | LinkageError failure) {
                    // The setter may have completed before reporting failure. Its
                    // authoritative readback, not the exception, determines ownership.
                    writeFailure = failure;
                }
                ItemStack actual = menu.getItemInSlot(sourceSlot);
                if (original.equals(actual)) {
                    return false; // A normal rejected write leaves every item at source.
                }
                if (!updated.equals(actual) || !attached.getAsBoolean()
                    || !destinationUnchanged.getAsBoolean()) {
                    throw new IllegalStateException("Quantum source debit could not be verified against its live slot",
                        writeFailure);
                }
                if (!sameQuantum(Keys.getQuantumCache(actual.getItemMeta()),
                    before, exactTemplate, remaining)) {
                    throw new IllegalStateException("Quantum source debit changed its stored item identity or balance");
                }
                return true;
            });
    }

    private static boolean refuseUnchangedSource(BlockMenu menu, int slot, ItemStack original) {
        if (!original.equals(menu.getItemInSlot(slot))) {
            throw new IllegalStateException("Quantum source changed during detached debit preparation");
        }
        return false;
    }

    private static boolean sameQuantum(QuantumCache current, QuantumCache original,
        ItemStack template, long amount) {
        return current != null && current.getAmountLong() == amount
            && current.getLimitLong() == original.getLimitLong()
            && current.isVoidExcess() == original.isVoidExcess()
            && current.supportsCustomMaxAmount() == original.supportsCustomMaxAmount()
            && current.getItemStack() != null && template.equals(current.getItemStack());
    }
}

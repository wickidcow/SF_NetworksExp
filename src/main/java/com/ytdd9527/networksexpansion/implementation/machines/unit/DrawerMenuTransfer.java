package com.ytdd9527.networksexpansion.implementation.machines.unit;

import com.balugaq.netex.api.data.ItemContainer;
import com.balugaq.netex.api.data.StorageUnitData;
import com.ytdd9527.networksexpansion.utils.itemstacks.ItemStackUtil;
import io.github.sefiraat.networks.utils.StackUtils;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Exact physical receipts for drawer GUI transfers; inventory snapshots never own stock. */
final class DrawerMenuTransfer {
    private DrawerMenuTransfer() {
    }

    static int withdraw(StorageUnitData drawer, Location location, ItemContainer source,
        int requested, Player player, BooleanSupplier attached) {
        return drawer.withdrawToVerifiedDestination(location, source, requested, false,
            (offered, sourceUnchanged) -> insert(player.getInventory(), offered, attached, sourceUnchanged));
    }

    static int depositCursor(StorageUnitData drawer, Location location, Player player,
        BooleanSupplier attached) {
        ItemStack live = player.getItemOnCursor();
        if (empty(live) || !attached.getAsBoolean()) return 0;
        ItemStack original = live.clone();
        return drawer.depositFromVerifiedSource(location, original, false, true,
            (consumed, destinationUnchanged) -> debit(player::getItemOnCursor, player::setItemOnCursor,
                original, consumed, attached, destinationUnchanged));
    }

    static int depositInventory(StorageUnitData drawer, Location location, Player player,
        ItemStack template, BooleanSupplier attached) {
        Inventory inventory = player.getInventory();
        ItemStack[] storage = inventory.getStorageContents();
        if (storage == null) return 0;
        int consumed = 0;
        for (int slot = 0; slot < Math.min(storage.length, inventory.getSize()); slot++) {
            if (!attached.getAsBoolean()) break;
            ItemStack live = inventory.getItem(slot);
            if (empty(live) || !StackUtils.itemsMatch(live, template)) continue;
            ItemStack original = live.clone();
            final int sourceSlot = slot;
            consumed += drawer.depositFromVerifiedSource(location, original, true, false,
                (amount, destinationUnchanged) -> debit(() -> inventory.getItem(sourceSlot),
                    updated -> inventory.setItem(sourceSlot, updated), original, amount,
                    attached, destinationUnchanged));
        }
        return consumed;
    }

    private static boolean debit(Supplier<ItemStack> reader, Consumer<ItemStack> writer,
        ItemStack original, int amount, BooleanSupplier attached, BooleanSupplier destinationUnchanged) {
        if (amount <= 0 || amount > original.getAmount()) return false;
        ItemStack updated = amount == original.getAmount() ? null : original.asQuantity(original.getAmount() - amount);
        if (updated != null) updated = ItemStackUtil.getCleanItem(updated);
        if (!attached.getAsBoolean() || !destinationUnchanged.getAsBoolean()
            || !same(original, reader.get())) return false;
        Throwable writeFailure = null;
        try {
            writer.accept(updated);
        } catch (RuntimeException | LinkageError failure) {
            writeFailure = failure;
        }
        ItemStack actual = reader.get();
        if (same(original, actual)) return false;
        if (!same(updated, actual) || !attached.getAsBoolean() || !destinationUnchanged.getAsBoolean()) {
            throw new IllegalStateException("Drawer GUI source debit has no exact live-slot receipt", writeFailure);
        }
        return true;
    }

    private static int insert(Inventory inventory, ItemStack offered,
        BooleanSupplier attached, BooleanSupplier sourceUnchanged) {
        ItemStack[] storage = inventory.getStorageContents();
        if (storage == null || empty(offered)) return 0;
        int size = Math.min(storage.length, inventory.getSize());
        ItemStack[] before = new ItemStack[size];
        ItemStack[] expected = new ItemStack[size];
        for (int slot = 0; slot < size; slot++) {
            ItemStack actual = inventory.getItem(slot);
            before[slot] = empty(actual) ? null : actual.clone();
            expected[slot] = before[slot] == null ? null : before[slot].clone();
        }

        int remaining = offered.getAmount();
        int maximum = Math.min(offered.getMaxStackSize(), inventory.getMaxStackSize());
        if (maximum <= 0) return 0;
        // Preserve partial-stack-first behavior, but prepare only detached values.
        for (int slot = 0; slot < size && remaining > 0; slot++) {
            ItemStack current = expected[slot];
            if (current == null || !current.isSimilar(offered)) continue;
            int add = Math.min(remaining, Math.max(0, Math.min(maximum, current.getMaxStackSize()) - current.getAmount()));
            if (add > 0) {
                current.setAmount(current.getAmount() + add);
                remaining -= add;
            }
        }
        for (int slot = 0; slot < size && remaining > 0; slot++) {
            if (expected[slot] != null) continue;
            int add = Math.min(maximum, remaining);
            expected[slot] = offered.asQuantity(add);
            remaining -= add;
        }
        for (int slot = 0; slot < size; slot++) {
            if (expected[slot] != null) expected[slot] = ItemStackUtil.getCleanItem(expected[slot]);
        }
        if (remaining == offered.getAmount() || !attached.getAsBoolean() || !sourceUnchanged.getAsBoolean()) return 0;
        for (int slot = 0; slot < size; slot++) {
            if (!same(before[slot], inventory.getItem(slot))) return 0;
        }

        Throwable writeFailure = null;
        for (int slot = 0; slot < size; slot++) {
            if (same(before[slot], expected[slot])) continue;
            if (!attached.getAsBoolean() || !sourceUnchanged.getAsBoolean()
                || !same(before[slot], inventory.getItem(slot))) {
                throw new IllegalStateException("Drawer GUI destination changed before its next slot write");
            }
            try {
                inventory.setItem(slot, expected[slot]);
            } catch (RuntimeException | LinkageError failure) {
                writeFailure = failure;
                break;
            }
        }

        int received = 0;
        for (int slot = 0; slot < size; slot++) {
            ItemStack actual = inventory.getItem(slot);
            if (same(before[slot], expected[slot])) {
                if (!same(before[slot], actual)) {
                    throw new IllegalStateException("Drawer GUI changed an unplanned inventory slot", writeFailure);
                }
                continue;
            }
            int originalAmount = empty(before[slot]) ? 0 : before[slot].getAmount();
            int actualAmount = empty(actual) ? 0 : actual.getAmount();
            if ((!empty(actual) && !expected[slot].isSimilar(actual))
                || actualAmount < originalAmount || actualAmount > expected[slot].getAmount()) {
                throw new IllegalStateException("Drawer GUI inventory addition cannot be reconciled", writeFailure);
            }
            received += actualAmount - originalAmount;
        }
        if (!attached.getAsBoolean() || !sourceUnchanged.getAsBoolean()) {
            throw new IllegalStateException("Drawer GUI source ownership changed during inventory write", writeFailure);
        }
        return received;
    }

    private static boolean empty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private static boolean same(ItemStack expected, ItemStack actual) {
        return empty(expected) ? empty(actual) : expected.equals(actual);
    }
}

package com.ytdd9527.networksexpansion.implementation.machines.unit;

import com.balugaq.netex.api.data.ItemContainer;
import com.balugaq.netex.api.data.StorageUnitData;
import com.ytdd9527.networksexpansion.implementation.tools.ItemMover;
import com.ytdd9527.networksexpansion.implementation.tools.ItemMoverStorage;
import com.ytdd9527.networksexpansion.utils.itemstacks.ItemStackUtil;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.function.BooleanSupplier;

/** ItemMover PDC preparation plus authoritative Drawer menu slot receipts. */
final class DrawerItemMoverTransfer {
    private DrawerItemMoverTransfer() {
    }

    static int fromMover(BlockMenu menu, int slot, Location location, StorageUnitData drawer,
        ItemContainer destination, ItemStack observed, BooleanSupplier attached) {
        ItemStack original = ItemMoverStorage.snapshot(observed);
        if (!attached.getAsBoolean() || !same(original, menu.getItemInSlot(slot))) {
            return 0;
        }
        ItemMoverStorage.State before = ItemMoverStorage.inspect(original);
        if (before.template() == null || before.amount() <= 0) {
            return 0;
        }
        return drawer.depositFromVerifiedSource(location, destination, before.template(), before.amount(),
            (moved, destinationUnchanged) -> {
                ItemStack prepared;
                try {
                    prepared = ItemMoverStorage.prepareAmount(original, before.amount() - moved);
                    prepared = finishDisplay(prepared, before.amount() - moved);
                } catch (RuntimeException | LinkageError preparationFailure) {
                    return refuseUnchanged(menu, slot, original, preparationFailure);
                }
                return commitSlot(menu, slot, original, prepared, attached, destinationUnchanged);
            });
    }

    static int toMover(BlockMenu menu, int slot, Location location, StorageUnitData drawer,
        ItemContainer source, ItemStack observed, BooleanSupplier attached) {
        ItemStack original = ItemMoverStorage.snapshot(observed);
        if (!attached.getAsBoolean() || !same(original, menu.getItemInSlot(slot))) {
            return 0;
        }
        ItemMoverStorage.State before = ItemMoverStorage.inspect(original);
        ItemStack template = source.getSample();
        if (before.template() != null && !sameIdentity(before.template(), template)) {
            return 0;
        }
        int requested = (int) Math.min((long) source.getAmount(), Long.MAX_VALUE - before.amount());
        if (requested <= 0) {
            return 0;
        }
        return drawer.withdrawToVerifiedDestination(location, source, requested, false,
            (offered, sourceUnchanged) -> {
                int accepted = offered.getAmount();
                if (accepted <= 0 || accepted > requested || !sameIdentity(template, offered)) {
                    return 0;
                }
                ItemStack prepared;
                try {
                    prepared = ItemMoverStorage.prepareCredit(original, offered, accepted);
                    prepared = finishDisplay(prepared, before.amount() + accepted);
                } catch (RuntimeException | LinkageError preparationFailure) {
                    refuseUnchanged(menu, slot, original, preparationFailure);
                    return 0;
                }
                return commitSlot(menu, slot, original, prepared, attached, sourceUnchanged) ? accepted : 0;
            });
    }

    private static ItemStack finishDisplay(ItemStack prepared, long expectedAmount) {
        ItemMoverStorage.State beforeDisplay = ItemMoverStorage.inspect(prepared);
        ItemMover.updateLore(prepared);
        ItemStack detached = ItemStackUtil.getCleanItem(prepared);
        ItemMoverStorage.State readback = ItemMoverStorage.inspect(detached);
        if (readback.amount() != expectedAmount
            || !same(beforeDisplay.template(), readback.template())) {
            throw new IllegalStateException("Prepared ItemMover display changed its exact stored identity or balance");
        }
        return detached;
    }

    private static boolean commitSlot(BlockMenu menu, int slot, ItemStack original, ItemStack prepared,
        BooleanSupplier attached, BooleanSupplier drawerUnchanged) {
        // Live wrapper metadata reads may call external code. Check the Drawer
        // after that read, immediately before committing the physical slot.
        if (!attached.getAsBoolean() || !same(original, menu.getItemInSlot(slot))
            || !drawerUnchanged.getAsBoolean()) {
            return refuseUnchanged(menu, slot, original, null);
        }
        Throwable writeFailure = null;
        try {
            menu.replaceExistingItem(slot, prepared);
        } catch (RuntimeException | LinkageError failure) {
            writeFailure = failure;
        }
        ItemStack actual = menu.getItemInSlot(slot);
        if (same(original, actual)) {
            return false;
        }
        if (!same(prepared, actual) || !attached.getAsBoolean() || !drawerUnchanged.getAsBoolean()) {
            throw new IllegalStateException("ItemMover transfer has no exact live menu slot receipt", writeFailure);
        }
        return true;
    }

    private static boolean refuseUnchanged(BlockMenu menu, int slot, ItemStack original, Throwable cause) {
        if (!same(original, menu.getItemInSlot(slot))) {
            throw new IllegalStateException("ItemMover slot changed during detached transfer preparation", cause);
        }
        return false;
    }

    private static boolean same(ItemStack first, ItemStack second) {
        return first == second || first != null && second != null
            && first.getAmount() == second.getAmount() && sameIdentity(first, second);
    }

    private static boolean sameIdentity(ItemStack first, ItemStack second) {
        return first != null && second != null && first.getType() == second.getType()
            && java.util.Objects.equals(first.getItemMeta(), second.getItemMeta());
    }
}

package io.github.sefiraat.networks.slimefun.network.grid;

import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Verifies real crafting-grid menu changes rather than trusting a mutable
 * ItemStack returned by a foreign addon or a menu consume/push helper.
 *
 * <p>A failed readback must prevent an output grant. There is no optimistic
 * rollback of already-consumed inputs because third-party setters can
 * write-then-throw without a transaction receipt.</p>
 */
final class GridCraftingCommitGuard {
    private GridCraftingCommitGuard() {
    }

    static @Nullable ItemStack snapshot(@Nullable ItemStack stack) {
        if (empty(stack)) {
            return null;
        }
        final ItemStack cloned = stack.clone();
        if (cloned == stack || cloned.getAmount() != stack.getAmount() || !cloned.isSimilar(stack)) {
            throw new IllegalStateException("A crafting source/output stack could not be copied exactly");
        }
        return cloned;
    }

    static void consumeVerified(@NotNull BlockMenu menu, int slot, @NotNull ItemStack required) {
        final int amount = required.getAmount();
        final ItemStack before = snapshot(menu.getItemInSlot(slot));
        if (amount <= 0 || before == null || before.getAmount() < amount
            || !before.isSimilar(required)) {
            throw new IllegalStateException("Required crafting ingredient changed before consumption");
        }

        // Use the Slimefun menu's own consume handler so supported container
        // replacements and virtual-item behavior remain under core ownership.
        menu.consumeItem(slot, amount, true);
        final ItemStack after = menu.getItemInSlot(slot);
        if (before.getAmount() > amount) {
            if (empty(after)
                || after.getAmount() != before.getAmount() - amount
                || !before.isSimilar(after)) {
                throw new IllegalStateException("Crafting ingredient amount was not debited exactly");
            }
        } else if (!empty(after)) {
            // A completely used ingredient may yield a container item (bucket,
            // bottle, bowl or virtual-item remainder), but the original item
            // itself must never survive in a positive amount.
            if (before.isSimilar(after) || after.getAmount() != 1) {
                throw new IllegalStateException("Fully consumed crafting ingredient still exists");
            }
        }
        menu.markDirty();
    }

    static void verifyExactOutputAddition(
        @NotNull BlockMenu menu,
        int slot,
        @Nullable ItemStack previous,
        @NotNull ItemStack crafted) {
        if (empty(crafted) || crafted.getAmount() <= 0) {
            throw new IllegalStateException("Invalid crafted output");
        }

        final ItemStack after = menu.getItemInSlot(slot);
        if (empty(after) || !crafted.isSimilar(after)) {
            throw new IllegalStateException("Crafting output did not reach its intended slot");
        }

        final int originalAmount;
        if (empty(previous)) {
            originalAmount = 0;
        } else {
            if (!previous.isSimilar(after)) {
                throw new IllegalStateException("Existing crafting output identity was replaced");
            }
            originalAmount = previous.getAmount();
        }
        if ((long) originalAmount + crafted.getAmount() != after.getAmount()) {
            throw new IllegalStateException("Crafting output did not gain exactly the produced amount");
        }
    }

    private static boolean empty(@Nullable ItemStack item) {
        return item == null || item.getType() == Material.AIR || item.getAmount() <= 0;
    }
}

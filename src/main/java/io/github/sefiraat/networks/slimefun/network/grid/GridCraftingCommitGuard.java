package io.github.sefiraat.networks.slimefun.network.grid;

import io.github.sefiraat.networks.utils.StackUtils;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Readbacks for one manual crafting transaction. No live inventory snapshots
 * are retained between operations. An ambiguous write never authorizes a refund.
 */
final class GridCraftingCommitGuard {
    private static final Set<BlockMenu> ACTIVE = ConcurrentHashMap.newKeySet();

    private GridCraftingCommitGuard() {
    }

    /** Reject same-menu recursive crafting from event/consume callbacks. */
    static boolean tryEnter(@NotNull BlockMenu menu) {
        return ACTIVE.add(menu);
    }

    static void exit(@NotNull BlockMenu menu) {
        ACTIVE.remove(menu);
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

    /**
     * Preflight every slot before consuming the first item, then verify the
     * entire resulting matrix. A later slot's callback may have changed an
     * earlier slot even though that earlier debit initially looked correct.
     */
    static @NotNull ItemStack[] consumeVerified(
        @NotNull BlockMenu menu, int @NotNull [] slots, ItemStack @NotNull [] recipe) {
        if (recipe.length > slots.length) {
            throw new IllegalStateException("Recipe exceeds crafting matrix");
        }
        final ItemStack[] before = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            if (slots[i] < 0 || slots[i] >= menu.getSize()) {
                throw new IllegalStateException("Invalid crafting ingredient slot");
            }
            for (int j = 0; j < i; j++) {
                if (slots[i] == slots[j]) {
                    throw new IllegalStateException("Duplicate crafting ingredient slot");
                }
            }
            before[i] = snapshot(menu.getItemInSlot(slots[i]));
            final ItemStack required = i < recipe.length ? recipe[i] : null;
            if (empty(required)) {
                if (!empty(before[i])) {
                    throw new IllegalStateException("Unexpected item in an empty recipe slot");
                }
            } else {
                requireIngredient(before[i], required);
            }
        }

        final ItemStack[] after = new ItemStack[slots.length];
        for (int i = 0; i < slots.length; i++) {
            verifyUnchanged(menu, slots[i], before[i]);
            final ItemStack required = i < recipe.length ? recipe[i] : null;
            if (!empty(required)) {
                after[i] = consumeVerified(menu, slots[i], required);
            }
        }
        verifyRecipeRemainders(menu, slots, after);
        return after;
    }

    static @Nullable ItemStack consumeVerified(
        @NotNull BlockMenu menu, int slot, @NotNull ItemStack required) {
        final int amount = required.getAmount();
        final ItemStack before = snapshot(menu.getItemInSlot(slot));
        requireIngredient(before, required);

        // Container replacement stays under the supported Slimefun core's
        // consume handler. A virtual ingredient which cannot prove a debit
        // fails closed rather than being treated as a free crafting catalyst.
        menu.consumeItem(slot, amount, true);
        final ItemStack after = snapshot(menu.getItemInSlot(slot));
        if (before.getAmount() > amount) {
            if (empty(after)
                || after.getAmount() != before.getAmount() - amount
                || !before.isSimilar(after)) {
                throw new IllegalStateException("Crafting ingredient amount was not debited exactly");
            }
        } else if (!empty(after)) {
            if (before.isSimilar(after) || after.getAmount() != 1) {
                throw new IllegalStateException("Fully consumed crafting ingredient still exists");
            }
        }
        menu.markDirty();
        verifyUnchanged(menu, slot, after);
        return after;
    }

    private static void requireIngredient(@Nullable ItemStack supplied, @NotNull ItemStack required) {
        // Eligibility must match NetworkCraftingGrid.hasExactIngredients and
        // the existing recipe engine. Receipt checks use the actual physical
        // source snapshot, not the possibly less-strict recipe template.
        if (required.getAmount() <= 0 || empty(supplied)
            || supplied.getAmount() < required.getAmount() || !StackUtils.itemsMatch(supplied, required)) {
            throw new IllegalStateException("Required crafting ingredient changed before consumption");
        }
    }

    static void verifyRecipeRemainders(
        @NotNull BlockMenu menu, int @NotNull [] slots, ItemStack @NotNull [] expected) {
        if (slots.length != expected.length) {
            throw new IllegalStateException("Crafting matrix readback size changed");
        }
        for (int i = 0; i < slots.length; i++) {
            verifyUnchanged(menu, slots[i], expected[i]);
        }
    }

    static void verifyUnchanged(@NotNull BlockMenu menu, int slot, @Nullable ItemStack expected) {
        final ItemStack current = menu.getItemInSlot(slot);
        if (empty(expected)) {
            if (!empty(current)) {
                throw new IllegalStateException("Empty crafting slot changed during the transaction");
            }
        } else if (empty(current) || current.getAmount() != expected.getAmount()
            || !expected.isSimilar(current)) {
            throw new IllegalStateException("Crafting slot changed during the transaction");
        }
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

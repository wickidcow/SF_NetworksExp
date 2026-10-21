package com.ytdd9527.networksexpansion.implementation.tools;

import com.jeff_media.morepersistentdatatypes.DataType;
import io.github.sefiraat.networks.utils.Keys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

/** Exact ItemMover PDC preparation and receipts. Existing storage keys remain unchanged. */
public final class ItemMoverStorage {
    private static final Set<ItemStack> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final NamespacedKey PENDING = key("item_mover_pending_transfer");
    private static final NamespacedKey DIRECTION = key("item_mover_pending_direction");

    private ItemMoverStorage() {}

    private static NamespacedKey key(String name) {
        return new NamespacedKey(Keys.ITEM_MOVER_ITEM.getNamespace(), name);
    }

    public record State(@Nullable ItemStack template, long amount) {}

    /** Malformed or unresolved balances are preserved and cannot participate in transfers. */
    public static @NotNull State inspect(@NotNull ItemStack mover) {
        if (mover.getType() != Material.DEBUG_STICK || mover.getAmount() != 1 || !mover.hasItemMeta()) {
            throw new IllegalArgumentException("ItemMover must be one item with metadata");
        }
        ItemMeta meta = mover.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("ItemMover metadata is missing");
        PersistentDataContainer data = meta.getPersistentDataContainer();
        if (data.has(PENDING) || data.has(DIRECTION)) throw new IllegalStateException("ItemMover has an unresolved transfer receipt");
        checkType(data, Keys.ITEM_MOVER_ITEM, DataType.ITEM_STACK);
        checkType(data, Keys.ITEM_MOVER_AMOUNT, PersistentDataType.INTEGER);
        checkType(data, Keys.ITEM_MOVER_AMOUNT_LONG, PersistentDataType.LONG);
        Integer legacy = data.get(Keys.ITEM_MOVER_AMOUNT, PersistentDataType.INTEGER);
        Long modern = data.get(Keys.ITEM_MOVER_AMOUNT_LONG, PersistentDataType.LONG);
        if (legacy != null && modern != null && legacy.longValue() != modern.longValue()) {
            throw new IllegalStateException("ItemMover contains conflicting legacy and long balances");
        }
        long amount = legacy == null ? modern == null ? 0 : modern : legacy;
        ItemStack template = data.get(Keys.ITEM_MOVER_ITEM, DataType.ITEM_STACK);
        if (amount < 0 || template != null && (template.getType().isAir() || template.getAmount() != 1)
            || amount > 0 && template == null) {
            throw new IllegalStateException("ItemMover balance has no exact item identity");
        }
        return new State(template == null ? null : snapshot(template), amount);
    }

    private static <P, C> void checkType(PersistentDataContainer data, NamespacedKey key,
                                        PersistentDataType<P, C> type) {
        if (data.has(key) && !data.has(key, type)) {
            throw new IllegalStateException("ItemMover storage key has an unexpected type: " + key);
        }
    }

    public static boolean hasPendingTransfer(@NotNull ItemStack mover) {
        ItemMeta meta = mover.getItemMeta();
        return meta != null && (meta.getPersistentDataContainer().has(PENDING)
            || meta.getPersistentDataContainer().has(DIRECTION));
    }

    public record PendingReceipt(ItemStack original, ItemStack prepared, ItemStack offered, String direction) {}

    /** Read-only recovery evidence. This method never decides whether an external side committed. */
    public static @Nullable PendingReceipt pendingReceipt(@NotNull ItemStack mover) {
        ItemMeta meta = mover.getItemMeta();
        if (meta == null || !hasPendingTransfer(mover)) return null;
        var data = meta.getPersistentDataContainer();
        ItemStack[] snapshots = data.get(PENDING, DataType.ITEM_STACK_ARRAY);
        String direction = data.get(DIRECTION, PersistentDataType.STRING);
        if (snapshots == null || snapshots.length != 3 || snapshots[0] == null || snapshots[1] == null
            || snapshots[2] == null || direction == null || !direction.startsWith("1:")) {
            throw new IllegalStateException("ItemMover transfer receipt cannot be decoded exactly");
        }
        return new PendingReceipt(snapshots[0], snapshots[1], snapshots[2], direction.substring(2));
    }

    /** Prepares a detached debit or balance update. It does not mutate the authoritative item. */
    public static @NotNull ItemStack prepareAmount(@NotNull ItemStack source, long amount) {
        State state = inspect(source);
        if (amount < 0 || amount > 0 && state.template() == null) {
            throw new IllegalArgumentException("ItemMover amount requires an exact existing identity");
        }
        return prepare(source, state.template(), amount, false);
    }

    /** Prepares one complete identity and balance update before either endpoint is changed. */
    public static @NotNull ItemStack prepareCredit(@NotNull ItemStack source, @NotNull ItemStack incoming,
                                                  long amount) {
        State state = inspect(source);
        if (incoming.getType().isAir() || incoming.getAmount() <= 0 || amount <= 0
            || amount > Long.MAX_VALUE - state.amount()) {
            throw new IllegalArgumentException("ItemMover credit is outside its exact balance range");
        }
        ItemStack template = snapshot(incoming);
        template.setAmount(1);
        if (state.template() != null && !same(state.template(), template)) {
            throw new IllegalArgumentException("ItemMover credit has a different item identity");
        }
        return prepare(source, template, state.amount() + amount, false);
    }

    /** Empty-state cleanup and lore are applied to the same detached metadata snapshot. */
    @SuppressWarnings("deprecation")
    public static @NotNull ItemStack prepareLore(@NotNull ItemStack source, @NotNull List<String> lore) {
        State state = inspect(source);
        ItemStack prepared = state.amount() == 0 ? prepareAmount(source, 0) : snapshot(source);
        ItemMeta meta = prepared.getItemMeta();
        meta.setLore(lore);
        prepared.setItemMeta(meta);
        if (!meta.equals(prepared.getItemMeta())) {
            throw new IllegalStateException("Prepared ItemMover lore did not retain its exact metadata");
        }
        return prepared;
    }

    /** Compatibility setter preparation; an assigned positive balance cannot be relabelled. */
    static @NotNull ItemStack prepareTemplate(@NotNull ItemStack source, @Nullable ItemStack template) {
        State state = inspect(source);
        if (template == null || template.getType().isAir() || template.getAmount() != 1) {
            if (state.amount() > 0) throw new IllegalStateException("Cannot erase an occupied ItemMover identity");
            return prepare(source, null, 0, false);
        }
        if (state.amount() > 0 && !same(template, state.template())) {
            throw new IllegalStateException("Cannot relabel an occupied ItemMover identity");
        }
        return prepare(source, template, state.amount(), true);
    }

    private static ItemStack prepare(ItemStack source, ItemStack template, long amount, boolean keepEmptyTemplate) {
        ItemStack candidate = snapshot(source);
        ItemMeta meta = source.getItemMeta();
        if (meta == null) throw new IllegalArgumentException("ItemMover metadata is missing");
        PersistentDataContainer data = meta.getPersistentDataContainer();
        data.remove(Keys.ITEM_MOVER_AMOUNT);
        if (amount == 0 && !keepEmptyTemplate) {
            data.remove(Keys.ITEM_MOVER_ITEM);
            data.remove(Keys.ITEM_MOVER_AMOUNT_LONG);
        } else {
            if (template == null) throw new IllegalArgumentException("ItemMover template is missing");
            data.set(Keys.ITEM_MOVER_ITEM, DataType.ITEM_STACK, snapshot(template));
            data.set(Keys.ITEM_MOVER_AMOUNT_LONG, PersistentDataType.LONG, amount);
        }
        candidate.setItemMeta(meta);
        State readback = inspect(candidate);
        if (!meta.equals(candidate.getItemMeta()) || readback.amount() != amount
            || (amount > 0 || keepEmptyTemplate) && !same(template, readback.template())) {
            throw new IllegalStateException("Prepared ItemMover metadata did not round-trip exactly");
        }
        return candidate;
    }

    /**
     * Commits to this ItemStack only. Inventory callers must separately set and read back
     * their authoritative slot. A rejected unchanged write returns false; unknown writes throw.
     */
    public static boolean commit(@NotNull ItemStack target, @NotNull ItemStack expected,
                                 @NotNull ItemStack prepared) {
        if (!begin(target)) return false;
        try { return commitOwned(target, expected, prepared); }
        finally { end(target); }
    }

    private static boolean commitOwned(ItemStack target, ItemStack expected, ItemStack prepared) {
        if (!same(target, expected)) return false;
        if (target.getType() != prepared.getType() || target.getAmount() != prepared.getAmount()) {
            throw new IllegalArgumentException("ItemMover metadata commit cannot replace its physical item");
        }
        Throwable failure = null;
        try { target.setItemMeta(prepared.getItemMeta()); }
        catch (RuntimeException | LinkageError error) { failure = error; }
        // The actual metadata is authoritative, including write-then-throw and false-return setters.
        if (same(target, prepared)) return true;
        if (same(target, expected)) return false;
        throw new IllegalStateException("ItemMover metadata write has no exact receipt", failure);
    }

    /** An authoritative inventory endpoint; detached event items alone cannot authorize a transfer. */
    public static boolean commitSlot(@NotNull Supplier<ItemStack> reader, @NotNull Consumer<ItemStack> writer,
                                     @NotNull ItemStack expected, @NotNull ItemStack prepared) {
        if (!same(expected, reader.get())) return false;
        Throwable failure = null;
        try { writer.accept(snapshot(prepared)); }
        catch (RuntimeException | LinkageError error) { failure = error; }
        ItemStack actual = reader.get();
        if (same(prepared, actual)) return true;
        if (same(expected, actual)) return false;
        throw new IllegalStateException("ItemMover authoritative slot write has no exact receipt", failure);
    }

    /** Existing physical-stack deposit, with a single complete PDC write before consumption. */
    public static int deposit(@NotNull ItemStack mover, @Nullable ItemStack incoming) {
        if (incoming == null || incoming.getType().isAir() || incoming.getAmount() <= 0 || mover == incoming
            || !begin(mover, incoming)) return 0;
        try {
            ItemStack before = snapshot(mover);
            ItemStack offered = snapshot(incoming);
            State state = inspect(before);
            int accepted = (int) Math.min(Long.MAX_VALUE - state.amount(), offered.getAmount());
            if (accepted <= 0 || state.template() != null && !sameIdentity(state.template(), offered)) return 0;
            ItemStack credited = prepareCredit(before, offered, accepted);
            ItemStack reserved = pending(before, credited, offered, "physical-input");
            if (!same(incoming, offered) || !commitOwned(mover, before, reserved)) return 0;
            if (!same(incoming, offered)) {
                restore(mover, reserved, before);
                throw new IllegalStateException("ItemMover physical source changed during metadata commit");
            }
            Throwable failure = null;
            try { incoming.setAmount(offered.getAmount() - accepted); }
            catch (RuntimeException | LinkageError error) { failure = error; }
            if (isRemainder(incoming, offered, offered.getAmount() - accepted)) {
                finish(mover, reserved, credited);
                return accepted;
            }
            if (same(incoming, offered)) {
                restore(mover, reserved, before);
                return 0;
            }
            throw new IllegalStateException("ItemMover physical source debit has no exact receipt", failure);
        } finally { end(mover, incoming); }
    }

    /**
     * The destination receives a detached reserved stack. Only a normal, exact remainder
     * releases the reservation; thrown or malformed outcomes retain the durable receipt.
     */
    public static int transferTo(@NotNull ItemStack mover, int maximum,
                                 @NotNull Consumer<ItemStack> destination) {
        if (maximum <= 0 || !begin(mover)) return 0;
        try {
            return transferToOwned(mover, maximum, destination,
                (expected, prepared) -> commitOwned(mover, expected, prepared));
        } finally { end(mover); }
    }

    public static int transferToSlot(@NotNull ItemStack expected, @NotNull Supplier<ItemStack> reader,
                                     @NotNull Consumer<ItemStack> writer, int maximum,
                                     @NotNull Consumer<ItemStack> destination) {
        if (maximum <= 0 || !begin(expected)) return 0;
        try {
            return transferToOwned(expected, maximum, destination,
                (before, prepared) -> commitSlot(reader, writer, before, prepared));
        } finally { end(expected); }
    }

    private static int transferToOwned(ItemStack mover, int maximum, Consumer<ItemStack> destination, Commit commit) {
        ItemStack before = snapshot(mover);
        State state = inspect(before);
        int planned = (int) Math.min(state.amount(), maximum);
        if (planned <= 0) return 0;
        ItemStack offered = snapshot(state.template());
        offered.setAmount(planned);
        ItemStack expectedOffer = snapshot(offered);
        ItemStack debited = prepareAmount(before, state.amount() - planned);
        ItemStack reserved = pending(before, debited, offered, "external-output");
        if (!commit.write(before, reserved)) return 0;
        destination.accept(offered);
        int left = empty(offered) ? 0 : offered.getAmount();
        if (left < 0 || left > planned || !isRemainder(offered, expectedOffer, left)) {
            throw new IllegalStateException("ItemMover destination returned an invalid remainder");
        }
        int accepted = planned - left;
        ItemStack settled = accepted == 0 ? before : prepareAmount(before, state.amount() - accepted);
        if (!commit.write(reserved, settled)) {
            throw new IllegalStateException("ItemMover transfer remains reserved because settlement was not verified");
        }
        return accepted;
    }

    /**
     * Reserves a durable receipt before calling an existing withdrawal API. A valid returned
     * stack is credited exactly once. Null, wrong identity or thrown results remain unresolved;
     * they are never compensated by an unverified second deposit.
     */
    public static int transferFrom(@NotNull ItemStack mover, @NotNull ItemStack template, int maximum,
                                   @NotNull IntFunction<ItemStack> withdrawal) {
        if (maximum <= 0 || !begin(mover)) return 0;
        try {
            return transferFromOwned(mover, template, maximum, withdrawal,
                (expected, prepared) -> commitOwned(mover, expected, prepared));
        } finally { end(mover); }
    }

    public static int transferFromSlot(@NotNull ItemStack expected, @NotNull Supplier<ItemStack> reader,
                                       @NotNull Consumer<ItemStack> writer, @NotNull ItemStack template, int maximum,
                                       @NotNull IntFunction<ItemStack> withdrawal) {
        if (maximum <= 0 || !begin(expected)) return 0;
        try {
            return transferFromOwned(expected, template, maximum, withdrawal,
                (before, prepared) -> commitSlot(reader, writer, before, prepared));
        } finally { end(expected); }
    }

    private static int transferFromOwned(ItemStack mover, ItemStack template, int maximum,
                                         IntFunction<ItemStack> withdrawal, Commit commit) {
        ItemStack before = snapshot(mover);
        State state = inspect(before);
        int planned = (int) Math.min(maximum, Long.MAX_VALUE - state.amount());
        if (planned <= 0 || state.template() != null && !sameIdentity(state.template(), template)) return 0;
        ItemStack offered = snapshot(template);
        offered.setAmount(planned);
        ItemStack credited = prepareCredit(before, offered, planned);
        ItemStack reserved = pending(before, before, offered, "external-input");
        if (!commit.write(before, reserved)) return 0;
        ItemStack received = withdrawal.apply(planned);
        if (received == null || received.getType().isAir() || received.getAmount() <= 0
            || received.getAmount() > planned || !sameIdentity(offered, received)) {
            throw new IllegalStateException("ItemMover withdrawal returned no exact item receipt");
        }
        int accepted = received.getAmount();
        if (accepted != planned) credited = prepareCredit(before, received, accepted);
        if (!commit.write(reserved, credited)) {
            throw new IllegalStateException("ItemMover transfer remains reserved because credit was not verified");
        }
        return accepted;
    }

    @FunctionalInterface private interface Commit { boolean write(ItemStack expected, ItemStack prepared); }

    private static ItemStack pending(ItemStack before, ItemStack prepared, ItemStack offered, String direction) {
        ItemStack reserved = snapshot(prepared);
        ItemMeta meta = reserved.getItemMeta();
        // Reuse the existing Bukkit item serializer in one byte-array receipt. Keeping
        // original/prepared/offered together also avoids a partially populated receipt.
        // Some Slimefun wrappers preserve their subclass on clone but have no Bukkit
        // deserializer. Standard copies retain their live data without serializing an
        // unknown wrapper alias. Never substitute a registered/default item template.
        byte[] encoded = DataType.ITEM_STACK_ARRAY.toPrimitive(new ItemStack[] {
            snapshot(before), snapshot(prepared), snapshot(offered)},
            meta.getPersistentDataContainer().getAdapterContext());
        meta.getPersistentDataContainer().set(PENDING, PersistentDataType.BYTE_ARRAY, encoded);
        meta.getPersistentDataContainer().set(DIRECTION, PersistentDataType.STRING, "1:" + direction);
        reserved.setItemMeta(meta);
        PersistentDataContainer actual = reserved.getItemMeta().getPersistentDataContainer();
        if (!meta.equals(reserved.getItemMeta())
            || !Arrays.equals(encoded, actual.get(PENDING, PersistentDataType.BYTE_ARRAY))
            || !("1:" + direction).equals(actual.get(DIRECTION, PersistentDataType.STRING))) {
            throw new IllegalStateException("Prepared ItemMover transfer receipt did not retain its exact serialized bytes");
        }
        // Prove this runtime can decode the complete receipt before touching an endpoint.
        if (pendingReceipt(reserved) == null) throw new IllegalStateException("Prepared ItemMover receipt is absent");
        return reserved;
    }

    /** Exact plain snapshot; wrapper clone/copy constructors may rebuild live model data. */
    public static @NotNull ItemStack snapshot(@NotNull ItemStack source) {
        ItemStack copy = new ItemStack(source.getType(), source.getAmount());
        if (source.hasItemMeta() && !copy.setItemMeta(source.getItemMeta())) {
            throw new IllegalStateException("ItemMover receipt metadata could not be copied exactly");
        }
        if (!same(source, copy)) {
            throw new IllegalStateException("ItemMover receipt copy changed its item data");
        }
        return copy;
    }

    // Paper's cross-wrapper ItemStack.equals may cast a Slimefun wrapper to
    // CraftItemStack. Compare the complete Bukkit fields without that cast.
    private static boolean same(ItemStack first, ItemStack second) {
        return first == second || first != null && second != null
            && first.getAmount() == second.getAmount() && sameIdentity(first, second);
    }

    private static boolean sameIdentity(ItemStack first, ItemStack second) {
        return first != null && second != null && first.getType() == second.getType()
            && java.util.Objects.equals(first.getItemMeta(), second.getItemMeta());
    }

    private static void finish(ItemStack mover, ItemStack reserved, ItemStack settled) {
        if (!commitOwned(mover, reserved, settled)) {
            throw new IllegalStateException("ItemMover transfer remains reserved because settlement was not verified");
        }
    }

    private static void restore(ItemStack mover, ItemStack reserved, ItemStack original) {
        if (!commitOwned(mover, reserved, original)) {
            throw new IllegalStateException("ItemMover transfer remains reserved because rollback was not verified");
        }
    }

    private static boolean isRemainder(ItemStack current, ItemStack offered, int amount) {
        if (amount == 0) return empty(current);
        return current.getAmount() == amount && sameIdentity(current, offered);
    }

    private static boolean empty(ItemStack stack) {
        return stack.getType().isAir() || stack.getAmount() == 0;
    }

    private static boolean begin(ItemStack... stacks) {
        synchronized (ACTIVE) {
            for (ItemStack stack : stacks) if (ACTIVE.contains(stack)) return false;
            Collections.addAll(ACTIVE, stacks);
            return true;
        }
    }

    private static void end(ItemStack... stacks) {
        synchronized (ACTIVE) { for (ItemStack stack : stacks) ACTIVE.remove(stack); }
    }
}

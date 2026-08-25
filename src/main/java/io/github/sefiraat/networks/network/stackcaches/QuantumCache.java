package io.github.sefiraat.networks.network.stackcaches;

import com.balugaq.netex.utils.Lang;
import io.github.sefiraat.networks.utils.DisplayNameUtils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.util.ArrayList;
import java.util.List;

/** Thread-safe amount and stored-item state for Network Quantum Storage. */
@SuppressWarnings("deprecation")
public class QuantumCache extends ItemStackCache {

    @Nullable
    private final ItemMeta storedItemMeta;
    private final boolean supportsCustomMaxAmount;

    private volatile long limit;
    private volatile long amount;

    @Setter
    @Getter
    private volatile boolean voidExcess;

    public QuantumCache(
        @Nullable ItemStack storedItem,
        long amount,
        int limit,
        boolean voidExcess,
        boolean supportsCustomMaxAmount) {
        this(storedItem, amount, (long) limit, voidExcess, supportsCustomMaxAmount);
    }

    public QuantumCache(
        @Nullable ItemStack storedItem,
        long amount,
        long limit,
        boolean voidExcess,
        boolean supportsCustomMaxAmount) {
        super(storedItem);
        this.storedItemMeta = storedItem == null ? null : storedItem.getItemMeta();
        final long repairedAmount = repairLegacyNegative(amount);
        // Never truncate persisted contents merely because an older custom limit is malformed or too small.
        this.limit = Math.max(Math.max(1L, limit), repairedAmount);
        this.amount = clampAmount(repairedAmount, this.limit);
        this.voidExcess = voidExcess;
        this.supportsCustomMaxAmount = supportsCustomMaxAmount;
    }

    public int getLimit() {
        return limit > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) limit;
    }

    public long getLimitLong() {
        return limit;
    }

    public synchronized void setLimit(long newLimit) {
        // Lowering a configured limit must never delete already stored items.
        this.limit = Math.max(Math.max(1L, newLimit), this.amount);
    }

    public int getAmountInt() {
        return amount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) amount;
    }

    public long getAmountLong() {
        return amount;
    }

    public synchronized void setAmount(int newAmount) {
        setAmount((long) newAmount);
    }

    public synchronized void setAmount(long newAmount) {
        this.amount = clampAmount(repairLegacyNegative(newAmount), this.limit);
    }

    /**
     * Adds an incoming amount and returns the unaccepted remainder. In void mode the excess is intentionally
     * consumed and therefore the remainder is zero.
     */
    public synchronized int increaseAmount(int incoming) {
        if (incoming <= 0) {
            return 0;
        }

        final long capacity = Math.max(0L, this.limit - this.amount);
        final int accepted = (int) Math.min((long) incoming, capacity);
        this.amount += accepted;
        return this.voidExcess ? 0 : incoming - accepted;
    }

    /**
     * Restores a previously withdrawn amount without applying void-excess semantics.
     *
     * @return any amount that could not be restored
     */
    public synchronized int restoreAmount(int restored) {
        if (restored <= 0) {
            return 0;
        }
        final long capacity = Math.max(0L, this.limit - this.amount);
        final int accepted = (int) Math.min((long) restored, capacity);
        this.amount += accepted;
        return restored - accepted;
    }

    public synchronized void reduceAmount(int removed) {
        if (removed <= 0) {
            return;
        }
        this.amount = Math.max(0L, this.amount - removed);
    }

    @Nullable
    public synchronized ItemStack withdrawItem(int requested) {
        if (requested <= 0 || this.amount <= 0 || this.getItemStack() == null) {
            return null;
        }

        final int withdrawn = (int) Math.min(this.amount, (long) requested);
        if (withdrawn <= 0) {
            return null;
        }

        final ItemStack clone = this.getItemStack().clone();
        clone.setAmount(withdrawn);
        this.amount -= withdrawn;
        return clone;
    }

    @Nullable
    public synchronized ItemStack withdrawItem() {
        final ItemStack stored = this.getItemStack();
        return stored == null ? null : withdrawItem(stored.getMaxStackSize());
    }

    @Override
    @Nullable
    public synchronized ItemStack getItemStack() {
        return super.getItemStack();
    }

    @Override
    public synchronized void setItemStack(@Nullable ItemStack itemStack) {
        super.setItemStack(itemStack);
    }

    @Nullable
    public ItemMeta getStoredItemMeta() {
        return this.storedItemMeta;
    }

    public boolean supportsCustomMaxAmount() {
        return this.supportsCustomMaxAmount;
    }

    public void addMetaLore(@NotNull ItemMeta itemMeta) {
        List<String> old = itemMeta.getLore();
        final List<String> lore = old != null ? new ArrayList<>(old) : new ArrayList<>();
        lore.add("");
        lore.add(storedItemLine());
        lore.add(storedAmountLine());
        if (this.supportsCustomMaxAmount) {
            lore.add(customLimitLine());
        }
        itemMeta.setLore(lore);
    }

    /** Updates historical cache lore defensively, even when an older item has missing lines. */
    public void updateMetaLore(@NotNull ItemMeta itemMeta) {
        final List<String> existing = itemMeta.hasLore() && itemMeta.getLore() != null
            ? new ArrayList<>(itemMeta.getLore())
            : new ArrayList<>();
        final int requiredTail = this.supportsCustomMaxAmount ? 3 : 2;
        while (existing.size() < requiredTail) {
            existing.add("");
        }

        final int base = existing.size() - requiredTail;
        existing.set(base, storedItemLine());
        existing.set(base + 1, storedAmountLine());
        if (this.supportsCustomMaxAmount) {
            existing.set(base + 2, customLimitLine());
        }
        itemMeta.setLore(existing);
    }

    private @NotNull String storedItemLine() {
        String itemName = Lang.getString("messages.normal-operation.quantum_cache.empty");
        if (getItemStack() != null) {
            itemName = DisplayNameUtils.getDisplayName(getItemStack());
        }
        return String.format(Lang.getString("messages.normal-operation.quantum_cache.stored_item"), itemName);
    }

    private @NotNull String storedAmountLine() {
        return String.format(
            Lang.getString("messages.normal-operation.quantum_cache.stored_amount"), getAmountLong());
    }

    private @NotNull String customLimitLine() {
        return String.format(
            Lang.getString("messages.normal-operation.quantum_cache.custom_max_limit"), getLimitLong());
    }

    private static long repairLegacyNegative(long value) {
        if (value < -2_000_000_000L) {
            return value == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(value);
        }
        return Math.max(0L, value);
    }

    private static long clampAmount(long value, long limit) {
        return Math.max(0L, Math.min(value, Math.max(1L, limit)));
    }

    @Nullable
    public ItemStack getItemStack() {
        return super.getItemStack();
    }

    @Override
    public synchronized void setItemStack(ItemStack itemStack) {
        super.setItemStack(itemStack);
    }
}

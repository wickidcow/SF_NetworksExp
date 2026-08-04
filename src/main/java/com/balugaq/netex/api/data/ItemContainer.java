package com.balugaq.netex.api.data;

import io.github.sefiraat.networks.network.stackcaches.ItemStackCache;
import io.github.sefiraat.networks.utils.StackUtils;
import lombok.Getter;
import lombok.ToString;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@Getter
@ToString
public class ItemContainer extends ItemStackCache {

    private final int id;

    private volatile int amount;

    public ItemContainer(int id, @NotNull ItemStack item, int amount) {
        super(StackUtils.getAsQuantity(item, 1));
        this.id = id;
        this.amount = amount;
    }

    public @NotNull ItemStack getSample() {
        return itemStack.clone();
    }

    public @NotNull ItemStack getSampleDirectly() {
        return itemStack;
    }

    public boolean isSimilar(ItemStack other) {
        return StackUtils.itemsMatch(this, other);
    }

    public synchronized void setAmount(int amount) {
        this.amount = Math.max(0, amount);
    }

    public synchronized void addAmount(int amount) {
        if (amount > 0) {
            this.amount += amount;
        }
    }

    /**
     * Remove specific amount from container
     *
     * @param amount: amount will be removed
     * @return amount that actual removed
     */
    public synchronized int removeAmount(int amount) {
        if (amount <= 0 || this.amount <= 0) {
            return 0;
        }
        final int removed = Math.min(this.amount, amount);
        this.amount -= removed;
        return removed;
    }
}

package com.balugaq.netex.api.data;

import io.github.sefiraat.networks.network.stackcaches.ItemStackCache;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.core.attributes.DistinctiveItem;
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
        super(canonicalizeSample(item));
        this.id = id;
        this.amount = amount;
    }

    /**
     * Cargo storage persists the exact incoming ItemStack, but runtime matching should follow Slimefun's
     * identity contract. Ordinary Slimefun items are therefore represented by their current registered
     * template. This keeps recipe/blueprint requests stable across Paper data-component changes and addon
     * rebuilds. Stateful items opt into DistinctiveItem and retain their exact instance metadata.
     */
    private static @NotNull ItemStack canonicalizeSample(@NotNull ItemStack item) {
        final SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
        if (slimefunItem != null && !(slimefunItem instanceof DistinctiveItem)) {
            return StackUtils.getAsQuantity(slimefunItem.getItem(), 1);
        }
        return StackUtils.getAsQuantity(item, 1);
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

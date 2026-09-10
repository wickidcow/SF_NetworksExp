package io.github.sefiraat.networks.internal.ie1link;

import org.bukkit.inventory.ItemStack;

/** Inert companion type for relocated obsolete IE1 StorageCache descriptors. */
@Deprecated
public abstract class StorageCache {

    public abstract void depositAll(ItemStack[] itemStacks, boolean observeVoiding);
}

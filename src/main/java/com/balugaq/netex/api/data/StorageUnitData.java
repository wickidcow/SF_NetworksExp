package com.balugaq.netex.api.data;

import com.balugaq.netex.api.enums.StorageUnitType;
import com.ytdd9527.networksexpansion.implementation.machines.unit.NetworksDrawer;
import com.ytdd9527.networksexpansion.utils.databases.DataStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.utils.StackUtils;
import lombok.Getter;
import lombok.ToString;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@SuppressWarnings({"UnusedAssignment", "DuplicatedCode"})
@ToString
public class StorageUnitData {
    public static final Map<Location, Map<Integer, Integer /* Access times */>> observingAccessHistory =
        new ConcurrentHashMap<>();
    public static final Map<Location, Map<Integer, Integer /* Cache miss times */>> persistentAccessHistory =
        new ConcurrentHashMap<>();

    @Getter
    private final int id;

    @Getter
    private final OfflinePlayer owner;

    private final ConcurrentHashMap<Integer, ItemContainer> storedItems;
    @Getter
    private boolean isPlaced;

    @Getter
    private StorageUnitType sizeType;

    @Getter
    private Location lastLocation;

    public StorageUnitData(
        int id, @NotNull String ownerUUID, StorageUnitType sizeType, boolean isPlaced, Location lastLocation) {
        this(
            id,
            Bukkit.getOfflinePlayer(UUID.fromString(ownerUUID)),
            sizeType,
            isPlaced,
            lastLocation,
            new ConcurrentHashMap<>());
    }

    public StorageUnitData(
        int id,
        @NotNull String ownerUUID,
        StorageUnitType sizeType,
        boolean isPlaced,
        Location lastLocation,
        ConcurrentHashMap<Integer, ItemContainer> storedItems) {
        this(id, Bukkit.getOfflinePlayer(UUID.fromString(ownerUUID)), sizeType, isPlaced, lastLocation, storedItems);
    }

    @Deprecated(forRemoval = true)
    public StorageUnitData(
        int id,
        OfflinePlayer owner,
        StorageUnitType sizeType,
        boolean isPlaced,
        Location lastLocation,
        Map<Integer, ItemContainer> storedItems) {
        this(id,
            owner,
            sizeType,
            isPlaced,
            lastLocation,
            storedItems instanceof ConcurrentHashMap<Integer, ItemContainer> concurrent
                ? concurrent
                : throwUnsupportedOperationException("General Map is no longer allowed to be an argument in this method, you are supposed to use ConcurrentMap instead of Map"));
    }

    public StorageUnitData(
        int id,
        OfflinePlayer owner,
        StorageUnitType sizeType,
        boolean isPlaced,
        Location lastLocation,
        ConcurrentHashMap<Integer, ItemContainer> storedItems) {
        this.id = id;
        this.owner = owner;
        this.sizeType = sizeType;
        this.isPlaced = isPlaced;
        this.lastLocation = lastLocation;
        this.storedItems = storedItems; //!! DO NOT USE `new` keyword to create a new ConcurrentHashMap, this will cause data lose!!
    }

    /**
     * Per-accessor hot-path caches store stable database item IDs, never positions in a map snapshot.
     * This prevents a drawer insert/removal from making a cached index point at a different item.
     */
    public static void addPersistentAccessHistory(Location location, Integer itemId) {
        persistentAccessHistory
            .computeIfAbsent(normalizeHistoryLocation(location), ignored -> new ConcurrentHashMap<>())
            .put(itemId, 0);
    }

    public static void addCacheMiss(Location location, Integer itemId) {
        final Map<Integer, Integer> locations =
            persistentAccessHistory.computeIfAbsent(normalizeHistoryLocation(location), ignored -> new ConcurrentHashMap<>());
        final int value = locations.merge(itemId, 1, Integer::sum);
        if (value > NetworkRoot.cacheMissThreshold) {
            removePersistentAccessHistory(location, itemId);
        }
    }

    public static void minusCacheMiss(Location location, Integer itemId) {
        final Map<Integer, Integer> locations = persistentAccessHistory.get(normalizeHistoryLocation(location));
        if (locations == null) {
            return;
        }
        locations.computeIfPresent(itemId, (ignored, misses) -> misses <= 1 ? null : misses - 1);
    }

    public static Map<Integer, Integer> getPersistentAccessHistory(Location location) {
        final Map<Integer, Integer> cached = persistentAccessHistory.get(normalizeHistoryLocation(location));
        return cached == null ? Map.of() : cached;
    }

    public static void removePersistentAccessHistory(Location location) {
        persistentAccessHistory.remove(normalizeHistoryLocation(location));
    }

    public static void removePersistentAccessHistory(Location location, Integer itemId) {
        final Map<Integer, Integer> locations = persistentAccessHistory.get(normalizeHistoryLocation(location));
        if (locations == null) {
            return;
        }
        locations.remove(itemId);
        if (locations.isEmpty()) {
            persistentAccessHistory.remove(normalizeHistoryLocation(location), locations);
        }
    }

    public static void addCountObservingAccessHistory(Location location, Integer itemId) {
        final Map<Integer, Integer> locations =
            observingAccessHistory.computeIfAbsent(normalizeHistoryLocation(location), ignored -> new ConcurrentHashMap<>());
        final int count = locations.merge(itemId, 1, Integer::sum);
        if (count >= NetworkRoot.persistentThreshold) {
            locations.remove(itemId);
            addPersistentAccessHistory(location, itemId);
        }
    }

    public static Map<Integer, Integer> getCountObservingAccessHistory(Location location) {
        final Map<Integer, Integer> cached = observingAccessHistory.get(normalizeHistoryLocation(location));
        return cached == null ? Map.of() : cached;
    }

    public static void removeCountObservingAccessHistory(Location location) {
        observingAccessHistory.remove(normalizeHistoryLocation(location));
    }

    public static void removeCountObservingAccessHistory(Location location, Integer itemId) {
        final Map<Integer, Integer> locations = observingAccessHistory.get(normalizeHistoryLocation(location));
        if (locations == null) {
            return;
        }
        locations.remove(itemId);
        if (locations.isEmpty()) {
            observingAccessHistory.remove(normalizeHistoryLocation(location), locations);
        }
    }

    public static void clearAccessHistory(Location location) {
        final Location key = normalizeHistoryLocation(location);
        observingAccessHistory.remove(key);
        persistentAccessHistory.remove(key);
    }

    public static void clearAllAccessHistory() {
        observingAccessHistory.clear();
        persistentAccessHistory.clear();
    }

    private static @NotNull Location normalizeHistoryLocation(@NotNull Location location) {
        final Location normalized = location.clone();
        normalized.setX(location.getBlockX());
        normalized.setY(location.getBlockY());
        normalized.setZ(location.getBlockZ());
        normalized.setYaw(0.0F);
        normalized.setPitch(0.0F);
        return normalized;
    }

    public static boolean isBlacklisted(@NotNull ItemStack itemStack) {
        return StackUtils.isBlacklisted(itemStack);
    }

    private static ConcurrentHashMap<Integer, ItemContainer> throwUnsupportedOperationException(@NotNull String message) {
        throw new UnsupportedOperationException(message);
    }

    /**
     * Add item to unit, the amount will be the item stack amount
     *
     * @param item: item will be added
     * @return the amount actual added
     */
    @Deprecated
    public int addStoredItem(@NotNull ItemStack item, boolean contentLocked) {
        return addStoredItem(item, item.getAmount(), contentLocked, false);
    }

    @Deprecated
    public int addStoredItem(@NotNull ItemStack item, boolean contentLocked, boolean force) {
        return addStoredItem(item, item.getAmount(), contentLocked, force);
    }

    @Deprecated
    public int addStoredItem(@NotNull ItemStack item, int amount, boolean contentLocked) {
        return addStoredItem(item, amount, contentLocked, false);
    }

    /**
     * Add item to unit
     *
     * @param item:   item will be added
     * @param amount: amount will be added
     * @return the amount actual added
     */
    @Deprecated
    public int addStoredItem(@NotNull ItemStack item, int amount, boolean contentLocked, boolean force) {
        return addStoredItem0(getLastLocation(), item, amount, contentLocked, force);
    }

    public synchronized void setPlaced(boolean isPlaced) {
        if (this.isPlaced != isPlaced) {
            this.isPlaced = isPlaced;
            DataStorage.setContainerStatus(id, isPlaced);
        }
    }

    public synchronized void setSizeType(@NotNull StorageUnitType sizeType) {
        if (this.sizeType != sizeType) {
            this.sizeType = sizeType;
            DataStorage.setContainerSizeType(id, sizeType);
        }
    }

    public synchronized void setLastLocation(@NotNull Location lastLocation) {
        if (!lastLocation.equals(this.lastLocation)) {
            this.lastLocation = lastLocation.clone();
            DataStorage.setContainerLocation(id, this.lastLocation);
        }
    }

    public synchronized void removeItem(int itemId) {
        if (storedItems.remove(itemId) != null) {
            DataStorage.deleteStoredItem(id, itemId);
        }
    }

    public synchronized void setItemAmount(int itemId, int amount) {
        if (amount < 0) {
            // Directly remove
            removeItem(itemId);
            return;
        }
        ItemContainer container = storedItems.get(itemId);
        if (container != null) {
            container.setAmount(amount);
            DataStorage.setStoredAmount(id, itemId, amount);
        }
    }

    public synchronized void removeAmount(int itemId, int amount) {
        ItemContainer container = storedItems.get(itemId);
        if (container != null) {
            container.removeAmount(amount);
            if (container.getAmount() <= 0 && !NetworksDrawer.isLocked(getLastLocation())) {
                removeItem(itemId);
                return;
            }
            DataStorage.setStoredAmount(id, itemId, container.getAmount());
        }
    }

    public int getStoredTypeCount() {
        return storedItems.size();
    }

    public int getTotalAmount() {
        int re = 0;
        for (ItemContainer each : storedItems.values()) {
            re += each.getAmount();
        }
        return re;
    }

    public long getTotalAmountLong() {
        long re = 0;
        for (ItemContainer each : storedItems.values()) {
            re += each.getAmount();
        }
        return re;
    }

    @Deprecated
    public @NotNull List<ItemContainer> getStoredItems() {
        return copyStoredItems();
    }

    public @NotNull List<ItemContainer> copyStoredItems() {
        return new ArrayList<>(storedItems.values());
    }

    public @NotNull Collection<ItemContainer> getStoredItemsDirectly() {
        return storedItems.values();
    }

    public @NotNull Map<Integer, ItemContainer> copyStoredItemsMap() {
        return new ConcurrentHashMap<>(storedItems);
    }

    public Map<Integer, ItemContainer> getStoredItemsMap() {
        return storedItems;
    }

    @Deprecated
    @Nullable
    public ItemStack requestItem(@NotNull ItemRequest itemRequest) {
        return requestItem0(getLastLocation(), itemRequest);
    }

    @Deprecated
    public void depositItemStacks(@NotNull Map<ItemStack, Long> itemsToDeposit, boolean contentLocked) {
        depositItemStacks0(getLastLocation(), itemsToDeposit, contentLocked);
    }

    @Deprecated
    public void depositItemStack(@NotNull Map.Entry<ItemStack, Integer> entry, boolean contentLocked) {
        depositItemStack0(getLastLocation(), entry, contentLocked);
    }

    @Deprecated
    public void depositItemStack(@NotNull Map<ItemStack, Integer> itemsToDeposit, boolean contentLocked) {
        depositItemStack0(getLastLocation(), itemsToDeposit, contentLocked);
    }

    @Deprecated
    public void depositItemStack(@NotNull ItemStack @NotNull [] itemsToDeposit, boolean contentLocked) {
        depositItemStack0(getLastLocation(), itemsToDeposit, contentLocked);
    }

    @Deprecated
    public void depositItemStack(@Nullable ItemStack itemsToDeposit, boolean contentLocked, boolean force) {
        depositItemStack0(getLastLocation(), itemsToDeposit, contentLocked, force);
    }

    @Deprecated
    public void depositItemStack(ItemStack item, boolean contentLocked) {
        depositItemStack0(getLastLocation(), item, contentLocked);
    }

    @Nullable
    public synchronized ItemStack requestItem0(@NotNull Location accessor, @NotNull ItemRequest itemRequest) {
        return requestItem0(accessor, itemRequest, true);
    }

    /**
     * Atomically withdraws one matching drawer entry. Cached lookups use stable ItemContainer IDs and every
     * database write is queued only after the in-memory amount has been committed.
     */
    @Nullable
    public synchronized ItemStack requestItem0(
        @NotNull Location accessor,
        @NotNull ItemRequest itemRequest,
        boolean contentLocked) {

        final ItemStack template = itemRequest.getItemStack();
        final int requested = itemRequest.getAmount();
        if (template == null || template.getType().isAir() || requested <= 0) {
            return null;
        }

        final Map<Integer, Integer> hotIds = getPersistentAccessHistory(accessor);
        for (Integer itemId : List.copyOf(hotIds.keySet())) {
            final ItemContainer container = storedItems.get(itemId);
            if (container == null) {
                removePersistentAccessHistory(accessor, itemId);
                continue;
            }
            if (!StackUtils.itemsMatch(itemRequest, container.getSampleDirectly())) {
                addCacheMiss(accessor, itemId);
                continue;
            }

            final ItemStack withdrawn = withdrawFromContainer(container, template, requested, contentLocked);
            if (withdrawn != null) {
                minusCacheMiss(accessor, itemId);
                return withdrawn;
            }
        }

        for (ItemContainer container : storedItems.values()) {
            if (!StackUtils.itemsMatch(itemRequest, container.getSampleDirectly())) {
                continue;
            }
            final ItemStack withdrawn = withdrawFromContainer(container, template, requested, contentLocked);
            if (withdrawn != null) {
                addCountObservingAccessHistory(accessor, container.getId());
                return withdrawn;
            }
        }
        return null;
    }

    @Nullable
    private ItemStack withdrawFromContainer(
        @NotNull ItemContainer container,
        @NotNull ItemStack template,
        int requested,
        boolean contentLocked) {

        final int available = Math.max(0, container.getAmount());
        final int taken = Math.min(requested, available);
        if (taken <= 0) {
            if (!contentLocked) {
                removeItem(container.getId());
            }
            return null;
        }

        container.removeAmount(taken);
        if (container.getAmount() <= 0 && !contentLocked) {
            removeItem(container.getId());
        } else {
            DataStorage.setStoredAmount(id, container.getId(), Math.max(0, container.getAmount()));
        }

        final ItemStack result = template.clone();
        result.setAmount(taken);
        return result;
    }

    public synchronized void depositItemStacks0(
        @NotNull Location accessor, @NotNull Map<ItemStack, Long> itemsToDeposit, boolean contentLocked) {
        for (Map.Entry<ItemStack, Long> entry : itemsToDeposit.entrySet()) {
            if (entry.getValue() > Integer.MAX_VALUE) {
                // rollback to MAX_VALUE
                long before = entry.getValue();
                ItemStack item = StackUtils.getAsQuantity(entry.getKey(), Integer.MAX_VALUE);
                depositItemStack0(accessor, item, contentLocked);
                long leftover = item.getAmount();
                entry.setValue(before - Integer.MAX_VALUE + leftover);
            } else {
                ItemStack item = StackUtils.getAsQuantity(entry.getKey(), Math.toIntExact(entry.getValue()));
                depositItemStack0(accessor, item, contentLocked);
                long rest = item.getAmount();
                entry.setValue(rest);
            }
        }
    }

    public synchronized void depositItemStack0(
        @NotNull Location accessor, @NotNull Map.Entry<ItemStack, Integer> entry, boolean contentLocked) {
        ItemStack item = StackUtils.getAsQuantity(entry.getKey(), entry.getValue());
        depositItemStack0(accessor, item, contentLocked);
        int leftover = item.getAmount();
        entry.setValue(leftover);
    }

    public synchronized void depositItemStack0(
        @NotNull Location accessor, @NotNull Map<ItemStack, Integer> itemsToDeposit, boolean contentLocked) {
        for (Map.Entry<ItemStack, Integer> entry : itemsToDeposit.entrySet()) {
            depositItemStack0(accessor, entry, contentLocked);
        }
    }

    public synchronized void depositItemStack0(
        @NotNull Location accessor, @NotNull ItemStack @NotNull [] itemsToDeposit, boolean contentLocked) {
        for (ItemStack item : itemsToDeposit) {
            depositItemStack0(accessor, item, contentLocked);
        }
    }

    public synchronized void depositItemStack0(
        @NotNull Location accessor, @Nullable ItemStack itemsToDeposit, boolean contentLocked, boolean force) {
        if (itemsToDeposit == null
            || itemsToDeposit.getType().isAir()
            || itemsToDeposit.getAmount() <= 0
            || isBlacklisted(itemsToDeposit)) {
            return;
        }

        final int incoming = itemsToDeposit.getAmount();
        final boolean matchedExistingType = storedItems.values().stream().anyMatch(container -> container.isSimilar(itemsToDeposit));
        final int actualAdded = addStoredItem0(accessor, itemsToDeposit, incoming, contentLocked, force);
        if (NetworksDrawer.isVoidExcess(getLastLocation()) && matchedExistingType) {
            // Preserve historical drawer semantics: only overflow for an already stored item type is voided.
            itemsToDeposit.setAmount(0);
        } else {
            itemsToDeposit.setAmount(Math.max(0, incoming - actualAdded));
        }
    }

    public synchronized void depositItemStack0(@NotNull Location accessor, ItemStack item, boolean contentLocked) {
        depositItemStack0(accessor, item, contentLocked, false);
    }

    /**
     * Add item to unit
     *
     * @param accessor: accessor
     * @param item:     item will be added
     * @param amount:   amount will be added
     * @return the amount actual added
     */
    public synchronized int addStoredItem0(
        @NotNull Location accessor,
        @NotNull ItemStack item,
        int amount,
        boolean contentLocked,
        boolean force) {

        if (item.getType().isAir() || amount <= 0 || isBlacklisted(item)) {
            return 0;
        }

        final Map<Integer, Integer> hotIds = getPersistentAccessHistory(accessor);
        for (Integer itemId : List.copyOf(hotIds.keySet())) {
            final ItemContainer container = storedItems.get(itemId);
            if (container == null) {
                removePersistentAccessHistory(accessor, itemId);
                continue;
            }
            if (!container.isSimilar(item)) {
                addCacheMiss(accessor, itemId);
                continue;
            }

            final int accepted = addToContainer(container, amount);
            minusCacheMiss(accessor, itemId);
            return accepted;
        }

        for (ItemContainer container : storedItems.values()) {
            if (!container.isSimilar(item)) {
                continue;
            }

            final int accepted = addToContainer(container, amount);
            addCountObservingAccessHistory(accessor, container.getId());
            return accepted;
        }

        if (!force && (contentLocked || NetworksDrawer.isLocked(getLastLocation()))) {
            return 0;
        }

        if (storedItems.size() >= sizeType.getMaxItemCount()) {
            return 0;
        }

        final int accepted = Math.max(0, Math.min(amount, sizeType.getEachMaxSize()));
        if (accepted <= 0) {
            return 0;
        }
        final int itemId = DataStorage.getItemId(item);
        final ItemContainer existing = storedItems.putIfAbsent(itemId, new ItemContainer(itemId, item, accepted));
        if (existing == null) {
            DataStorage.addStoredItem(id, itemId, accepted);
            addCountObservingAccessHistory(accessor, itemId);
            return accepted;
        }

        // A concurrent creator won the slot. Reuse its canonical container instead of overwriting its amount.
        final int added = addToContainer(existing, amount);
        return added;
    }

    private int addToContainer(@NotNull ItemContainer container, int requested) {
        final int current = Math.max(0, container.getAmount());
        if (current > sizeType.getEachMaxSize()) {
            container.setAmount(sizeType.getEachMaxSize());
            DataStorage.setStoredAmount(id, container.getId(), container.getAmount());
            return 0;
        }

        final int capacity = Math.max(0, sizeType.getEachMaxSize() - current);
        final int accepted = Math.max(0, Math.min(requested, capacity));
        if (accepted > 0) {
            container.addAmount(accepted);
            DataStorage.setStoredAmount(id, container.getId(), container.getAmount());
        }
        return accepted;
    }

    /**
     * Add item to unit, the amount will be the item stack amount
     *
     * @param accessor: accessor
     * @param item:     item will be added
     * @return the amount actual added
     */
    public int addStoredItem0(Location accessor, @NotNull ItemStack item, boolean contentLocked) {
        return addStoredItem0(accessor, item, item.getAmount(), contentLocked, false);
    }

    public int addStoredItem0(Location accessor, @NotNull ItemStack item, boolean contentLocked, boolean force) {
        return addStoredItem0(accessor, item, item.getAmount(), contentLocked, force);
    }

    public int addStoredItem0(Location accessor, @NotNull ItemStack item, int amount, boolean contentLocked) {
        return addStoredItem0(accessor, item, amount, contentLocked, false);
    }
}

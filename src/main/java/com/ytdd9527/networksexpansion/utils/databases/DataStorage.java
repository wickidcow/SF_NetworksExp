package com.ytdd9527.networksexpansion.utils.databases;

import com.balugaq.netex.api.data.StorageUnitData;
import com.balugaq.netex.api.enums.StorageUnitType;
import com.balugaq.netex.utils.Debug;
import com.balugaq.netex.utils.Lang;
import io.github.sefiraat.networks.Networks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/** Thread-safe cache and delayed write buffer for drawer storage units. */
public final class DataStorage {

    private enum LoadState {
        LOADING,
        LOADED
    }

    private static final ConcurrentMap<Integer, LoadState> STATE = new ConcurrentHashMap<>(4096);
    private static final ConcurrentMap<Integer, Optional<StorageUnitData>> CACHE = new ConcurrentHashMap<>(4096);
    private static final AtomicReference<ConcurrentMap<Integer, ConcurrentMap<Integer, Integer>>> CHANGES =
        new AtomicReference<>(new ConcurrentHashMap<>(4096));

    private DataStorage() {
    }

    public static void requestStorageData(int id) {
        if (id < 0 || STATE.putIfAbsent(id, LoadState.LOADING) != null) {
            return;
        }

        try {
            Networks.getQueryQueue().scheduleQuery(() -> {
                loadContainer(id);
                return false;
            });
        } catch (RuntimeException exception) {
            STATE.remove(id, LoadState.LOADING);
            throw exception;
        }
    }

    public static void restoreFromLocation(
        @NotNull Location location,
        @NotNull Consumer<Optional<StorageUnitData>> usage
    ) {
        Networks.getQueryQueue().scheduleQuery(new QueuedTask() {
            private Optional<StorageUnitData> result = Optional.empty();

            @Override
            public boolean execute() {
                int id = dataSource().getIdFromLocation(location);
                if (id >= 0) {
                    if (!isContainerLoaded(id)) {
                        STATE.put(id, LoadState.LOADING);
                        loadContainer(id);
                    }
                    result = getCachedStorageData(id);
                }
                return true;
            }

            @Override
            public boolean callback() {
                Bukkit.getScheduler().runTask(Networks.getInstance(), () -> usage.accept(result));
                return false;
            }
        });
    }

    public static @NotNull Optional<StorageUnitData> getCachedStorageData(int id) {
        return CACHE.getOrDefault(id, Optional.empty());
    }

    public static int getItemId(@NotNull ItemStack item) {
        return dataSource().getItemId(item);
    }

    public static synchronized @NotNull StorageUnitData createStorageUnitData(
        @NotNull OfflinePlayer owner,
        @NotNull StorageUnitType sizeType,
        @NotNull Location placedLocation
    ) {
        StorageUnitData data = new StorageUnitData(
            dataSource().getNextContainerId(), owner.getUniqueId().toString(), sizeType, true, placedLocation);

        dataSource().saveNewStorageData(data);
        CACHE.put(data.getId(), Optional.of(data));
        STATE.put(data.getId(), LoadState.LOADED);
        return data;
    }

    public static void setContainerStatus(int id, boolean placed) {
        if (isContainerLoaded(id)) {
            getCachedStorageData(id).ifPresent(data -> data.setPlaced(placed));
        }
        dataSource().updateContainer(id, "IsPlaced", placed ? "1" : "0");
    }

    public static void setContainerSizeType(int id, @NotNull StorageUnitType type) {
        if (isContainerLoaded(id)) {
            getCachedStorageData(id).ifPresent(data -> data.setSizeType(type));
        }
        dataSource().updateContainer(id, "SizeType", String.valueOf(type.ordinal()));
    }

    public static void setContainerLocation(int id, @NotNull Location location) {
        if (isContainerLoaded(id)) {
            getCachedStorageData(id).ifPresent(data -> data.setLastLocation(location));
        }
        dataSource().updateContainer(id, "LastLocation", formatLocation(location));
    }

    // These methods intentionally do not mutate the current StorageUnitData snapshot.
    public static void addStoredItem(int containerId, int itemId, int amount) {
        dataSource().addStoredItem(containerId, itemId, amount);
    }

    public static void deleteStoredItem(int containerId, int itemId) {
        dataSource().deleteStoredItem(containerId, itemId);
    }

    public static void setStoredAmount(int containerId, int itemId, int amount) {
        CHANGES.get()
            .computeIfAbsent(containerId, ignored -> new ConcurrentHashMap<>())
            .put(itemId, amount);
    }

    public static void saveAmountChange() {
        ConcurrentMap<Integer, ConcurrentMap<Integer, Integer>> snapshot =
            CHANGES.getAndSet(new ConcurrentHashMap<>(4096));
        if (snapshot.isEmpty()) {
            return;
        }

        Networks.getInstance().getLogger().info(Lang.getString("messages.data-saving.saving-drawer"));
        for (Map.Entry<Integer, ConcurrentMap<Integer, Integer>> container : snapshot.entrySet()) {
            for (Map.Entry<Integer, Integer> item : container.getValue().entrySet()) {
                dataSource().updateItemAmount(container.getKey(), item.getKey(), item.getValue());
            }
        }
        Networks.getInstance().getLogger().info(Lang.getString("messages.data-saving.saved-drawer"));
        Networks.getInstance().debug("Database task amount: " + Networks.getQueryQueue().getTaskAmount());
    }

    public static int getPendingContainerChangeCount() {
        return CHANGES.get().size();
    }

    public static int getCachedContainerCount() {
        return CACHE.size();
    }

    public static int getLoadingContainerCount() {
        int loading = 0;
        for (LoadState state : STATE.values()) {
            if (state == LoadState.LOADING) {
                loading++;
            }
        }
        return loading;
    }

    public static boolean isContainerLoaded(int id) {
        return id == -1 || STATE.get(id) == LoadState.LOADED;
    }

    static void setContainerLoaded(int id) {
        STATE.put(id, LoadState.LOADED);
    }

    static @NotNull String formatLocation(@NotNull Location location) {
        return Objects.requireNonNull(location.getWorld(), "Drawer location has no world").getUID()
            + ";" + location.getBlockX()
            + ";" + location.getBlockY()
            + ";" + location.getBlockZ();
    }

    public static void clearRuntimeCache() {
        CACHE.clear();
        STATE.clear();
        CHANGES.set(new ConcurrentHashMap<>(4096));
    }

    private static void loadContainer(int id) {
        try {
            StorageUnitData data = dataSource().getStorageData(id);
            CACHE.put(id, Optional.ofNullable(data));
            // A missing row is still a completed lookup. Older builds left it permanently loading.
            STATE.put(id, LoadState.LOADED);
        } catch (RuntimeException exception) {
            STATE.remove(id);
            Debug.trace(exception);
        }
    }

    private static @NotNull DataSource dataSource() {
        DataSource source = Networks.getDataSource();
        if (source == null) {
            throw new IllegalStateException("Networks drawer database is not initialized");
        }
        return source;
    }
}

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

import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Level;

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
    private static final AtomicBoolean SAVE_IN_FLIGHT = new AtomicBoolean();
    private static volatile String lastSaveStatus = "idle";
    private static volatile int recoveryEntryCount;

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

    /**
     * Replays the idempotent recovery journal before normal autosaving starts. This does not block the server
     * thread; it is ordered through the same SQLite worker as every other drawer operation.
     */
    public static void replayRecoveryJournal() {
        if (!recoveryJournalEnabled() || !SAVE_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }

        Path journal = dataSource().getRecoveryJournalFile();
        final Map<Integer, Map<Integer, Integer>> recovered;
        try {
            Map<DrawerRecoveryJournal.Key, Integer> flat = DrawerRecoveryJournal.read(journal);
            recoveryEntryCount = flat.size();
            if (flat.isEmpty()) {
                SAVE_IN_FLIGHT.set(false);
                lastSaveStatus = "recovery-empty";
                return;
            }
            recovered = DrawerRecoveryJournal.expand(flat);
            lastSaveStatus = "replaying-" + flat.size();
            Networks.getInstance().getLogger().warning(
                "Replaying " + flat.size() + " Networks drawer amount update(s) from the recovery journal.");
        } catch (IOException exception) {
            SAVE_IN_FLIGHT.set(false);
            lastSaveStatus = "recovery-read-failed";
            throw new IllegalStateException("Could not read the Networks drawer recovery journal", exception);
        }

        scheduleSnapshot(recovered, true);
    }

    /**
     * Persists every still-unsubmitted runtime amount to the recovery journal before queue shutdown. The
     * values remain in memory as well; this method is a crash/shutdown checkpoint, not a normal save.
     */
    public static void checkpointPendingChangesForShutdown() {
        if (!recoveryJournalEnabled()) {
            return;
        }
        Map<Integer, Map<Integer, Integer>> pending = immutableSnapshot(CHANGES.get());
        if (pending.isEmpty()) {
            return;
        }
        try {
            DrawerRecoveryJournal.merge(dataSource().getRecoveryJournalFile(), pending);
            recoveryEntryCount = DrawerRecoveryJournal.count(dataSource().getRecoveryJournalFile());
            lastSaveStatus = "shutdown-checkpointed";
        } catch (IOException exception) {
            lastSaveStatus = "shutdown-checkpoint-failed";
            Networks.getInstance().getLogger().log(
                Level.SEVERE,
                "Networks could not checkpoint pending drawer amounts before shutdown.",
                exception);
        }
    }

    public static void saveAmountChange() {
        if (!SAVE_IN_FLIGHT.compareAndSet(false, true)) {
            return;
        }

        ConcurrentMap<Integer, ConcurrentMap<Integer, Integer>> liveSnapshot =
            CHANGES.getAndSet(new ConcurrentHashMap<>(4096));
        if (liveSnapshot.isEmpty()) {
            SAVE_IN_FLIGHT.set(false);
            lastSaveStatus = "idle";
            return;
        }

        Map<Integer, Map<Integer, Integer>> snapshot = immutableSnapshot(liveSnapshot);
        if (recoveryJournalEnabled()) {
            try {
                DrawerRecoveryJournal.merge(dataSource().getRecoveryJournalFile(), snapshot);
                recoveryEntryCount = DrawerRecoveryJournal.count(dataSource().getRecoveryJournalFile());
            } catch (IOException exception) {
                mergeBack(snapshot);
                SAVE_IN_FLIGHT.set(false);
                lastSaveStatus = "journal-write-failed";
                Networks.getInstance().getLogger().log(
                    Level.SEVERE,
                    "Networks refused to queue drawer amount updates because the recovery journal could not be written.",
                    exception);
                return;
            }
        }

        Networks.getInstance().getLogger().info(Lang.getString("messages.data-saving.saving-drawer"));
        lastSaveStatus = "saving-" + countEntries(snapshot);
        scheduleSnapshot(snapshot, false);
    }

    private static void scheduleSnapshot(
        @NotNull Map<Integer, ? extends Map<Integer, Integer>> snapshot,
        boolean recoveryReplay
    ) {
        try {
            dataSource().applyAmountChanges(snapshot, success -> completeSnapshot(snapshot, success, recoveryReplay));
        } catch (RuntimeException exception) {
            if (!recoveryReplay) {
                mergeBack(snapshot);
            }
            SAVE_IN_FLIGHT.set(false);
            lastSaveStatus = "queue-rejected";
            Networks.getInstance().getLogger().log(
                Level.SEVERE,
                "Networks could not queue a drawer amount transaction; the recovery journal was retained.",
                exception);
        }
    }

    private static void completeSnapshot(
        @NotNull Map<Integer, ? extends Map<Integer, Integer>> snapshot,
        boolean success,
        boolean recoveryReplay
    ) {
        try {
            if (success) {
                boolean journalRetained = false;
                if (recoveryJournalEnabled()) {
                    try {
                        DrawerRecoveryJournal.removeCommitted(dataSource().getRecoveryJournalFile(), snapshot);
                        recoveryEntryCount = DrawerRecoveryJournal.count(dataSource().getRecoveryJournalFile());
                    } catch (IOException exception) {
                        // The transaction committed. Keeping an idempotent absolute-value journal is safe and
                        // preferable to risking a false cleanup.
                        journalRetained = true;
                        lastSaveStatus = "committed-journal-retained";
                        Networks.getInstance().getLogger().log(
                            Level.WARNING,
                            "Drawer amounts committed, but Networks could not trim the recovery journal. "
                                + "The same absolute values will be replayed safely after restart.",
                            exception);
                    }
                }
                if (!journalRetained) {
                    lastSaveStatus = recoveryReplay ? "recovery-complete" : "saved";
                }
                Networks.getInstance().getLogger().info(Lang.getString("messages.data-saving.saved-drawer"));
            } else {
                if (!recoveryReplay) {
                    mergeBack(snapshot);
                }
                lastSaveStatus = recoveryReplay ? "recovery-failed" : "save-failed";
                Networks.getInstance().getLogger().severe(
                    "A Networks drawer amount transaction failed. The recovery journal was retained for retry.");
            }
        } finally {
            // Never leave autosaving permanently blocked if logging, journal cleanup, or a callback fails.
            SAVE_IN_FLIGHT.set(false);
            Networks.getInstance().debug("Database task amount: " + Networks.getQueryQueue().getTaskAmount());
        }
    }

    private static @NotNull Map<Integer, Map<Integer, Integer>> immutableSnapshot(
        @NotNull Map<Integer, ? extends Map<Integer, Integer>> source
    ) {
        Map<Integer, Map<Integer, Integer>> copy = new LinkedHashMap<>();
        for (Map.Entry<Integer, ? extends Map<Integer, Integer>> container : source.entrySet()) {
            copy.put(container.getKey(), Map.copyOf(container.getValue()));
        }
        return Map.copyOf(copy);
    }

    private static void mergeBack(@NotNull Map<Integer, ? extends Map<Integer, Integer>> snapshot) {
        ConcurrentMap<Integer, ConcurrentMap<Integer, Integer>> current = CHANGES.get();
        for (Map.Entry<Integer, ? extends Map<Integer, Integer>> container : snapshot.entrySet()) {
            ConcurrentMap<Integer, Integer> destination =
                current.computeIfAbsent(container.getKey(), ignored -> new ConcurrentHashMap<>());
            for (Map.Entry<Integer, Integer> item : container.getValue().entrySet()) {
                // A newer runtime amount always wins over the older failed snapshot.
                destination.putIfAbsent(item.getKey(), item.getValue());
            }
        }
    }

    private static int countEntries(@NotNull Map<Integer, ? extends Map<Integer, Integer>> snapshot) {
        int count = 0;
        for (Map<Integer, Integer> items : snapshot.values()) {
            count += items.size();
        }
        return count;
    }

    private static boolean recoveryJournalEnabled() {
        Networks plugin = Networks.getInstance();
        return plugin != null && plugin.getConfig().getBoolean("database.recovery-journal", true);
    }

    public static int getPendingContainerChangeCount() {
        return CHANGES.get().size();
    }

    public static int getPendingAmountChangeCount() {
        return countEntries(CHANGES.get());
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

    public static boolean isSaveInFlight() {
        return SAVE_IN_FLIGHT.get();
    }

    public static int getRecoveryEntryCount() {
        return recoveryEntryCount;
    }

    public static @NotNull String getLastSaveStatus() {
        return lastSaveStatus;
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
        SAVE_IN_FLIGHT.set(false);
        recoveryEntryCount = 0;
        lastSaveStatus = "idle";
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

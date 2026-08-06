package io.github.sefiraat.networks.diagnostics;

import com.balugaq.netex.api.data.StorageUnitData;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.utils.databases.DataSource;
import com.ytdd9527.networksexpansion.utils.databases.DataStorage;
import com.ytdd9527.networksexpansion.utils.databases.QueryQueue;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.compatibility.CompatibilityReport;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.slimefun.NetworksSlimefunItemStacks;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.slimefun.network.NetworkObject;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.sefiraat.networks.utils.FailureCircuitBreaker;
import io.github.sefiraat.networks.utils.TransferAudit;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Loaded-state integrity scanner for Networks nodes, controllers, indexes, drawers, and database work. */
public final class NetworksDoctor {

    private static final int SAMPLE_LIMIT = 12;
    private static int automaticNodeCursor;

    private NetworksDoctor() {
    }

    public static void resetRuntimeState() {
        automaticNodeCursor = 0;
    }

    public static @NotNull NetworksDoctorReport run(boolean repair) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Networks Doctor must run on the server thread");
        }

        long scanned = 0L;
        long issues = 0L;
        long repaired = 0L;
        long failures = 0L;
        long unloaded = 0L;
        List<String> details = new ArrayList<>();
        Set<Location> staleLocations = new HashSet<>();

        Map<Location, NodeDefinition> nodes = NetworkStorage.getAllNetworkObjects();
        for (Location location : nodes.keySet()) {
            scanned++;
            if (!isLoaded(location)) {
                unloaded++;
                continue;
            }

            try {
                SlimefunBlockData data = StorageCacheUtils.getBlock(location);
                SlimefunItem item = data == null ? null : SlimefunItem.getById(data.getSfId());
                boolean valid = item instanceof NetworkObject && item.getAddon() instanceof Networks;
                if (!valid) {
                    issues++;
                    staleLocations.add(location);
                    addSample(details, "Stale node: " + format(location));
                    if (repair) {
                        NetworkStorage.invalidateNode(location);
                        repaired++;
                    }
                }
            } catch (RuntimeException exception) {
                failures++;
                addSample(details, "Node scan failed at " + format(location) + ": "
                    + exception.getClass().getSimpleName());
            }
        }

        for (Location controller : new HashSet<>(NetworkController.getNetworks().keySet())) {
            scanned++;
            if (!isLoaded(controller)) {
                unloaded++;
                continue;
            }
            try {
                SlimefunBlockData data = StorageCacheUtils.getBlock(controller);
                boolean valid = data != null
                    && NetworksSlimefunItemStacks.NETWORK_CONTROLLER.getItemId().equals(data.getSfId());
                if (!valid) {
                    if (staleLocations.add(controller)) {
                        issues++;
                    }
                    addSample(details, "Stale controller: " + format(controller));
                    if (repair) {
                        NetworkController.removeRuntimeState(controller);
                        NetworkStorage.removeNodeOnly(controller);
                        repaired++;
                    }
                }
            } catch (RuntimeException exception) {
                failures++;
                addSample(details, "Controller scan failed at " + format(controller) + ": "
                    + exception.getClass().getSimpleName());
            }
        }

        final long now = System.currentTimeMillis();
        for (Map.Entry<Location, FailureCircuitBreaker.FailureSnapshot> entry
            : NetworkController.getControllerFailures().entrySet()) {
            final Location location = entry.getKey();
            final FailureCircuitBreaker.FailureSnapshot failure = entry.getValue();
            scanned++;

            if (!isLoaded(location)) {
                unloaded++;
                issues++;
                addSample(details, "Stale controller fault state in unloaded chunk: " + format(location));
                if (repair) {
                    NetworkController.clearControllerFailure(location);
                    repaired++;
                }
                continue;
            }

            try {
                final SlimefunItem item = StorageCacheUtils.getSfItem(location);
                if (!(item instanceof NetworkController)) {
                    issues++;
                    addSample(details, "Stale controller fault state: " + format(location));
                    if (repair) {
                        NetworkController.clearControllerFailure(location);
                        repaired++;
                    }
                    continue;
                }

                issues++;
                String state = failure.isBlocked(now)
                    ? "quarantined for " + Math.max(1L, failure.remainingCooldownMillis(now) / 1000L) + "s"
                    : "awaiting a successful rebuild";
                addSample(details, "Controller " + state + " at " + format(location)
                    + ": " + failure.failureType() + " (" + failure.consecutiveFailures() + " failure(s))");
            } catch (RuntimeException exception) {
                failures++;
                addSample(details, "Controller fault-state scan failed at " + format(location) + ": "
                    + exception.getClass().getSimpleName());
            }
        }

        for (Map.Entry<Location, QuantumCache> entry : Map.copyOf(NetworkQuantumStorage.getCaches()).entrySet()) {
            final Location location = entry.getKey();
            final QuantumCache cache = entry.getValue();
            scanned++;
            if (!isLoaded(location)) {
                unloaded++;
                continue;
            }

            try {
                final SlimefunItem item = StorageCacheUtils.getSfItem(location);
                if (!(item instanceof NetworkQuantumStorage)) {
                    issues++;
                    addSample(details, "Stale quantum cache: " + format(location));
                    if (repair) {
                        NetworkQuantumStorage.getCaches().remove(location, cache);
                        repaired++;
                    }
                    continue;
                }

                final long amount = cache.getAmountLong();
                final long limit = cache.getLimitLong();
                final var template = cache.getItemStack();
                if (amount < 0L || limit < 1L || amount > limit) {
                    issues++;
                    addSample(details, "Invalid quantum amount/limit at " + format(location)
                        + ": " + amount + '/' + limit);
                    if (repair && amount >= 0L) {
                        cache.setLimit(Math.max(1L, amount));
                        NetworkQuantumStorage.syncBlock(location, cache);
                        repaired++;
                    }
                }
                if (amount > 0L && (template == null || template.getType() == Material.AIR)) {
                    issues++;
                    addSample(details, "Quantum cache has " + amount + " item(s) but no recoverable template at "
                        + format(location));
                } else if (amount == 0L && template != null && template.getType() == Material.AIR) {
                    issues++;
                    addSample(details, "Quantum cache has an AIR template at " + format(location));
                    if (repair) {
                        NetworkQuantumStorage.getCaches().remove(location, cache);
                        repaired++;
                    }
                }
            } catch (RuntimeException exception) {
                failures++;
                addSample(details, "Quantum cache scan failed at " + format(location) + ": "
                    + exception.getClass().getSimpleName());
            }
        }

        int nodeCount = NetworkStorage.getAllNetworkObjects().size();
        int indexedCount = NetworkStorage.getIndexedLocationCount();
        if (nodeCount != indexedCount) {
            issues++;
            addSample(details, "Chunk index mismatch: " + indexedCount + " indexed locations for "
                + nodeCount + " registered nodes");
            if (repair) {
                NetworkStorage.rebuildChunkIndex();
                repaired++;
            }
        }

        QueryQueue queue = Networks.getQueryQueue();
        DataSource source = Networks.getDataSource();
        if (queue == null || !queue.isAcceptingTasks()) {
            issues++;
            addSample(details, "Database queue is not accepting tasks");
        }
        if (source == null || !source.isOpen()) {
            issues++;
            addSample(details, "Drawer database connection is closed");
        }
        if (DataStorage.getRecoveryEntryCount() > 0 && !DataStorage.isSaveInFlight()) {
            issues++;
            addSample(details, "Drawer recovery journal still contains "
                + DataStorage.getRecoveryEntryCount() + " unapplied amount update(s)");
        }
        TransferAudit.Snapshot transfer = TransferAudit.snapshot();
        if (transfer.outstandingRollbackItems() > 0 || transfer.uncompensatedDepositItems() > 0) {
            issues++;
            addSample(details, "Transfer compensation is incomplete: rollback="
                + transfer.outstandingRollbackItems() + ", deposit=" + transfer.uncompensatedDepositItems());
        }

        addRuntimeDetails(details);
        details.add("Registry: " + nodeCount + " nodes across "
            + NetworkStorage.getIndexedChunkCount() + " loaded chunk indexes; duplicate registrations="
            + NetworkStorage.getDuplicateRegistrationAttemptCount() + ", type conflicts="
            + NetworkStorage.getConflictingRegistrationReplacementCount());
        details.add("Controller safety: " + NetworkController.getTrackedControllerFailureCount() + " tracked, "
            + NetworkController.getQuarantinedControllerCount(now) + " quarantined, "
            + NetworkController.getTotalControllerFailureCount() + " total rebuild failures, "
            + NetworkController.getTotalControllerTripCount() + " circuit trips");
        details.add("Drawers: " + DataStorage.getCachedContainerCount() + " cached, "
            + DataStorage.getLoadingContainerCount() + " loading, "
            + DataStorage.getPendingContainerChangeCount() + " pending containers/"
            + DataStorage.getPendingAmountChangeCount() + " amount updates, save="
            + DataStorage.getLastSaveStatus() + ", recovery entries=" + DataStorage.getRecoveryEntryCount());
        details.add("Quantum storage: " + NetworkQuantumStorage.getCaches().size() + " loaded cache(s)");
        details.add("Drawer hot caches: " + StorageUnitData.observingAccessHistory.size() + " observing, "
            + StorageUnitData.persistentAccessHistory.size() + " persistent");
        if (queue != null) {
            QueryQueue.QueueSnapshot queueSnapshot = queue.snapshot();
            details.add("Database queue: " + queueSnapshot.queued() + " queued, "
                + queueSnapshot.inFlight() + " executing, " + queueSnapshot.executed() + " completed, "
                + queueSnapshot.failed() + " failed, " + queueSnapshot.rejected() + " rejected, "
                + queueSnapshot.cancelled() + " cancelled; last failure=" + queueSnapshot.lastFailure());
        }
        if (source != null) {
            details.add("Database safety: integrity=" + source.getIntegrityStatus()
                + ", startup backup=" + source.getLastBackup());
        }
        details.add("Transfer safety: withdrawals=" + transfer.withdrawalCommitted() + '/' + transfer.withdrawn()
            + " committed, deposits=" + transfer.depositCommitted() + '/' + transfer.depositOffered()
            + " committed, rollback outstanding=" + transfer.outstandingRollbackItems()
            + ", deposit compensation outstanding=" + transfer.uncompensatedDepositItems()
            + ", safety-dropped=" + transfer.safetyDropped() + ", failures=" + transfer.failures());
        if (unloaded > 0L) {
            details.add("Skipped " + unloaded + " entries in unloaded chunks; Doctor never force-loads chunks");
        }

        return new NetworksDoctorReport(repair, scanned, issues, repaired, failures, unloaded, details);
    }

    /**
     * Rotating, bounded stale-node repair for the scheduled maintenance task. Manual Doctor commands still
     * run the complete node/controller/storage scan, while automatic maintenance never walks the entire
     * loaded registry in one server tick.
     */
    public static @NotNull NetworksDoctorReport runAutomaticRepair(int maximumEntries) {
        if (!Bukkit.isPrimaryThread()) {
            throw new IllegalStateException("Networks Doctor must run on the server thread");
        }

        final int budget = Math.max(1, maximumEntries);
        long scanned = 0L;
        long issues = 0L;
        long repaired = 0L;
        long failures = 0L;
        long unloaded = 0L;
        List<String> details = new ArrayList<>();
        final long now = System.currentTimeMillis();

        Map<Location, NodeDefinition> nodes = NetworkStorage.getAllNetworkObjects();
        List<Location> locations = new ArrayList<>(nodes.keySet());
        int totalNodes = locations.size();
        int processed = Math.min(budget, totalNodes);

        if (totalNodes == 0) {
            automaticNodeCursor = 0;
        } else {
            int start = Math.floorMod(automaticNodeCursor, totalNodes);
            for (int offset = 0; offset < processed; offset++) {
                Location location = locations.get((start + offset) % totalNodes);
                scanned++;
                if (!isLoaded(location)) {
                    unloaded++;
                    continue;
                }

                try {
                    SlimefunBlockData data = StorageCacheUtils.getBlock(location);
                    SlimefunItem item = data == null ? null : SlimefunItem.getById(data.getSfId());
                    boolean valid = item instanceof NetworkObject && item.getAddon() instanceof Networks;
                    if (!valid) {
                        issues++;
                        addSample(details, "Stale node: " + format(location));
                        NetworkStorage.invalidateNode(location);
                        repaired++;
                    }
                } catch (RuntimeException exception) {
                    failures++;
                    addSample(details, "Automatic node scan failed at " + format(location) + ": "
                        + exception.getClass().getSimpleName());
                }
            }
            automaticNodeCursor = (start + processed) % totalNodes;
        }

        int nodeCount = NetworkStorage.getAllNetworkObjects().size();
        int indexedCount = NetworkStorage.getIndexedLocationCount();
        if (nodeCount != indexedCount) {
            issues++;
            addSample(details, "Chunk index mismatch: " + indexedCount + " indexed locations for "
                + nodeCount + " registered nodes");
            NetworkStorage.rebuildChunkIndex();
            repaired++;
        }

        QueryQueue queue = Networks.getQueryQueue();
        DataSource source = Networks.getDataSource();
        if (queue == null || !queue.isAcceptingTasks()) {
            issues++;
            addSample(details, "Database queue is not accepting tasks");
        }
        if (source == null || !source.isOpen()) {
            issues++;
            addSample(details, "Drawer database connection is closed");
        }

        addRuntimeDetails(details);
        details.add("Controller safety: " + NetworkController.getTrackedControllerFailureCount() + " tracked, "
            + NetworkController.getQuarantinedControllerCount(now) + " quarantined, "
            + NetworkController.getTotalControllerFailureCount() + " total rebuild failures, "
            + NetworkController.getTotalControllerTripCount() + " circuit trips");
        details.add("Node registration history: duplicates="
            + NetworkStorage.getDuplicateRegistrationAttemptCount() + ", type conflicts="
            + NetworkStorage.getConflictingRegistrationReplacementCount());
        details.add("Automatic scan window: " + processed + '/' + totalNodes
            + " loaded registry entries; next cursor=" + automaticNodeCursor);
        if (unloaded > 0L) {
            details.add("Skipped " + unloaded + " entries in unloaded chunks; Doctor never force-loads chunks");
        }

        return new NetworksDoctorReport(true, scanned, issues, repaired, failures, unloaded, details);
    }

    private static void addRuntimeDetails(@NotNull List<String> details) {
        CompatibilityReport compatibility = Networks.getCompatibilityReport();
        if (compatibility != null) {
            details.add("Core: " + compatibility.getCoreVariant().getDisplayName() + ' '
                + compatibility.getCoreVersion());
            details.add("Runtime: Minecraft " + compatibility.getMinecraftVersion() + ", Java "
                + compatibility.getJavaFeature());
        }

        SupportedPluginManager integrations = Networks.getSupportedPluginManager();
        if (integrations != null) {
            details.add("Integrations: " + String.join(", ", integrations.getIntegrationSummary()));
            List<String> storageAdapters = integrations.getStorageAdapterSummary();
            details.add("Storage adapters: " + (storageAdapters.isEmpty()
                ? "native-only" : String.join(", ", storageAdapters)));
        }
    }

    private static boolean isLoaded(@NotNull Location location) {
        World world = location.getWorld();
        return world != null && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);
    }

    private static void addSample(@NotNull List<String> details, @NotNull String detail) {
        if (details.size() < SAMPLE_LIMIT) {
            details.add(detail);
        }
    }

    private static @NotNull String format(@NotNull Location location) {
        String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        return world + ':' + location.getBlockX() + ',' + location.getBlockY() + ',' + location.getBlockZ();
    }
}

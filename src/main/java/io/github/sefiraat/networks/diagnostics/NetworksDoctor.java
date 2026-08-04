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
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.slimefun.NetworksSlimefunItemStacks;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.slimefun.network.NetworkObject;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
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

    private NetworksDoctor() {
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

        CompatibilityReport compatibility = Networks.getCompatibilityReport();
        if (compatibility != null) {
            details.add("Core: " + compatibility.getCoreVariant().getDisplayName() + ' '
                + compatibility.getCoreVersion());
            details.add("Runtime: Minecraft " + compatibility.getMinecraftVersion() + ", Java "
                + compatibility.getJavaFeature());
        }
        details.add("Registry: " + nodeCount + " nodes across "
            + NetworkStorage.getIndexedChunkCount() + " loaded chunk indexes");
        details.add("Drawers: " + DataStorage.getCachedContainerCount() + " cached, "
            + DataStorage.getLoadingContainerCount() + " loading, "
            + DataStorage.getPendingContainerChangeCount() + " pending change sets");
        details.add("Quantum storage: " + NetworkQuantumStorage.getCaches().size() + " loaded cache(s)");
        details.add("Drawer hot caches: " + StorageUnitData.observingAccessHistory.size() + " observing, "
            + StorageUnitData.persistentAccessHistory.size() + " persistent");
        if (queue != null) {
            details.add("Database queue: " + queue.getQueuedTaskAmount() + " queued, "
                + queue.getInFlightTaskAmount() + " executing");
        }
        if (unloaded > 0L) {
            details.add("Skipped " + unloaded + " entries in unloaded chunks; Doctor never force-loads chunks");
        }

        return new NetworksDoctorReport(repair, scanned, issues, repaired, failures, unloaded, details);
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

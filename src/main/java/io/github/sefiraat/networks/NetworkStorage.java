package io.github.sefiraat.networks;

import com.balugaq.netex.api.data.StorageUnitData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.bakedlibs.dough.blocks.ChunkPosition;
import io.github.sefiraat.networks.network.NetworkNode;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.slimefun.network.NetworkObject;
import lombok.experimental.UtilityClass;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.LongAdder;

/** Runtime index of loaded Networks nodes. */
@UtilityClass
public class NetworkStorage {

    private static final Map<ChunkPosition, Set<Location>> ALL_NETWORK_OBJECTS_BY_CHUNK = new ConcurrentHashMap<>();
    private static final Map<Location, NodeDefinition> ALL_NETWORK_OBJECTS = new ConcurrentHashMap<>();
    private static final LongAdder DUPLICATE_REGISTRATION_ATTEMPTS = new LongAdder();
    private static final LongAdder CONFLICTING_REGISTRATION_REPLACEMENTS = new LongAdder();

    /**
     * Removes a node and every child currently attached to its runtime network tree.
     * Chunk indexes are removed at the same time so stale nodes cannot survive a block break.
     */
    public static void removeNode(@NotNull Location location) {
        ArrayDeque<Location> pending = new ArrayDeque<>();
        Set<Location> visited = new HashSet<>();
        Set<Location> affectedControllers = new HashSet<>();
        pending.add(normalize(location));

        while (!pending.isEmpty()) {
            Location current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }

            NodeDefinition definition = ALL_NETWORK_OBJECTS.get(current);
            if (definition != null) {
                NetworkNode node = definition.getNode();
                if (node != null) {
                    NetworkRoot root = node.getRoot();
                    if (root != null) {
                        affectedControllers.add(root.getNodePosition());
                    }
                    for (NetworkNode child : node.getChildrenNodes()) {
                        Location childLocation = child.getNodePosition();
                        if (childLocation != null) {
                            pending.addLast(normalize(childLocation));
                        }
                    }
                }
            }
            removeNodeOnly(current);
        }

        affectedControllers.forEach(NetworkController::markTopologyDirty);
    }

    /** Removes only this runtime entry, without traversing children. */
    public static void removeNodeOnly(@NotNull Location location) {
        Location key = normalize(location);
        ALL_NETWORK_OBJECTS.remove(key);
        NetworkRoot.clearAccessHistory(key);
        StorageUnitData.clearAccessHistory(key);

        ChunkPosition chunkPosition = new ChunkPosition(key);
        Set<Location> locations = ALL_NETWORK_OBJECTS_BY_CHUNK.get(chunkPosition);
        if (locations != null) {
            locations.remove(key);
            if (locations.isEmpty()) {
                ALL_NETWORK_OBJECTS_BY_CHUNK.remove(chunkPosition, locations);
            }
        }
    }

    public static boolean containsKey(@NotNull Location location) {
        return getNode(location) != null;
    }

    /**
     * Returns the loaded runtime definition for a network node.
     *
     * <p>This method is intentionally a cheap registry lookup because it is called for every neighbour visited
     * during network topology discovery. Stale Slimefun block validation is handled by lifecycle events and the
     * bounded Networks Doctor maintenance pass instead of performing a storage lookup for every graph edge.</p>
     */
    public static @Nullable NodeDefinition getNode(@NotNull Location location) {
        final Location key = normalize(location);
        final NodeDefinition definition = ALL_NETWORK_OBJECTS.get(key);
        if (definition == null) {
            return null;
        }

        final World world = key.getWorld();
        if (world == null || !world.isChunkLoaded(key.getBlockX() >> 4, key.getBlockZ() >> 4)) {
            markAssignedRootDirty(definition);
            removeNodeOnly(key);
            return null;
        }

        return definition;
    }

    /**
     * Explicit physical-node validation for diagnostics and repair paths that need to verify Slimefun storage.
     * Normal graph traversal deliberately uses {@link #getNode(Location)} to avoid a storage lookup per edge.
     */
    public static @Nullable NodeDefinition getValidatedNode(@NotNull Location location) {
        final Location key = normalize(location);
        final NodeDefinition definition = getNode(key);
        if (definition == null) {
            return null;
        }

        final var slimefunItem = StorageCacheUtils.getSfItem(key);
        if (!(slimefunItem instanceof NetworkObject networkObject)
            || networkObject.getNodeType() != definition.getType()) {
            invalidateStaleNode(key, definition);
            return null;
        }
        return definition;
    }

    /** Invalidates one cached node and its owning runtime network without touching unloaded chunks. */
    public static void invalidateNode(@NotNull Location location) {
        final Location key = normalize(location);
        final NodeDefinition definition = ALL_NETWORK_OBJECTS.get(key);
        if (definition != null) {
            invalidateStaleNode(key, definition);
        }
    }

    private static void invalidateStaleNode(
        @NotNull Location location, @NotNull NodeDefinition definition) {
        final NetworkNode node = definition.getNode();
        final Location controllerLocation = node != null && node.getRoot() != null
            ? node.getRoot().getNodePosition()
            : null;

        removeNodeOnly(location);
        if (controllerLocation != null) {
            NetworkController.removeRuntimeState(controllerLocation);
        }
    }

    /**
     * Registers a loaded node without blindly replacing a live definition. Same-type duplicate attempts retain
     * the definition that already carries a runtime assignment; type conflicts replace the stale definition and
     * invalidate its previously built controller tree so no root can keep using the old node identity.
     */
    public static void registerNode(@NotNull Location location, @NotNull NodeDefinition nodeDefinition) {
        Location key = normalize(location);
        AtomicBoolean acceptedIncoming = new AtomicBoolean();
        AtomicBoolean topologyChanged = new AtomicBoolean();
        AtomicReference<NetworkRoot> conflictingRoot = new AtomicReference<>();

        ALL_NETWORK_OBJECTS.compute(key, (ignored, existing) -> {
            if (existing == null) {
                acceptedIncoming.set(true);
                topologyChanged.set(true);
                return nodeDefinition;
            }

            if (existing == nodeDefinition) {
                acceptedIncoming.set(true);
                return nodeDefinition;
            }

            if (existing.getType() == nodeDefinition.getType()) {
                DUPLICATE_REGISTRATION_ATTEMPTS.increment();
                if (existing.getNode() == null && nodeDefinition.getNode() != null) {
                    acceptedIncoming.set(true);
                    return nodeDefinition;
                }
                return existing;
            }

            CONFLICTING_REGISTRATION_REPLACEMENTS.increment();
            NetworkNode oldNode = existing.getNode();
            if (oldNode != null) {
                conflictingRoot.set(oldNode.getRoot());
            }
            acceptedIncoming.set(true);
            topologyChanged.set(true);
            return nodeDefinition;
        });

        ALL_NETWORK_OBJECTS_BY_CHUNK
            .computeIfAbsent(new ChunkPosition(key), ignored -> ConcurrentHashMap.newKeySet())
            .add(key);

        NetworkRoot oldRoot = conflictingRoot.get();
        if (acceptedIncoming.get() && oldRoot != null) {
            NetworkController.discardRuntimeNetwork(oldRoot.getNodePosition());
        } else if (topologyChanged.get()) {
            markAdjacentRootsDirty(key);
        }
    }

    /** Clears node-to-root assignments belonging to one discarded runtime tree without unregistering blocks. */
    public static int clearRuntimeAssignments(@NotNull NetworkRoot root) {
        int cleared = 0;
        for (Location location : root.getNodeLocations()) {
            NodeDefinition definition = ALL_NETWORK_OBJECTS.get(normalize(location));
            NetworkNode assigned = definition == null ? null : definition.getNode();
            if (assigned != null && assigned.getRoot() == root) {
                definition.setNode(null);
                cleared++;
            }
            NetworkRoot.clearAccessHistory(location);
            StorageUnitData.clearAccessHistory(location);
        }
        return cleared;
    }

    public static long getDuplicateRegistrationAttemptCount() {
        return DUPLICATE_REGISTRATION_ATTEMPTS.sum();
    }

    public static long getConflictingRegistrationReplacementCount() {
        return CONFLICTING_REGISTRATION_REPLACEMENTS.sum();
    }

    /**
     * Discards only nodes in the unloading chunk. Descendants in other loaded chunks remain indexed
     * and will reconnect when the controller performs its next dirty topology rebuild.
     */
    public static void unregisterChunk(@NotNull Chunk chunk) {
        ChunkPosition chunkPosition = new ChunkPosition(chunk);
        Set<Location> locations = ALL_NETWORK_OBJECTS_BY_CHUNK.remove(chunkPosition);
        if (locations == null) {
            return;
        }

        Set<Location> affectedControllers = new HashSet<>();
        for (Location location : new HashSet<>(locations)) {
            NodeDefinition definition = ALL_NETWORK_OBJECTS.get(location);
            if (definition != null) {
                NetworkNode node = definition.getNode();
                NetworkRoot root = node == null ? null : node.getRoot();
                if (root != null) {
                    affectedControllers.add(root.getNodePosition());
                }
            }
            ALL_NETWORK_OBJECTS.remove(location);
            NetworkRoot.clearAccessHistory(location);
            StorageUnitData.clearAccessHistory(location);
        }
        locations.clear();
        affectedControllers.forEach(NetworkController::markTopologyDirty);
    }

    public static @NotNull Map<Location, NodeDefinition> getAllNetworkObjects() {
        return Collections.unmodifiableMap(new HashMap<>(ALL_NETWORK_OBJECTS));
    }

    public static int getIndexedChunkCount() {
        return ALL_NETWORK_OBJECTS_BY_CHUNK.size();
    }

    public static int getIndexedLocationCount() {
        int count = 0;
        for (Set<Location> locations : ALL_NETWORK_OBJECTS_BY_CHUNK.values()) {
            count += locations.size();
        }
        return count;
    }

    /** Rebuilds the chunk index from the authoritative node map. */
    public static void rebuildChunkIndex() {
        ALL_NETWORK_OBJECTS_BY_CHUNK.clear();
        for (Location location : ALL_NETWORK_OBJECTS.keySet()) {
            ALL_NETWORK_OBJECTS_BY_CHUNK
                .computeIfAbsent(new ChunkPosition(location), ignored -> ConcurrentHashMap.newKeySet())
                .add(location);
        }
    }

    public static void clear() {
        ALL_NETWORK_OBJECTS.clear();
        ALL_NETWORK_OBJECTS_BY_CHUNK.clear();
        DUPLICATE_REGISTRATION_ATTEMPTS.reset();
        CONFLICTING_REGISTRATION_REPLACEMENTS.reset();
        NetworkRoot.clearAllAccessHistory();
        StorageUnitData.clearAllAccessHistory();
    }

    private static void markAssignedRootDirty(@NotNull NodeDefinition definition) {
        NetworkNode node = definition.getNode();
        NetworkRoot root = node == null ? null : node.getRoot();
        if (root != null) {
            NetworkController.markTopologyDirty(root.getNodePosition());
        }
    }

    private static void markAdjacentRootsDirty(@NotNull Location location) {
        Set<Location> controllers = new HashSet<>();
        for (var face : NetworkNode.VALID_FACES) {
            Location adjacentLocation = normalize(location.clone().add(face.getDirection()));
            NodeDefinition adjacentDefinition = ALL_NETWORK_OBJECTS.get(adjacentLocation);
            if (adjacentDefinition == null) {
                continue;
            }
            NetworkNode adjacentNode = adjacentDefinition.getNode();
            NetworkRoot root = adjacentNode == null ? null : adjacentNode.getRoot();
            if (root != null) {
                controllers.add(root.getNodePosition());
            }
        }
        controllers.forEach(NetworkController::markTopologyDirty);
    }

    private static @NotNull Location normalize(@NotNull Location location) {
        Location normalized = location.clone();
        normalized.setX(location.getBlockX());
        normalized.setY(location.getBlockY());
        normalized.setZ(location.getBlockZ());
        normalized.setYaw(0.0F);
        normalized.setPitch(0.0F);
        return normalized;
    }
}

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

/** Runtime index of loaded Networks nodes. */
@UtilityClass
public class NetworkStorage {

    private static final Map<ChunkPosition, Set<Location>> ALL_NETWORK_OBJECTS_BY_CHUNK = new ConcurrentHashMap<>();
    private static final Map<Location, NodeDefinition> ALL_NETWORK_OBJECTS = new ConcurrentHashMap<>();

    /**
     * Removes a node and every child currently attached to its runtime network tree.
     * Chunk indexes are removed at the same time so stale nodes cannot survive a block break.
     */
    public static void removeNode(@NotNull Location location) {
        ArrayDeque<Location> pending = new ArrayDeque<>();
        Set<Location> visited = new HashSet<>();
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

    public static @Nullable NodeDefinition getNode(@NotNull Location location) {
        final Location key = normalize(location);
        final NodeDefinition definition = ALL_NETWORK_OBJECTS.get(key);
        if (definition == null) {
            return null;
        }

        final World world = key.getWorld();
        if (world == null || !world.isChunkLoaded(key.getBlockX() >> 4, key.getBlockZ() >> 4)) {
            removeNodeOnly(key);
            return null;
        }

        /*
         * Network members can be destroyed without a normal BlockBreakEvent (explosions, withers,
         * programmable machines or other plugins). Validate the cached entry lazily so deleted Slimefun
         * data can never continue masquerading as a live cell, bridge or controller.
         */
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

    public static void registerNode(@NotNull Location location, @NotNull NodeDefinition nodeDefinition) {
        Location key = normalize(location);
        ALL_NETWORK_OBJECTS.put(key, nodeDefinition);
        ALL_NETWORK_OBJECTS_BY_CHUNK
            .computeIfAbsent(new ChunkPosition(key), ignored -> ConcurrentHashMap.newKeySet())
            .add(key);
    }

    /**
     * Discards only nodes in the unloading chunk. Descendants in other loaded chunks remain indexed
     * and will reconnect on their next Slimefun tick.
     */
    public static void unregisterChunk(@NotNull Chunk chunk) {
        ChunkPosition chunkPosition = new ChunkPosition(chunk);
        Set<Location> locations = ALL_NETWORK_OBJECTS_BY_CHUNK.remove(chunkPosition);
        if (locations == null) {
            return;
        }
        for (Location location : new HashSet<>(locations)) {
            ALL_NETWORK_OBJECTS.remove(location);
            NetworkRoot.clearAccessHistory(location);
            StorageUnitData.clearAccessHistory(location);
        }
        locations.clear();
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
        NetworkRoot.clearAllAccessHistory();
        StorageUnitData.clearAllAccessHistory();
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

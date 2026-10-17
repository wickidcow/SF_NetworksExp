package io.github.sefiraat.networks.utils;

import io.github.sefiraat.networks.network.NetworkRoot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fail-closed source-specific circuit breaker for an ambiguous storage refund.
 * Root replacement is NOT proof the underlying storage accepted or rejected
 * an ambiguous refund. Holds remain until controlled plugin shutdown/restart,
 * after an operator investigates the affected storage and logs.
 */
final class RootRollbackSafetyHold {
    private static final ConcurrentMap<BlockKey, HeldRoot> HELD = new ConcurrentHashMap<>();

    private RootRollbackSafetyHold() {
    }

    static boolean isHeld(@NotNull NetworkRoot root, @NotNull Location accessor) {
        if (HELD.isEmpty()) {
            return false;
        }
        // A different NetworkRoot object may point to the same underlying
        // barrel/cell data. It is NOT a verified recovery or refund receipt.
        // Never auto-clear the hold merely because the root object changed.
        return HELD.containsKey(BlockKey.of(accessor));
    }

    static void hold(
        @NotNull NetworkRoot root, @NotNull Location accessor,
        @NotNull Material material, int possibleAmount) {
        // Preserve the first incident, even if the graph rebuilt and a new
        // NetworkRoot instance later reported another ambiguous storage write.
        HELD.putIfAbsent(BlockKey.of(accessor),
            new HeldRoot(root, material, Math.max(0, possibleAmount), System.currentTimeMillis()));
    }

    static int count() {
        return HELD.size();
    }

    static @NotNull List<String> describe(int limit) {
        if (limit <= 0 || HELD.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(Math.min(limit, HELD.size()));
        for (Map.Entry<BlockKey, HeldRoot> entry : HELD.entrySet()) {
            BlockKey key = entry.getKey();
            HeldRoot held = entry.getValue();
            result.add(
                "world=" + (key.worldId() == null ? "unknown" : key.worldId())
                    + " x=" + key.x() + " y=" + key.y() + " z=" + key.z()
                    + ", possibly-unreconciled=" + held.possibleAmount() + "x" + held.material()
                    + ", first-seen=" + Instant.ofEpochMilli(held.since()));
            if (result.size() >= limit) {
                break;
            }
        }
        return List.copyOf(result);
    }

    static void reset() {
        HELD.clear();
    }

    private record HeldRoot(
        @NotNull NetworkRoot root,
        @NotNull Material material,
        int possibleAmount,
        long since) {
    }

    private record BlockKey(@Nullable UUID worldId, int x, int y, int z) {
        static BlockKey of(@NotNull Location location) {
            World world = location.getWorld();
            return new BlockKey(world == null ? null : world.getUID(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
    }
}

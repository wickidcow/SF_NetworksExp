package io.github.sefiraat.networks.utils;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stops repeated network withdrawals after a foreign menu produces an
 * unverifiable insertion result. Only an authoritative replacement registered
 * in Slimefun (or a plugin restart) can release the safety hold.
 */
final class MenuTransferSafetyHold {

    private static final ConcurrentMap<BlockKey, HeldMenu> HELD = new ConcurrentHashMap<>();

    private MenuTransferSafetyHold() {
    }

    static boolean isHeld(@NotNull BlockMenu menu) {
        // No record allocation or StorageCache lookup in the common success case.
        if (HELD.isEmpty()) {
            return false;
        }

        final BlockKey key = BlockKey.of(menu.getLocation());
        final HeldMenu incident = HELD.get(key);
        if (incident == null) {
            return false;
        }
        if (incident.menu() == menu) {
            return true;
        }

        // BlockMenu identity alone is not proof of a replaced block: temporary
        // wrappers may refer to the same live machine. Clear the hold only if
        // Slimefun's canonical menu registry confirms the replacement.
        try {
            if (StorageCacheUtils.getMenu(menu.getLocation()) == menu) {
                HELD.remove(key, incident);
                return false;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // Keep the machine held when registry verification itself fails.
        }
        return true;
    }

    static void hold(@NotNull BlockMenu menu, @NotNull Material material, int possibleUnreconciled) {
        HELD.compute(BlockKey.of(menu.getLocation()), (key, existing) -> {
            // Preserve the first incident for a still-held menu; never allow a
            // stale incident to mask a new ambiguous write to a different menu.
            if (existing != null && existing.menu() == menu) {
                return existing;
            }
            return new HeldMenu(
                menu,
                material,
                Math.max(0, possibleUnreconciled),
                System.currentTimeMillis());
        });
    }

    static int count() {
        return HELD.size();
    }

    static @NotNull List<String> describe(@SuppressWarnings("SameParameterValue") int limit) {
        if (limit <= 0 || HELD.isEmpty()) {
            return List.of();
        }

        final List<String> lines = new ArrayList<>(Math.min(limit, HELD.size()));
        for (Map.Entry<BlockKey, HeldMenu> entry : HELD.entrySet()) {
            final BlockKey key = entry.getKey();
            final HeldMenu hold = entry.getValue();
            lines.add(
                "world=" + (key.worldId() == null ? "unknown" : key.worldId())
                    + " x=" + key.x() + " y=" + key.y() + " z=" + key.z()
                    + ", possible-unreconciled=" + hold.possibleUnreconciled() + "x" + hold.material()
                    + ", first-seen=" + Instant.ofEpochMilli(hold.firstSeenEpochMilli()));
            if (lines.size() >= limit) {
                break;
            }
        }
        return List.copyOf(lines);
    }

    static void reset() {
        HELD.clear();
    }

    private record HeldMenu(
        @NotNull BlockMenu menu,
        @NotNull Material material,
        int possibleUnreconciled,
        long firstSeenEpochMilli) {
    }

    private record BlockKey(@Nullable UUID worldId, int x, int y, int z) {
        private static BlockKey of(@NotNull Location location) {
            final World world = location.getWorld();
            return new BlockKey(
                world == null ? null : world.getUID(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());
        }
    }
}

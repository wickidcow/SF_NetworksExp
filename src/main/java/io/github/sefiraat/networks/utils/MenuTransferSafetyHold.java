package io.github.sefiraat.networks.utils;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fail-closed destination hold when a third-party menu makes insertion results
 * unverifiable. Repeated retries must not silently withdraw or lose more items.
 *
 * <p>The hold is tied to the exact BlockMenu instance, not just its coordinates.
 * Replacing/breaking the machine creates a different menu, which clears the old
 * hold on the next transfer. A controlled Networks restart also clears holds.</p>
 */
final class MenuTransferSafetyHold {

    private static final ConcurrentMap<BlockKey, BlockMenu> HELD = new ConcurrentHashMap<>();

    private MenuTransferSafetyHold() {
    }

    static boolean isHeld(@NotNull BlockMenu menu) {
        final BlockKey key = BlockKey.of(menu.getLocation());
        final BlockMenu held = HELD.get(key);
        if (held == menu) {
            return true;
        }
        if (held != null) {
            // A recreated menu at the same block is a new destination instance.
            HELD.remove(key, held);
        }
        return false;
    }

    static void hold(@NotNull BlockMenu menu) {
        HELD.put(BlockKey.of(menu.getLocation()), menu);
    }

    static int count() {
        return HELD.size();
    }

    static void reset() {
        HELD.clear();
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

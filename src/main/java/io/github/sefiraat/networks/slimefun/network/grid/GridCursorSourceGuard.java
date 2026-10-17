package io.github.sefiraat.networks.slimefun.network.grid;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A legacy Grid click supplies a display/clicked stack, which may be detached
 * from the player's real inventory. Only a matching, nonempty live cursor can
 * be used as a source by the verified cursor-transfer helper. Shift-click
 * inventory deposits must use an explicit authoritative inventory slot.
 */
final class GridCursorSourceGuard {
    private GridCursorSourceGuard() {
    }

    static boolean matchesLiveCursor(@Nullable ItemStack clicked, @Nullable ItemStack cursor) {
        return clicked != null
            && clicked.getType() != null
            && !clicked.getType().isAir()
            && cursor != null
            && cursor.getType() != null
            && !cursor.getType().isAir()
            && cursor.getAmount() > 0
            && cursor.isSimilar(clicked);
    }
}

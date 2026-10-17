package io.github.sefiraat.networks.slimefun.network.grid;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A detached Grid-click snapshot may never be treated as the owned source. */
class LegacyGridSourceValidationTest {

    @Test
    void copiedPlayerInventoryStackWithoutLiveCursorIsRejected() {
        ItemStack detached = stack(Material.DIAMOND, 4);
        assertFalse(GridCursorSourceGuard.matchesLiveCursor(detached, null));
        assertFalse(GridCursorSourceGuard.matchesLiveCursor(detached, stack(Material.AIR, 0)));
    }

    @Test
    void detachedSnapshotCannotAuthorizeAnUnrelatedCursor() {
        assertFalse(GridCursorSourceGuard.matchesLiveCursor(
            stack(Material.DIAMOND, 4), stack(Material.EMERALD, 4)));
    }

    @Test
    void separateMatchingCursorIsAllowedOnlyAsAnAuthoritativeCursorSource() {
        ItemStack clicked = stack(Material.DIAMOND, 4);
        ItemStack cursor = stack(Material.DIAMOND, 4);
        when(cursor.isSimilar(clicked)).thenReturn(true);
        assertNotSame(clicked, cursor);
        assertTrue(GridCursorSourceGuard.matchesLiveCursor(clicked, cursor));
        verify(cursor).isSimilar(clicked);
    }

    @Test
    void emptyClickedDisplayIsNotAValidDeposit() {
        assertFalse(GridCursorSourceGuard.matchesLiveCursor(null, stack(Material.DIAMOND, 4)));
        assertFalse(GridCursorSourceGuard.matchesLiveCursor(stack(Material.AIR, 0), stack(Material.DIAMOND, 4)));
    }

    private static ItemStack stack(Material material, int amount) {
        ItemStack result = mock(ItemStack.class);
        when(result.getType()).thenReturn(material);
        when(result.getAmount()).thenReturn(amount);
        return result;
    }
}

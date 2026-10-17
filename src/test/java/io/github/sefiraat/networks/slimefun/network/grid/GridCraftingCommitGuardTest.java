package io.github.sefiraat.networks.slimefun.network.grid;

import java.util.concurrent.atomic.AtomicReference;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Verifies complete crafting matrices, not just independent slot snapshots. */
class GridCraftingCommitGuardTest {
    private static final int INGREDIENT = 3;
    private static final int OUTPUT = 4;
    private static final int SECOND = 5;
    private final AtomicReference<ItemStack> ingredient = new AtomicReference<>();
    private final AtomicReference<ItemStack> second = new AtomicReference<>();
    private final AtomicReference<ItemStack> result = new AtomicReference<>();
    private BlockMenu menu;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        menu = mock(BlockMenu.class);
        when(menu.getSize()).thenReturn(9);
        ingredient.set(new ItemStack(Material.STONE, 4));
        when(menu.getItemInSlot(INGREDIENT)).thenAnswer(invocation -> copy(ingredient.get()));
        when(menu.getItemInSlot(SECOND)).thenAnswer(invocation -> copy(second.get()));
        when(menu.getItemInSlot(OUTPUT)).thenAnswer(invocation -> copy(result.get()));
    }

    @AfterEach
    void tearDown() {
        GridCraftingCommitGuard.exit(menu);
        MockBukkit.unmock();
    }

    @Test
    void detachedIngredientThatWasNotActuallyDebitedNeverAuthorizesCraft() {
        assertThrows(IllegalStateException.class,
            () -> GridCraftingCommitGuard.consumeVerified(menu, INGREDIENT, new ItemStack(Material.STONE, 2)));
        assertEquals(4, ingredient.get().getAmount());
        verify(menu).consumeItem(INGREDIENT, 2, true);
        verify(menu, never()).markDirty();
    }

    @Test
    void exactPhysicalSourceDebitIsAccepted() {
        debitFirst();
        GridCraftingCommitGuard.consumeVerified(menu, INGREDIENT, new ItemStack(Material.STONE, 2));
        assertEquals(2, ingredient.get().getAmount());
        verify(menu).markDirty();
    }

    @Test
    void fullConsumptionAllowsVanillaContainerReplacement() {
        ingredient.set(new ItemStack(Material.WATER_BUCKET, 1));
        doAnswer(invocation -> {
            ingredient.set(new ItemStack(Material.BUCKET, 1));
            return null;
        }).when(menu).consumeItem(INGREDIENT, 1, true);
        GridCraftingCommitGuard.consumeVerified(menu, INGREDIENT, new ItemStack(Material.WATER_BUCKET, 1));
        assertEquals(Material.BUCKET, ingredient.get().getType());
    }

    @Test
    void partialDebitingOrAnUnexpectedReplacementFails() {
        doAnswer(invocation -> {
            ingredient.set(new ItemStack(Material.EMERALD, 4));
            return null;
        }).when(menu).consumeItem(INGREDIENT, 2, true);
        assertThrows(IllegalStateException.class,
            () -> GridCraftingCommitGuard.consumeVerified(menu, INGREDIENT, new ItemStack(Material.STONE, 2)));
    }

    @Test
    void craftedOutputNeedsExactSlotDeltaNotTheReportedRemainder() {
        ItemStack crafted = new ItemStack(Material.DIAMOND, 4);
        ItemStack prior = GridCraftingCommitGuard.snapshot(result.get());
        assertThrows(IllegalStateException.class,
            () -> GridCraftingCommitGuard.verifyExactOutputAddition(menu, OUTPUT, prior, crafted));
        result.set(new ItemStack(Material.DIAMOND, 4));
        assertDoesNotThrow(() -> GridCraftingCommitGuard.verifyExactOutputAddition(menu, OUTPUT, prior, crafted));
        result.set(new ItemStack(Material.DIAMOND, 8));
        assertThrows(IllegalStateException.class,
            () -> GridCraftingCommitGuard.verifyExactOutputAddition(menu, OUTPUT, prior, crafted));
    }

    @Test
    void existingOutputRequiresExactFourItemIncrease() {
        result.set(new ItemStack(Material.DIAMOND, 12));
        ItemStack prior = GridCraftingCommitGuard.snapshot(result.get());
        result.set(new ItemStack(Material.DIAMOND, 16));
        assertDoesNotThrow(() -> GridCraftingCommitGuard.verifyExactOutputAddition(
            menu, OUTPUT, prior, new ItemStack(Material.DIAMOND, 4)));
        result.set(new ItemStack(Material.DIAMOND, 15));
        assertThrows(IllegalStateException.class, () -> GridCraftingCommitGuard.verifyExactOutputAddition(
            menu, OUTPUT, prior, new ItemStack(Material.DIAMOND, 4)));
    }

    @Test
    void missingLaterIngredientIsRejectedBeforeAnyDebit() {
        ItemStack[] recipe = {new ItemStack(Material.STONE, 2), new ItemStack(Material.DIAMOND, 1)};
        assertThrows(IllegalStateException.class, () -> GridCraftingCommitGuard.consumeVerified(
            menu, new int[]{INGREDIENT, SECOND}, recipe));
        verify(menu, never()).consumeItem(anyInt(), anyInt(), anyBoolean());
        assertEquals(4, ingredient.get().getAmount());
    }

    @Test
    void laterConsumeCallbackCannotRestoreAnAlreadyVerifiedIngredient() {
        second.set(new ItemStack(Material.DIAMOND, 1));
        debitFirst();
        doAnswer(invocation -> {
            second.set(null);
            ingredient.set(new ItemStack(Material.STONE, 4));
            return null;
        }).when(menu).consumeItem(SECOND, 1, true);
        assertThrows(IllegalStateException.class, () -> GridCraftingCommitGuard.consumeVerified(
            menu, new int[]{INGREDIENT, SECOND},
            new ItemStack[]{new ItemStack(Material.STONE, 2), new ItemStack(Material.DIAMOND, 1)}));
    }

    @Test
    void validMultiIngredientRecipeProducesIndependentRemainderSnapshots() {
        second.set(new ItemStack(Material.DIAMOND, 1));
        debitFirst();
        doAnswer(invocation -> {
            second.set(null);
            return null;
        }).when(menu).consumeItem(SECOND, 1, true);
        int[] slots = {INGREDIENT, SECOND};
        ItemStack[] after = GridCraftingCommitGuard.consumeVerified(menu, slots,
            new ItemStack[]{new ItemStack(Material.STONE, 2), new ItemStack(Material.DIAMOND, 1)});
        assertEquals(2, after[0].getAmount());
        assertNull(after[1]);
        assertDoesNotThrow(() -> GridCraftingCommitGuard.verifyRecipeRemainders(menu, slots, after));
        ingredient.get().setAmount(3);
        assertEquals(2, after[0].getAmount());
        assertThrows(IllegalStateException.class,
            () -> GridCraftingCommitGuard.verifyRecipeRemainders(menu, slots, after));
    }

    @Test
    void originallyEmptyIngredientSlotMustStayEmpty() {
        debitFirst();
        doAnswer(invocation -> {
            second.set(new ItemStack(Material.EMERALD, 1));
            return null;
        }).when(menu).markDirty();
        assertThrows(IllegalStateException.class, () -> GridCraftingCommitGuard.consumeVerified(
            menu, new int[]{INGREDIENT, SECOND}, new ItemStack[]{new ItemStack(Material.STONE, 2), null}));
    }

    @Test
    void dirtyCallbackCannotUndoItsOwnIngredientDebit() {
        debitFirst();
        doAnswer(invocation -> {
            ingredient.set(new ItemStack(Material.STONE, 4));
            return null;
        }).when(menu).markDirty();
        assertThrows(IllegalStateException.class,
            () -> GridCraftingCommitGuard.consumeVerified(menu, INGREDIENT, new ItemStack(Material.STONE, 2)));
    }

    @Test
    void duplicateIngredientSlotsAreRejectedBeforeConsumption() {
        assertThrows(IllegalStateException.class, () -> GridCraftingCommitGuard.consumeVerified(
            menu, new int[]{INGREDIENT, INGREDIENT},
            new ItemStack[]{new ItemStack(Material.STONE, 2), new ItemStack(Material.STONE, 2)}));
        verify(menu, never()).consumeItem(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void sameMenuReentryIsBlockedAndExitAllowsTheNextIndependentCraft() {
        assertTrue(GridCraftingCommitGuard.tryEnter(menu));
        assertFalse(GridCraftingCommitGuard.tryEnter(menu));
        BlockMenu another = mock(BlockMenu.class);
        assertTrue(GridCraftingCommitGuard.tryEnter(another));
        GridCraftingCommitGuard.exit(another);
        GridCraftingCommitGuard.exit(menu);
        assertTrue(GridCraftingCommitGuard.tryEnter(menu));
    }

    @Test
    void outputChangeBeforeInsertionIsDetected() {
        result.set(new ItemStack(Material.DIAMOND, 12));
        ItemStack expected = GridCraftingCommitGuard.snapshot(result.get());
        result.set(new ItemStack(Material.DIAMOND, 16));
        assertThrows(IllegalStateException.class,
            () -> GridCraftingCommitGuard.verifyUnchanged(menu, OUTPUT, expected));
    }

    private void debitFirst() {
        doAnswer(invocation -> {
            ItemStack updated = ingredient.get().clone();
            updated.setAmount(updated.getAmount() - 2);
            ingredient.set(updated);
            return null;
        }).when(menu).consumeItem(INGREDIENT, 2, true);
    }

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}

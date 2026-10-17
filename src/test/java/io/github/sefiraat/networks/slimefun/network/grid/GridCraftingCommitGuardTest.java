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

/** Verifies input debit receipts and prevents double-crediting crafted outputs. */
class GridCraftingCommitGuardTest {
    private static final int INGREDIENT = 3;
    private static final int OUTPUT = 4;
    private final AtomicReference<ItemStack> ingredient = new AtomicReference<>();
    private final AtomicReference<ItemStack> result = new AtomicReference<>();
    private BlockMenu menu;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        menu = mock(BlockMenu.class);
        ingredient.set(new ItemStack(Material.STONE, 4));
        when(menu.getItemInSlot(INGREDIENT)).thenAnswer(invocation -> copy(ingredient.get()));
        when(menu.getItemInSlot(OUTPUT)).thenAnswer(invocation -> copy(result.get()));
    }

    @AfterEach
    void tearDown() {
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
        doAnswer(invocation -> {
            ItemStack updated = ingredient.get().clone();
            updated.setAmount(updated.getAmount() - 2);
            ingredient.set(updated);
            return null;
        }).when(menu).consumeItem(INGREDIENT, 2, true);

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

        GridCraftingCommitGuard.consumeVerified(
            menu, INGREDIENT, new ItemStack(Material.WATER_BUCKET, 1));

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

    private static ItemStack copy(ItemStack stack) {
        return stack == null ? null : stack.clone();
    }
}

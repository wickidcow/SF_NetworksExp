package io.github.sefiraat.networks.slimefun.network.pusher;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeAwareControlSlotTest {
    @BeforeEach
    void setUp() { MockBukkit.mock(); }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @Test
    void emptyControlSlotCanShowTheToggle() {
        assertEquals(RecipeAwareControlSlot.Decision.REFRESH,
            RecipeAwareControlSlot.inspect(null, icon(false, 8), icon(true, 8)));
        assertEquals(RecipeAwareControlSlot.Decision.REFRESH,
            RecipeAwareControlSlot.inspect(new ItemStack(Material.AIR), icon(false, 8), icon(true, 8)));
    }

    @Test
    void unchangedControlIsNotWrittenOnEveryMachineTick() {
        assertEquals(RecipeAwareControlSlot.Decision.UNCHANGED,
            RecipeAwareControlSlot.inspect(icon(false, 8), icon(false, 8), icon(true, 8)));
    }

    @Test
    void legitimateModeToggleStillChangesTheDisplay() {
        assertEquals(RecipeAwareControlSlot.Decision.REFRESH,
            RecipeAwareControlSlot.inspect(icon(false, 8), icon(true, 8), icon(false, 8)));
    }

    @Test
    void historicalBatchSettingChangeRefreshesTheControl() {
        assertEquals(RecipeAwareControlSlot.Decision.REFRESH,
            RecipeAwareControlSlot.inspect(icon(true, 5), icon(true, 8), icon(false, 8)));
    }

    @Test
    void aPlayersDiamondIsNotOverwrittenOrEdited() {
        ItemStack foreign = new ItemStack(Material.DIAMOND, 13);
        ItemStack prior = foreign.clone();
        assertEquals(RecipeAwareControlSlot.Decision.FOREIGN,
            RecipeAwareControlSlot.inspect(foreign, icon(false, 8), icon(true, 8)));
        assertEquals(prior, foreign);
    }

    @Test
    void forgedSimilarLookingItemWithForeignPdcRemainsProtected() {
        ItemStack foreign = icon(false, 8);
        foreign.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("otheraddon", "owner"), PersistentDataType.STRING, "preserve"));
        ItemStack prior = foreign.clone();
        assertEquals(RecipeAwareControlSlot.Decision.FOREIGN,
            RecipeAwareControlSlot.inspect(foreign, icon(false, 8), icon(true, 8)));
        assertEquals(prior, foreign);
    }

    @Test
    void malformedHistoricalBatchLineCannotClaimTheControl() {
        ItemStack foreign = icon(false, 8);
        foreign.editMeta(meta -> {
            var lore = new java.util.ArrayList<>(meta.lore());
            lore.set(4, Component.text("Batch target: invalid"));
            meta.lore(lore);
        });
        assertEquals(RecipeAwareControlSlot.Decision.FOREIGN,
            RecipeAwareControlSlot.inspect(foreign, icon(false, 8), icon(true, 8)));
    }

    private static ItemStack icon(boolean enabled, int batches) {
        ItemStack item = new ItemStack(enabled ? Material.CRAFTING_TABLE : Material.HOPPER, batches);
        item.editMeta(meta -> {
            meta.displayName(Component.text(enabled ? "Recipe-Aware Mode: ON" : "Recipe-Aware Mode: OFF"));
            meta.lore(List.of(
                Component.empty(),
                Component.text("OFF: template items use normal pusher behavior."),
                Component.text("ON: duplicate template slots and template stack amounts"),
                Component.text("define the ingredient quantities for one recipe."),
                Component.text("Batch target: " + batches),
                Component.text("The pusher keeps up to that many recipe batches"),
                Component.text("buffered in the target's valid input slots.")));
        });
        return item;
    }
}

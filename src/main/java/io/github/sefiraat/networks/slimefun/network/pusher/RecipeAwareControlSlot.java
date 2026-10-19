package io.github.sefiraat.networks.slimefun.network.pusher;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Guards recipe-mode GUI slots that were empty and player-accessible on older builds. */
final class RecipeAwareControlSlot {
    private static final int BATCH_LINE = 4;
    private static final Pattern BATCH_TEXT =
        Pattern.compile("Batch target: ([1-9]|[1-5][0-9]|6[0-4])");

    enum Decision { REFRESH, UNCHANGED, FOREIGN }

    private RecipeAwareControlSlot() { }

    static @NotNull Decision inspect(
        @Nullable ItemStack present, @NotNull ItemStack desired, @NotNull ItemStack opposite) {
        return inspect(present, desired, opposite, null);
    }

    /** Recognize a metadata- and quantity-identical historical backdrop without claiming a player's stack. */
    static @NotNull Decision inspect(
        @Nullable ItemStack present, @NotNull ItemStack desired, @NotNull ItemStack opposite,
        @Nullable ItemStack historicalBackground) {

        if (present == null || present.getType() == Material.AIR || present.getAmount() <= 0) {
            return Decision.REFRESH;
        }
        if (matchesControl(present, desired)) {
            return present.isSimilar(desired) && present.getAmount() == desired.getAmount()
                ? Decision.UNCHANGED : Decision.REFRESH;
        }
        if (matchesControl(present, opposite)) {
            return Decision.REFRESH;
        }
        // BlockMenuPreset prepopulates this slot with a language-specific background
        // pane on new machines; only that exact backdrop is safe to promote.
        return historicalBackground != null
            && present.isSimilar(historicalBackground)
            && present.getAmount() == historicalBackground.getAmount()
            ? Decision.REFRESH : Decision.FOREIGN;
    }

    private static boolean matchesControl(@NotNull ItemStack present, @NotNull ItemStack target) {
        if (present.getType() != target.getType()) {
            return false;
        }
        if (present.isSimilar(target)) {
            return true;
        }
        final ItemMeta original = present.getItemMeta();
        final ItemMeta targetMeta = target.getItemMeta();
        if (original == null || targetMeta == null) {
            return false;
        }
        final List<Component> oldLore = original.lore();
        final List<Component> targetLore = targetMeta.lore();
        if (oldLore == null || targetLore == null
            || oldLore.size() != targetLore.size() || oldLore.size() <= BATCH_LINE
            || oldLore.get(BATCH_LINE) == null) {
            return false;
        }
        if (!BATCH_TEXT.matcher(
            PlainTextComponentSerializer.plainText().serialize(oldLore.get(BATCH_LINE))).matches()) {
            return false;
        }
        // Only normalize the historical variable batch line. Every other bit
        // of metadata, including unknown owner PDC and model data, must match.
        final ItemStack normalized = present.clone();
        final ItemMeta corrected = normalized.getItemMeta();
        if (corrected == null) {
            return false;
        }
        final List<Component> lore = new ArrayList<>(oldLore);
        lore.set(BATCH_LINE, targetLore.get(BATCH_LINE));
        corrected.lore(lore);
        normalized.setItemMeta(corrected);
        return normalized.isSimilar(target);
    }
}

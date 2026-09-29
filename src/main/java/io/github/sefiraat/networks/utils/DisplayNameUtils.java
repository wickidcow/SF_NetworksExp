package io.github.sefiraat.networks.utils;

import com.ytdd9527.networksexpansion.utils.TextUtil;
import java.util.Locale;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Networks-owned display-name helpers.
 *
 * <p>This avoids a runtime dependency on GuizhanLib while preserving custom item names and
 * providing readable English names for ordinary Bukkit materials.
 */
public final class DisplayNameUtils {

    private DisplayNameUtils() {}

    @NotNull
    public static String getDisplayName(@Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            return "Air";
        }

        ItemMeta itemMeta = itemStack.getItemMeta();
        if (itemMeta != null && itemMeta.displayName() != null) {
            return TextUtil.legacy(itemMeta.displayName());
        }

        return getMaterialName(itemStack.getType());
    }

    @NotNull
    public static String getMaterialName(@NotNull Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder name = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }

            if (!name.isEmpty()) {
                name.append(' ');
            }

            name.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }

        return name.toString();
    }
}

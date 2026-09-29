package com.ytdd9527.networksexpansion.utils;

import com.balugaq.netex.api.groups.MainItemGroup;
import com.balugaq.netex.api.groups.SubFlexItemGroup;
import io.github.sefiraat.networks.utils.Keys;
import lombok.experimental.UtilityClass;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

/**
 * @author Final_ROOT
 * @since 2.0
 */
@UtilityClass
public class GroupConfigUtil {

    public static @NotNull MainItemGroup getMainItemGroup(
        @NotNull String key, @NotNull Material defaultMaterial, @NotNull String defaultName) {
        NamespacedKey namespacedKey = Keys.newKey(key);
        return new MainItemGroup(namespacedKey, createIcon(defaultMaterial, defaultName), 0);
    }

    public static @NotNull SubFlexItemGroup getSubFlexItemGroup(
        @NotNull String key, @NotNull Material defaultMaterial, @NotNull String defaultName) {
        NamespacedKey namespacedKey = Keys.newKey(key);
        return new SubFlexItemGroup(namespacedKey, createIcon(defaultMaterial, defaultName), 0);
    }

    private static @NotNull ItemStack createIcon(@NotNull Material material, @NotNull String name) {
        ItemStack icon = new ItemStack(material);
        ItemMeta meta = icon.getItemMeta();
        meta.displayName(TextUtil.component(TextUtil.color(name)).decoration(TextDecoration.ITALIC, false));
        icon.setItemMeta(meta);
        return icon;
    }
}

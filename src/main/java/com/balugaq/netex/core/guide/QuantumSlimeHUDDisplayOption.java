package com.balugaq.netex.core.guide;

import com.balugaq.jeg.api.patches.JEGGuideSettings;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.utils.Keys;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideOption;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideSettings;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

import java.util.Optional;

/**
 * @author balugaq
 */
@SuppressWarnings({"UnnecessaryUnicodeEscape", "SameReturnValue"})
@NullMarked
public class QuantumSlimeHUDDisplayOption implements SlimefunGuideOption<Boolean> {
    public static final QuantumSlimeHUDDisplayOption instance = new QuantumSlimeHUDDisplayOption();

    public static QuantumSlimeHUDDisplayOption instance() {
        return instance;
    }

    @Override
    public SlimefunAddon getAddon() {
        return Networks.getInstance();
    }

    @Override
    public Optional<ItemStack> getDisplayItem(Player p, ItemStack guide) {
        boolean enabled = getSelectedOption(p, guide).orElse(false);
        ItemStack item = new CustomItemStack(
                isEnabled(p) ? Material.KNOWLEDGE_BOOK : Material.BOOK,
                "&bQuantum Storage HUD display: &" + (enabled ? "aPercentage" : "4Numeric"),
                "",
                "&7\u21E8 &eClick to change the Quantum Storage HUD display to " + (!enabled ? "Percentage" : "Numeric")
        );
        return Optional.of(item);
    }

    public static boolean isEnabled(Player p) {
        return getSelectedOption(p);
    }

    @Override
    public NamespacedKey getKey() {
        return key0();
    }

    public static boolean getSelectedOption(Player p) {
        return !PersistentDataAPI.hasByte(p, key0()) || PersistentDataAPI.getByte(p, key0()) == (byte) 1;
    }

    public static NamespacedKey key0() {
        return Keys.newKey("beginners_guide");
    }

    @Override
    public void onClick(Player p, ItemStack guide) {
        setSelectedOption(p, guide, !getSelectedOption(p, guide).orElse(false));
        if (Networks.getSupportedPluginManager().isJustEnoughGuide()) {
            JEGGuideSettings.openSettings(p, guide);
        } else {
            SlimefunGuideSettings.openSettings(p, guide);
        }
    }

    @Override
    public Optional<Boolean> getSelectedOption(Player p, ItemStack guide) {
        NamespacedKey key = getKey();
        boolean value = !PersistentDataAPI.hasByte(p, key) || PersistentDataAPI.getByte(p, key) == (byte) 1;
        return Optional.of(value);
    }

    @Override
    public void setSelectedOption(Player p, ItemStack guide, Boolean value) {
        PersistentDataAPI.setByte(p, getKey(), value ? (byte) 1 : (byte) 0);
    }
}

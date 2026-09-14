package io.github.sefiraat.networks.diagnostics;

import io.github.sefiraat.networks.network.stackcaches.BlueprintInstance;
import io.github.sefiraat.networks.slimefun.NetworksSlimefunItemStacks;
import io.github.sefiraat.networks.utils.DisplayNameUtils;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.Theme;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentCraftingBlueprintType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.ChatColor;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

/** State-preserving English presentation migration for historical Crafting Blueprints. */
final class LegacyBlueprintSchemaMigration {

    static final String ITEM_ID = "NTW_CRAFTING_BLUEPRINT";
    static final String CANDIDATE_TYPE = "networks-crafting-blueprint-english-presentation";

    private LegacyBlueprintSchemaMigration() {
    }

    static @Nullable Result inspect(@NotNull ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (!containsCjk(meta)) {
            return null;
        }

        BlueprintInstance instance = DataTypeMethods.getCustomSafely(
            meta,
            Keys.BLUEPRINT_INSTANCE,
            PersistentCraftingBlueprintType.TYPE);
        if (instance == null || instance == BlueprintInstance.INVALID || instance.getItemStack().getType().isAir()) {
            return new Result(
                "MANUAL_ONLY",
                "Translated Crafting Blueprint presentation found, but its encoded recipe cannot be decoded safely.",
                null);
        }

        return new Result(
            "READY",
            "Translated Crafting Blueprint presentation can be regenerated from its existing encoded recipe.",
            claim(instance));
    }

    static boolean migrate(@NotNull ItemStack item, @NotNull String expectedClaim) {
        ItemMeta meta = item.getItemMeta();
        BlueprintInstance instance = DataTypeMethods.getCustomSafely(
            meta,
            Keys.BLUEPRINT_INSTANCE,
            PersistentCraftingBlueprintType.TYPE);
        if (instance == null || instance == BlueprintInstance.INVALID || instance.getItemStack().getType().isAir()) {
            return false;
        }
        if (!MessageDigest.isEqual(
            claim(instance).getBytes(StandardCharsets.US_ASCII),
            expectedClaim.getBytes(StandardCharsets.US_ASCII))) {
            return false;
        }

        ItemMeta canonicalMeta = NetworksSlimefunItemStacks.CRAFTING_BLUEPRINT.getItemMeta();
        String englishName = canonicalMeta.hasDisplayName() && !containsCjk(canonicalMeta.getDisplayName())
            ? canonicalMeta.getDisplayName()
            : ChatColor.AQUA + "Crafting Blueprint";
        List<String> lore = buildEnglishLore(instance);
        boolean changed = !englishName.equals(meta.getDisplayName()) || !lore.equals(meta.getLore());
        if (!changed) {
            return false;
        }

        meta.setDisplayName(englishName);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return true;
    }

    private static @NotNull List<String> buildEnglishLore(@NotNull BlueprintInstance instance) {
        List<String> lore = new ArrayList<>();
        lore.add(Theme.CLICK_INFO + "Encoded Recipe");
        for (ItemStack ingredient : instance.getRecipeItems()) {
            lore.add(Theme.PASSIVE + "- " + englishItemName(ingredient));
        }
        lore.add("");
        lore.add(Theme.CLICK_INFO + "Output Item");
        lore.add(Theme.PASSIVE + "- " + englishItemName(instance.getItemStack()));
        return lore;
    }

    private static @NotNull String englishItemName(@Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return "Empty";
        }

        SlimefunItem slimefunItem = SlimefunItem.getByItem(item);
        if (slimefunItem != null) {
            ItemMeta canonicalMeta = slimefunItem.getItem().getItemMeta();
            if (canonicalMeta.hasDisplayName() && !containsCjk(canonicalMeta.getDisplayName())) {
                return ChatColor.stripColor(canonicalMeta.getDisplayName());
            }
            return humanize(slimefunItem.getId());
        }

        String displayName = DisplayNameUtils.getDisplayName(item);
        if (!containsCjk(displayName)) {
            return ChatColor.stripColor(displayName);
        }
        return humanize(item.getType().name());
    }

    private static @NotNull String claim(@NotNull BlueprintInstance instance) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (ItemStack ingredient : instance.getRecipeItems()) {
                updateDigest(digest, ingredient);
            }
            digest.update((byte) 0x7f);
            updateDigest(digest, instance.getItemStack());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    private static void updateDigest(@NotNull MessageDigest digest, @Nullable ItemStack item) {
        if (item == null || item.getType().isAir()) {
            digest.update((byte) 0);
            return;
        }
        digest.update(item.serializeAsBytes());
        digest.update((byte) 0xff);
    }

    private static boolean containsCjk(@NotNull ItemMeta meta) {
        if (meta.hasDisplayName() && containsCjk(meta.getDisplayName())) {
            return true;
        }
        if (meta.hasLore()) {
            for (String line : meta.getLore()) {
                if (containsCjk(line)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containsCjk(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static @NotNull String humanize(@NotNull String id) {
        String[] words = id.toLowerCase(Locale.ROOT).split("[_:-]+");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                result.append(word.substring(1));
            }
        }
        return result.toString();
    }

    record Result(@NotNull String readiness, @NotNull String detail, @Nullable String validationClaim) {
    }
}

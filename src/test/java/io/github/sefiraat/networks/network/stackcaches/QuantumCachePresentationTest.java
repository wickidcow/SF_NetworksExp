package io.github.sefiraat.networks.network.stackcaches;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;

import com.balugaq.netex.utils.Lang;
import com.ytdd9527.networksexpansion.utils.TextUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

/** Actual metadata and QuantumCache methods; localization is the only substituted service. */
class QuantumCachePresentationTest {
    private MockedStatic<Lang> language;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
        language = mockStatic(Lang.class);
        language.when(() -> Lang.getString(anyString())).thenAnswer(call -> switch ((String) call.getArgument(0)) {
            case "messages.normal-operation.quantum_cache.empty" -> "Empty";
            case "messages.normal-operation.quantum_cache.stored_item" -> "\u00a77Stored: %s";
            case "messages.normal-operation.quantum_cache.stored_amount" -> "\u00a77Amount: %s";
            case "messages.normal-operation.quantum_cache.custom_max_limit" -> "\u00a7bLimit: %s";
            default -> throw new AssertionError("Unexpected localization key: " + call.getArgument(0));
        });
    }

    @AfterEach
    void tearDown() {
        try { if (language != null) language.close(); }
        finally { MockBukkit.unmock(); }
    }

    @Test
    void appendPreservesRichExistingLoreExactly() {
        var meta = meta();
        var rich = richLore();
        meta.lore(rich);
        cache(false).addMetaLore(meta);
        assertEquals(rich, meta.lore().subList(0, rich.size()));
        assertEquals(rich.size() + 3, meta.lore().size());
    }

    @Test
    void updatePreservesRichPrefixAndReplacesOnlyTheExistingTail() {
        for (boolean custom : List.of(false, true)) {
            var meta = meta();
            var rich = richLore();
            var initial = new ArrayList<>(rich);
            initial.add(Component.text("old item"));
            initial.add(Component.text("old amount"));
            if (custom) initial.add(Component.text("old limit"));
            meta.lore(initial);
            cache(custom).updateMetaLore(meta);
            assertEquals(rich, meta.lore().subList(0, rich.size()));
            assertEquals(initial.size(), meta.lore().size());
            assertEquals(TextUtil.component("\u00a77Amount: 9000000001"), meta.lore().get(rich.size() + 1));
        }
    }

    @Test
    void absentLoreAndShortHistoricalTailsStaySupported() {
        for (boolean custom : List.of(false, true)) {
            int required = custom ? 3 : 2;
            for (int count = 0; count <= required; count++) {
                var meta = meta();
                meta.lore(count == 0 ? null : new ArrayList<>(java.util.Collections.nCopies(count, Component.text("old"))));
                assertDoesNotThrow(() -> cache(custom).updateMetaLore(meta));
                assertEquals(required, meta.lore().size());
                assertEquals(TextUtil.component("\u00a77Stored: Diamond"), meta.lore().get(0));
            }
        }
    }

    @Test
    void appendWithoutLoreKeepsTheHistoricalSpacer() {
        var meta = meta();
        cache(true).addMetaLore(meta);
        assertEquals(List.of(Component.empty(), TextUtil.component("\u00a77Stored: Diamond"),
            TextUtil.component("\u00a77Amount: 9000000001"), TextUtil.component("\u00a7bLimit: 9000000009")), meta.lore());
    }

    @Test
    @SuppressWarnings("deprecation") // Reference the prior API behavior for representable historical text.
    void legacyTextOutputMatchesTheOriginalAlgorithm() {
        var random = new java.util.Random(70123L);
        for (int attempt = 0; attempt < 250; attempt++) {
            boolean custom = random.nextBoolean();
            var original = meta();
            var lines = new ArrayList<String>();
            for (int count = random.nextInt(8); count > 0; count--)
                lines.add("\u00a7" + "0123456789abcdef".charAt(random.nextInt(16)) + "old-" + count);
            original.setLore(lines);
            var candidate = original.clone();
            var reference = original.getLore();
            var old = reference == null ? new ArrayList<String>() : new ArrayList<>(reference);
            if (random.nextBoolean()) {
                old.add(""); old.add("\u00a77Stored: Diamond"); old.add("\u00a77Amount: 9000000001");
                if (custom) old.add("\u00a7bLimit: 9000000009");
                cache(custom).addMetaLore(candidate);
            } else {
                int required = custom ? 3 : 2;
                while (old.size() < required) old.add("");
                int base = old.size() - required;
                old.set(base, "\u00a77Stored: Diamond"); old.set(base + 1, "\u00a77Amount: 9000000001");
                if (custom) old.set(base + 2, "\u00a7bLimit: 9000000009");
                cache(custom).updateMetaLore(candidate);
            }
            original.setLore(old);
            assertEquals(original.lore(), candidate.lore(), "Historical layout " + attempt);
        }
    }

    @Test
    void metadataIdentityAndCustomModelsRemainUntouched() {
        var meta = meta();
        var id = NamespacedKey.fromString("slimefun:slimefun_item");
        var owner = NamespacedKey.fromString("oldaddon:owner");
        meta.getPersistentDataContainer().set(id, PersistentDataType.STRING, "NTW_QUANTUM_STORAGE_9");
        meta.getPersistentDataContainer().set(owner, PersistentDataType.STRING, UUID.randomUUID().toString());
        var model = meta.getCustomModelDataComponent();
        model.setFloats(List.of(12345.25F)); model.setStrings(List.of("old-model"));
        meta.setCustomModelDataComponent(model);
        meta.displayName(Component.text("Owner's name"));
        meta.setUnbreakable(true);
        var before = meta.clone();
        var cache = cache(true);
        cache.addMetaLore(meta);
        cache.updateMetaLore(meta);
        meta.lore(null); before.lore(null);
        assertEquals(before, meta);
        assertEquals(9_000_000_001L, cache.getAmountLong());
        assertEquals(9_000_000_009L, cache.getLimitLong());
        assertTrue(cache.isVoidExcess());
    }

    @Test
    void storedTemplateIsNotReplacedOrRewritten() {
        var stored = new ItemStack(Material.DIAMOND, 37);
        var data = stored.getItemMeta(); data.lore(richLore()); stored.setItemMeta(data);
        var before = stored.clone();
        var cache = new QuantumCache(stored, 37L, 100L, false, false);
        cache.addMetaLore(meta()); cache.updateMetaLore(meta());
        assertSame(stored, cache.getItemStack());
        assertEquals(before, stored);
    }

    @Test
    void repeatedUpdateDoesNotGrowLore() {
        var meta = meta(); meta.lore(richLore());
        var cache = cache(true); cache.addMetaLore(meta);
        var expected = meta.lore();
        for (int i = 0; i < 100; i++) cache.updateMetaLore(meta);
        assertEquals(expected, meta.lore());
    }

    @Test
    void cacheWithNoStoredItemRetainsItsEmptyLabel() {
        var meta = meta();
        new QuantumCache(null, 0, 64, false, false).addMetaLore(meta);
        assertEquals(TextUtil.component("\u00a77Stored: Empty"), meta.lore().get(1));
    }

    @Test
    void localizedFormattingRemainsSupported() {
        language.when(() -> Lang.getString("messages.normal-operation.quantum_cache.stored_item"))
            .thenReturn("\u00a7x\u00a71\u00a72\u00a73\u00a74\u00a75\u00a76RGB: %s");
        var meta = meta(); cache(false).addMetaLore(meta);
        assertEquals(TextUtil.component("\u00a7x\u00a71\u00a72\u00a73\u00a74\u00a75\u00a76RGB: Diamond"), meta.lore().get(1));
    }

    private static QuantumCache cache(boolean custom) {
        return new QuantumCache(new ItemStack(Material.DIAMOND), 9_000_000_001L, 9_000_000_009L, true, custom);
    }
    private static ItemMeta meta() { return new ItemStack(Material.CHEST).getItemMeta(); }
    private static List<Component> richLore() {
        return List.of(Component.translatable("block.minecraft.diamond_block"),
            Component.text("Custom", NamedTextColor.GOLD).font(Key.key("oldaddon:font"))
                .insertion("original").hoverEvent(HoverEvent.showText(Component.text("retained"))));
    }
}

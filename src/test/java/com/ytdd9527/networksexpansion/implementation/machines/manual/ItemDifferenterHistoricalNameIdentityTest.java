package com.ytdd9527.networksexpansion.implementation.machines.manual;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.core.services.CustomItemDataService;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Historical source-side identity regression. Item Differenter is a read-only
 * diagnostic, not a network stacking/transfer permission or item migration.
 * Old AlbionMC bytes are not available; these are representative fixtures.
 */
class ItemDifferenterHistoricalNameIdentityTest {
    private static final NamespacedKey ID =
        new NamespacedKey("slimefun", "slimefun_item");
    private static final NamespacedKey OWNER =
        new NamespacedKey("networks-fixture", "owner");
    private static final NamespacedKey LEDGER =
        new NamespacedKey("networks-fixture", "ledger");
    private static final NamespacedKey BYTES =
        new NamespacedKey("networks-fixture", "storage_bytes");

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void teardown() {
        MockBukkit.unmock();
    }

    private static ItemStack tagged(boolean historical) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 1);
        item.editMeta(meta -> {
            Component text = Component.text("Small Chunk of Uranium", NamedTextColor.RED);
            meta.displayName(historical
                ? Component.text("").append(text.decoration(TextDecoration.BOLD, false))
                : text.decoration(TextDecoration.BOLD, true));
            meta.lore(java.util.List.of(Component.text("Radiation Level: Moderate", NamedTextColor.YELLOW)
                .decoration(TextDecoration.ITALIC, false)));
            meta.getPersistentDataContainer().set(ID, PersistentDataType.STRING, "SMALL_URANIUM");
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "owner-kept");
            meta.getPersistentDataContainer().set(LEDGER, PersistentDataType.LONG, 9876543210123L);
            meta.getPersistentDataContainer().set(
                BYTES, PersistentDataType.BYTE_ARRAY, new byte[]{-1, 0, 42});
        });
        return item;
    }

    /**
     * MockBukkit's copied PersistentDataContainerMock objects do not implement
     * Paper's value-equality comparison: distinct metadata copies can report
     * unequal PDC even with the same keys, types and values. Use real ItemStack
     * metadata for the source fixtures and serialized-byte receipts, but model
     * that single PDC equals boundary with a shared mock ONLY when every typed
     * fixture value and key is equal. The production comparator is unchanged.
     */
    private static String compare(ItemStack left, ItemStack right) {
        ItemMeta realLeft = left.getItemMeta();
        ItemMeta realRight = right.getItemMeta();
        PersistentDataContainer first = mock(PersistentDataContainer.class);
        PersistentDataContainer second = typedPdcEquals(realLeft, realRight)
            ? first : mock(PersistentDataContainer.class);
        ItemMeta leftMeta = comparisonMeta(realLeft, first);
        ItemMeta rightMeta = comparisonMeta(realRight, second);
        ItemStack leftSnapshot = comparisonStack(left, leftMeta);
        ItemStack rightSnapshot = comparisonStack(right, rightMeta);
        CustomItemDataService service = mock(CustomItemDataService.class);
        when(service.getItemData(leftMeta)).thenReturn(Optional.ofNullable(
            realLeft.getPersistentDataContainer().get(ID, PersistentDataType.STRING)));
        when(service.getItemData(rightMeta)).thenReturn(Optional.ofNullable(
            realRight.getPersistentDataContainer().get(ID, PersistentDataType.STRING)));
        try (MockedStatic<Slimefun> slimefun = mockStatic(Slimefun.class);
             MockedStatic<StackUtils> stackUtils = mockStatic(StackUtils.class)) {
            slimefun.when(Slimefun::getItemDataService).thenReturn(service);
            return ItemDifferenter.calc(leftSnapshot, rightSnapshot);
        }
    }

    private static boolean typedPdcEquals(ItemMeta left, ItemMeta right) {
        var a = left.getPersistentDataContainer();
        var b = right.getPersistentDataContainer();
        return a.getKeys().equals(b.getKeys())
            && Objects.equals(a.get(ID, PersistentDataType.STRING),
                b.get(ID, PersistentDataType.STRING))
            && Objects.equals(a.get(OWNER, PersistentDataType.STRING),
                b.get(OWNER, PersistentDataType.STRING))
            && Objects.equals(a.get(LEDGER, PersistentDataType.LONG),
                b.get(LEDGER, PersistentDataType.LONG))
            && Arrays.equals(a.get(BYTES, PersistentDataType.BYTE_ARRAY),
                b.get(BYTES, PersistentDataType.BYTE_ARRAY));
    }

    private static ItemMeta comparisonMeta(ItemMeta source, PersistentDataContainer pdc) {
        ItemMeta snapshot = mock(ItemMeta.class);
        when(snapshot.getPersistentDataContainer()).thenReturn(pdc);
        when(snapshot.getEnchants()).thenReturn(Map.of());
        when(snapshot.getItemFlags()).thenReturn(Set.of());
        when(snapshot.hasDisplayName()).thenReturn(source.hasDisplayName());
        when(snapshot.displayName()).thenReturn(source.displayName());
        when(snapshot.hasItemName()).thenReturn(source.hasItemName());
        if (source.hasItemName()) {
            when(snapshot.itemName()).thenReturn(source.itemName());
        }
        when(snapshot.hasLore()).thenReturn(source.hasLore());
        when(snapshot.lore()).thenReturn(source.lore());
        return snapshot;
    }

    private static ItemStack comparisonStack(ItemStack original, ItemMeta snapshot) {
        ItemStack copy = mock(ItemStack.class);
        when(copy.getType()).thenReturn(original.getType());
        when(copy.getAmount()).thenReturn(original.getAmount());
        when(copy.hasItemMeta()).thenReturn(original.hasItemMeta());
        when(copy.getItemMeta()).thenReturn(snapshot);
        return copy;
    }

    private static void assertReadOnly(ItemStack left, ItemStack right, String expected) {
        byte[] leftBytes = left.serializeAsBytes();
        byte[] rightBytes = right.serializeAsBytes();
        int leftCount = left.getAmount();
        int rightCount = right.getAmount();

        assertEquals(expected, compare(left, right));
        assertArrayEquals(leftBytes, left.serializeAsBytes(), "Left source bytes must not change");
        assertArrayEquals(rightBytes, right.serializeAsBytes(), "Right source bytes must not change");
        assertEquals(leftCount, left.getAmount());
        assertEquals(rightCount, right.getAmount());
        assertEquals("owner-kept", left.getItemMeta().getPersistentDataContainer()
            .get(OWNER, PersistentDataType.STRING));
        assertEquals(9876543210123L, left.getItemMeta().getPersistentDataContainer()
            .get(LEDGER, PersistentDataType.LONG));
        assertArrayEquals(new byte[]{-1, 0, 42}, right.getItemMeta()
            .getPersistentDataContainer().get(BYTES, PersistentDataType.BYTE_ARRAY));
    }

    @Test
    void sameIdButNestedHistoricalDisplayNameIsDifferentInBothDirections() {
        ItemStack historical = tagged(true);
        ItemStack cheat = tagged(false);
        assertFalse(historical.isSimilar(cheat),
            "Identical PDC, Slimefun ID and visible text do not erase Adventure tree differences");
        assertReadOnly(historical, cheat, "neq.displayname");
        assertReadOnly(cheat, historical, "neq.displayname");
    }

    @Test
    void sameIdAndDisplayButDifferentItemNameMustRemainDifferent() {
        ItemStack left = tagged(false);
        ItemStack right = tagged(false);
        left.editMeta(meta -> meta.itemName(Component.text("Historical name", NamedTextColor.GOLD)));
        right.editMeta(meta -> meta.itemName(Component.text("Current name", NamedTextColor.GOLD)));
        assertFalse(left.isSimilar(right));
        assertReadOnly(left, right, "neq.itemname");
        assertReadOnly(right, left, "neq.itemname");
    }

    @Test
    void sameIdButItemNamePresenceMismatchCannotReportEquality() {
        ItemStack original = tagged(false);
        ItemStack named = tagged(false);
        named.editMeta(meta -> meta.itemName(Component.text("Custom item-name component")));
        assertFalse(original.isSimilar(named));
        assertReadOnly(original, named, "neq.hasitemname");
        assertReadOnly(named, original, "neq.hasitemname");
    }

    @Test
    void identicalItemsRemainEqualWithoutRewritingTheHistoricalSerializedData() {
        ItemStack original = tagged(true);
        ItemStack cloned = original.clone();
        assertTrue(original.isSimilar(cloned));
        assertReadOnly(original, cloned, "eq");
    }

    @Test
    void differentTypedOwnerOrSlimefunIdStillFailsClosed() {
        ItemStack original = tagged(false);
        ItemStack differentOwner = tagged(false);
        differentOwner.editMeta(meta -> meta.getPersistentDataContainer()
            .set(OWNER, PersistentDataType.STRING, "different-owner"));
        ItemStack differentId = tagged(false);
        differentId.editMeta(meta -> meta.getPersistentDataContainer()
            .set(ID, PersistentDataType.STRING, "OTHER_SLIMEFUN_ID"));

        assertFalse(original.isSimilar(differentOwner));
        assertFalse(original.isSimilar(differentId));
        assertEquals("neq.pdc", compare(original, differentOwner));
        assertEquals("neq.pdc", compare(original, differentId));
        assertEquals("owner-kept", original.getItemMeta().getPersistentDataContainer()
            .get(OWNER, PersistentDataType.STRING));
        assertEquals("SMALL_URANIUM", original.getItemMeta().getPersistentDataContainer()
            .get(ID, PersistentDataType.STRING));
    }
}

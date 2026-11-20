package com.ytdd9527.networksexpansion.implementation.machines.manual;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Read-only diagnostic fallback. The genuine native Paper matcher, not the
 * Slimefun ID or an incomplete hand-written field list, remains authoritative.
 * Representative fixtures are not authentic old AlbionMC inventory bytes.
 */
class ItemDifferenterUnclassifiedMetadataTest {
    private static final NamespacedKey SFID = new NamespacedKey("slimefun", "slimefun_item");
    private static final NamespacedKey OWNER = new NamespacedKey("networks-fixture", "owner");
    private static final NamespacedKey LEDGER = new NamespacedKey("networks-fixture", "ledger");
    private static final NamespacedKey BYTES = new NamespacedKey("networks-fixture", "binary");

    @BeforeEach
    void setup() {
        MockBukkit.mock();
    }

    @AfterEach
    void teardown() {
        MockBukkit.unmock();
    }

    private static ItemStack tagged() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, 17);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Small Chunk of Uranium", NamedTextColor.RED));
            meta.getPersistentDataContainer().set(SFID, PersistentDataType.STRING, "SMALL_URANIUM");
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "owner-kept");
            meta.getPersistentDataContainer().set(LEDGER, PersistentDataType.LONG, 9876543210123L);
            meta.getPersistentDataContainer().set(
                BYTES, PersistentDataType.BYTE_ARRAY, new byte[]{-1, 0, 42});
        });
        return item;
    }

    @Test
    void unclassifiedItemComponentMismatchIsVisibleAndDoesNotRewriteEitherStack() {
        ItemStack historical = tagged();
        ItemStack fresh = tagged();
        // A field outside the manual Item Differenter checks must not become
        // diagnostic "eq" when Bukkit itself refuses ItemStack similarity.
        fresh.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        assertFalse(historical.isSimilar(fresh),
            "The fixture must exhibit a meaningful unclassified ItemStack difference");

        byte[] historicalBefore = historical.serializeAsBytes();
        byte[] freshBefore = fresh.serializeAsBytes();
        int oldCount = historical.getAmount();
        int newCount = fresh.getAmount();

        assertEquals("neq.unclassified-meta",
            ItemDifferenter.reconcileUnclassifiedIdentity("eq", historical.isSimilar(fresh)));
        assertEquals("neq.unclassified-meta",
            ItemDifferenter.reconcileUnclassifiedIdentity("eq", fresh.isSimilar(historical)));
        assertArrayEquals(historicalBefore, historical.serializeAsBytes());
        assertArrayEquals(freshBefore, fresh.serializeAsBytes());
        assertEquals(oldCount, historical.getAmount());
        assertEquals(newCount, fresh.getAmount());
        assertTypedData(historical);
        assertTypedData(fresh);
    }

    @Test
    void matchingSourceIdentityPreservesEqualityWithoutChangingAnyData() {
        ItemStack old = tagged();
        ItemStack same = old.clone();
        same.setAmount(1);
        assertTrue(old.isSimilar(same), "Bukkit identity ignores stack quantity");
        byte[] before = old.serializeAsBytes();
        assertEquals("eq",
            ItemDifferenter.reconcileUnclassifiedIdentity("eq", old.isSimilar(same)));
        assertArrayEquals(before, old.serializeAsBytes());
        assertEquals(17, old.getAmount());
        assertEquals(1, same.getAmount());
        assertTypedData(old);
        assertTypedData(same);
    }

    @Test
    void recognizedPdcAndAdventureReasonsAreNeverMaskedByFallback() {
        assertEquals("neq.pdc",
            ItemDifferenter.reconcileUnclassifiedIdentity("neq.pdc", false));
        assertEquals("neq.displayname",
            ItemDifferenter.reconcileUnclassifiedIdentity("neq.displayname", false));
        assertEquals("neq.itemname",
            ItemDifferenter.reconcileUnclassifiedIdentity("neq.itemname", false));
        assertEquals("banned.blacklist",
            ItemDifferenter.reconcileUnclassifiedIdentity("banned.blacklist", true));
        assertEquals("neq.amount",
            ItemDifferenter.reconcileUnclassifiedIdentity("neq.amount", true));

        ItemStack differentOwner = tagged();
        differentOwner.editMeta(meta -> meta.getPersistentDataContainer()
            .set(OWNER, PersistentDataType.STRING, "different-owner"));
        ItemStack original = tagged();
        assertFalse(original.isSimilar(differentOwner));
        assertTypedData(original);
        assertEquals("different-owner",
            differentOwner.getItemMeta().getPersistentDataContainer()
                .get(OWNER, PersistentDataType.STRING));
    }

    private static void assertTypedData(ItemStack item) {
        var pdc = item.getItemMeta().getPersistentDataContainer();
        assertEquals("SMALL_URANIUM", pdc.get(SFID, PersistentDataType.STRING));
        assertEquals("owner-kept", pdc.get(OWNER, PersistentDataType.STRING));
        assertEquals(9876543210123L, pdc.get(LEDGER, PersistentDataType.LONG));
        assertArrayEquals(new byte[]{-1, 0, 42},
            pdc.get(BYTES, PersistentDataType.BYTE_ARRAY));
    }
}

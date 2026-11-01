package com.ytdd9527.networksexpansion.implementation.machines.manual;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.damage.DamageType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.tag.DamageTypeTags;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pin real 1.21.11 ItemStack metadata copy/serialization behavior before
 * replacing the deprecated damage-resistance reader on Paper 26.3.
 *
 * This does not establish native 26.3 deserialization, a world restart, or
 * cross-fork storage compatibility.
 */
class ItemDifferenterRealDamageResistanceParityTest {
    @BeforeEach
    void startServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void stopServer() {
        MockBukkit.unmock();
    }

    @Test
    void absentResistanceStaysAbsentAfterCloneAndBinarySerialization() {
        ItemStack original = new ItemStack(Material.NETHERITE_SWORD);
        assertFalse(isFireResistant(original.getItemMeta()));
        assertFalse(isFireResistant(original.clone().getItemMeta()));
        assertFalse(isFireResistant(roundTrip(original).getItemMeta()));
    }

    @Test
    void explicitFireTagIsReadableImmediatelyAfterSetItemMeta() {
        ItemStack original = fireResistantSword();
        assertFireTagContract(original.getItemMeta(), "original after setItemMeta");
    }

    @Test
    void explicitFireTagSurvivesItemStackClone() {
        ItemStack original = fireResistantSword();
        assertFireTagContract(original.getItemMeta(), "original before clone");
        assertFireTagContract(original.clone().getItemMeta(), "ItemStack.clone");
    }

    @Test
    void explicitFireTagSurvivesBinarySerialization() {
        ItemStack original = fireResistantSword();
        assertFireTagContract(original.getItemMeta(), "original before serializeAsBytes");
        assertFireTagContract(roundTrip(original).getItemMeta(), "deserializeBytes(serializeAsBytes)");
    }

    private static ItemStack fireResistantSword() {
        ItemStack original = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = original.getItemMeta();
        assertNotNull(meta);
        meta.setDamageResistant(DamageTypeTags.IS_FIRE);
        assertTrue(original.setItemMeta(meta), "Test fixture must accept explicit fire-resistance metadata");
        return original;
    }

    private static void assertFireTagContract(ItemMeta meta, String phase) {
        assertNotNull(meta, phase + ": item metadata must remain readable");
        assertTrue(meta.hasDamageResistant(), phase + ": explicit resistance must remain present");
        Tag<DamageType> tag = meta.getDamageResistant();
        assertNotNull(tag, phase + ": explicit resistance tag must remain readable");
        assertEquals(DamageTypeTags.IS_FIRE.getKey(), tag.getKey(),
            phase + ": preserve the exact historical fire tag key");

        // MockBukkit resolves ItemMetaMock#getDamageResistant via Bukkit.getTag,
        // which need not return the DamageTypeTags.IS_FIRE singleton. The legacy
        // production predicate compares Tag objects, not just their keys.
        // Assert its exact pre-migration contract; the separate Mockito parity
        // test proves that the canonical singleton is classified as fire.
        assertEquals(Objects.equals(tag, DamageTypeTags.IS_FIRE), isFireResistant(meta),
            phase + ": do not silently change the historical tag-object equality rule");
    }

    private static ItemStack roundTrip(ItemStack item) {
        return ItemStack.deserializeBytes(item.serializeAsBytes());
    }

    private static boolean isFireResistant(ItemMeta meta) {
        assertNotNull(meta);
        try {
            Method method = ItemDifferenter.class.getDeclaredMethod("isFireResistant", ItemMeta.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, meta);
        } catch (InvocationTargetException failure) {
            throw new AssertionError("Production predicate failed for real serialized item metadata",
                failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Production fire predicate unavailable", failure);
        }
    }
}

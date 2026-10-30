package com.ytdd9527.networksexpansion.implementation.machines.manual;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.tag.DamageTypeTags;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

/**
 * Baseline for the 1.21.11 damage-resistance component after ItemStack cloning.
 * A newer Paper API replacement must preserve this distinction before the
 * legacy tag reader can be retired. This does not test old-world deserialization.
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
    void fireTagSurvivesItemStackClone() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        meta.setDamageResistant(DamageTypeTags.IS_FIRE);
        item.setItemMeta(meta);

        ItemMeta copied = copiedMeta(item);
        assertTrue(copied.hasDamageResistant());
        assertEquals(DamageTypeTags.IS_FIRE, copied.getDamageResistant());
        assertTrue(isFireResistant(copied));
    }

    @Test
    void otherDamageTagDoesNotBecomeFireAfterClone() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        meta.setDamageResistant(DamageTypeTags.IS_FREEZING);
        item.setItemMeta(meta);

        ItemMeta copied = copiedMeta(item);
        assertTrue(copied.hasDamageResistant());
        assertEquals(DamageTypeTags.IS_FREEZING, copied.getDamageResistant());
        assertFalse(isFireResistant(copied));
    }

    @Test
    void clearingResistanceStaysAbsentAfterClone() {
        ItemStack item = new ItemStack(Material.DIAMOND);
        ItemMeta meta = item.getItemMeta();
        assertNotNull(meta);
        meta.setDamageResistant(DamageTypeTags.IS_FIRE);
        meta.setDamageResistant(null);
        item.setItemMeta(meta);

        ItemMeta copied = copiedMeta(item);
        assertFalse(copied.hasDamageResistant());
        assertFalse(isFireResistant(copied));
    }

    private static ItemMeta copiedMeta(ItemStack item) {
        ItemMeta copied = item.clone().getItemMeta();
        assertNotNull(copied);
        return copied;
    }

    private static boolean isFireResistant(ItemMeta meta) {
        try {
            Method method = ItemDifferenter.class.getDeclaredMethod("isFireResistant", ItemMeta.class);
            method.setAccessible(true);
            return (boolean) method.invoke(null, meta);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new AssertionError("Unexpected checked exception in production predicate", cause);
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError("Production fire predicate not available", failure);
        }
    }
}

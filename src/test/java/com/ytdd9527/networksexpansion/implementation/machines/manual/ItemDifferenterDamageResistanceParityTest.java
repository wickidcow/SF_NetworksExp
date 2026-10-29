package com.ytdd9527.networksexpansion.implementation.machines.manual;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import org.bukkit.damage.DamageType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.tag.DamageTypeTags;
import org.bukkit.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Execute the private production fire-resistance predicate without changing
 * item matching or introducing Paper 26.3-only bytecode at the 1.21.11 floor.
 */
class ItemDifferenterDamageResistanceParityTest {
    @BeforeEach
    void initializeServer() {
        MockBukkit.mock();
    }

    @AfterEach
    void closeServer() {
        MockBukkit.unmock();
    }


    @Test
    void absentDamageResistanceNeverReadsUndefinedTag() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.getDamageResistant()).thenThrow(new IllegalStateException("Absent tag"));
        assertFalse(isFireResistant(meta));
        verify(meta, never()).getDamageResistant();
    }

    @Test
    void exactHistoricalFireTagIsStillRecognized() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDamageResistant()).thenReturn(true);
        when(meta.getDamageResistant()).thenReturn(DamageTypeTags.IS_FIRE);
        assertTrue(isFireResistant(meta));
    }

    @Test
    void presentButNullTagIsNotTreatedAsFire() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDamageResistant()).thenReturn(true);
        assertFalse(isFireResistant(meta));
    }

    @Test
    void aMixedOrOtherTagMustNotBeAssumedToEqualTheFireOnlyTag() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDamageResistant()).thenReturn(true);
        when(meta.getDamageResistant()).thenReturn(otherDamageTag());
        assertFalse(isFireResistant(meta));
    }

    @Test
    void unreadablePresentTagMustNotBeSilentlyAccepted() {
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.hasDamageResistant()).thenReturn(true);
        when(meta.getDamageResistant()).thenThrow(new IllegalStateException("Unreadable saved damage tag"));
        IllegalStateException failure = assertThrows(IllegalStateException.class, () -> isFireResistant(meta));
        assertEquals("Unreadable saved damage tag", failure.getMessage());
    }

    @SuppressWarnings("unchecked") // Test-only mock of the Bukkit generic tag interface.
    private static Tag<DamageType> otherDamageTag() {
        return mock(Tag.class);
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

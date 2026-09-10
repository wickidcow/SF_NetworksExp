package io.github.sefiraat.networks.compatibility;

import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Optional;

/**
 * Normalizes Slimefun item definitions to Bukkit ItemStacks across supported core families.
 *
 * <p>Legacy/United/Gugu-style cores expose SlimefunItemStack as an ItemStack subtype, while
 * official Slimefun Experimental uses a wrapper and exposes the Bukkit stack through item().
 * Keeping that difference behind this bridge prevents either API shape from leaking into
 * Networks bytecode at Bukkit-facing call sites.</p>
 */
public final class SlimefunItemStackBridge {

    private static final ClassValue<Optional<Method>> ITEM_ACCESSOR = new ClassValue<>() {
        @Override
        protected Optional<Method> computeValue(Class<?> type) {
            try {
                return Optional.of(type.getMethod("item"));
            } catch (NoSuchMethodException ignored) {
                return Optional.empty();
            }
        }
    };

    private SlimefunItemStackBridge() {
    }

    public static @NotNull ItemStack asBukkitItemStack(@NotNull Object slimefunItemStack) {
        if (slimefunItemStack instanceof ItemStack itemStack) {
            return itemStack;
        }

        Method accessor = ITEM_ACCESSOR.get(slimefunItemStack.getClass())
            .orElseThrow(() -> new IllegalStateException(
                "Unsupported SlimefunItemStack API shape: " + slimefunItemStack.getClass().getName()));

        try {
            Object value = accessor.invoke(slimefunItemStack);
            if (value instanceof ItemStack itemStack) {
                return itemStack;
            }
            throw new IllegalStateException(
                slimefunItemStack.getClass().getName() + ".item() did not return a Bukkit ItemStack");
        } catch (IllegalAccessException exception) {
            throw new IllegalStateException("Cannot access SlimefunItemStack.item()", exception);
        } catch (InvocationTargetException exception) {
            throw new IllegalStateException("SlimefunItemStack.item() failed", exception.getCause());
        }
    }
}

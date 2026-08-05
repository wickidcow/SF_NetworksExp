package io.github.sefiraat.networks.integrations.infinityexpansion2;

import io.github.sefiraat.networks.network.barrel.InfinityExpansion2Barrel;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.libraries.dough.blocks.BlockPosition;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;

/**
 * Reflection-backed bridge for Infinity Expansion 2 storage units.
 *
 * <p>IE2 is deliberately not a compile dependency. This keeps the same Networks JAR loadable against
 * Slimefun Legacy, United and Gugu, and it lets an incompatible IE2 preview fail soft instead of preventing
 * Networks from starting. Cache access is read-only; item movement always uses IE2's live input/output slots.</p>
 */
public final class InfinityExpansion2Integration {

    public static final String PLUGIN_NAME = "InfinityExpansion2";
    public static final String STORAGE_UNIT_CLASS =
        "net.guizhanss.infinityexpansion2.implementation.items.storage.StorageUnit";
    public static final String STORAGE_CACHE_CLASS =
        "net.guizhanss.infinityexpansion2.implementation.items.storage.StorageCache";

    private final Class<?> storageUnitClass;
    private final Method getCaches;
    private final Method getCapacity;
    private final Method getInputSlots;
    private final Method getOutputSlots;
    private final Method getCacheItemStack;
    private final Method getCacheAmount;
    private final Method getCacheLimit;

    public InfinityExpansion2Integration(@NotNull Plugin ie2Plugin) throws ReflectiveOperationException {
        final ClassLoader loader = ie2Plugin.getClass().getClassLoader();
        storageUnitClass = Class.forName(STORAGE_UNIT_CLASS, false, loader);
        final Class<?> storageCacheClass = Class.forName(STORAGE_CACHE_CLASS, false, loader);

        getCaches = storageUnitClass.getMethod("getCaches");
        getCapacity = storageUnitClass.getMethod("getCapacity");
        getInputSlots = storageUnitClass.getMethod("getInputSlots");
        getOutputSlots = storageUnitClass.getMethod("getOutputSlots");
        getCacheItemStack = storageCacheClass.getMethod("getItemStack");
        getCacheAmount = storageCacheClass.getMethod("getAmount");
        getCacheLimit = storageCacheClass.getMethod("getLimit");
    }

    public boolean isStorageUnit(@Nullable SlimefunItem item) {
        return item != null && storageUnitClass.isInstance(item);
    }

    public boolean isStorageUnitItem(@NotNull ItemStack stack) {
        return isStorageUnit(SlimefunItem.getByItem(stack));
    }

    public int @NotNull [] getInputSlots(@NotNull SlimefunItem storageUnit) throws ReflectiveOperationException {
        return sanitizeSlots(invokeIntArray(getInputSlots, storageUnit));
    }

    public int @NotNull [] getOutputSlots(@NotNull SlimefunItem storageUnit) throws ReflectiveOperationException {
        return sanitizeSlots(invokeIntArray(getOutputSlots, storageUnit));
    }

    public @Nullable InfinityExpansion2Barrel getBarrel(
        @NotNull Location location,
        @NotNull SlimefunItem storageUnit,
        boolean includeEmpty
    ) throws ReflectiveOperationException {
        if (!isStorageUnit(storageUnit)) {
            return null;
        }

        final StorageSnapshot snapshot = getSnapshot(location, storageUnit);
        if (snapshot == null) {
            return null;
        }

        ItemStack template = snapshot.itemStack();
        long totalAmount = snapshot.amount();

        final var menu = com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils.getMenu(location);
        if (menu != null) {
            for (int slot : getOutputSlots(storageUnit)) {
                final ItemStack output = menu.getItemInSlot(slot);
                if (isUsable(output)) {
                    if (template == null) {
                        template = one(output);
                    }
                    if (sameItem(template, output)) {
                        totalAmount += output.getAmount();
                    }
                }
            }

            if (template == null) {
                for (int slot : getInputSlots(storageUnit)) {
                    final ItemStack input = menu.getItemInSlot(slot);
                    if (isUsable(input)) {
                        template = one(input);
                        break;
                    }
                }
            }
        }

        if (!includeEmpty && (template == null || totalAmount <= 0L)) {
            return null;
        }

        return new InfinityExpansion2Barrel(
            this,
            storageUnit,
            location,
            template,
            Math.max(0L, totalAmount),
            Math.max(1L, snapshot.limit())
        );
    }

    public @Nullable ItemStack getLiveTemplate(
        @NotNull Location location,
        @NotNull SlimefunItem storageUnit
    ) throws ReflectiveOperationException {
        final StorageSnapshot snapshot = getSnapshot(location, storageUnit);
        if (snapshot != null && snapshot.itemStack() != null) {
            return snapshot.itemStack();
        }

        final var menu = com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils.getMenu(location);
        if (menu == null) {
            return null;
        }

        for (int slot : getOutputSlots(storageUnit)) {
            final ItemStack output = menu.getItemInSlot(slot);
            if (isUsable(output)) {
                return one(output);
            }
        }
        for (int slot : getInputSlots(storageUnit)) {
            final ItemStack input = menu.getItemInSlot(slot);
            if (isUsable(input)) {
                return one(input);
            }
        }
        return null;
    }

    private @Nullable StorageSnapshot getSnapshot(
        @NotNull Location location,
        @NotNull SlimefunItem storageUnit
    ) throws ReflectiveOperationException {
        final Object rawMap = invoke(getCaches, storageUnit);
        if (!(rawMap instanceof Map<?, ?> caches)) {
            return null;
        }

        final Object cache = caches.get(new BlockPosition(location.getBlock()));
        if (cache == null) {
            return null;
        }

        final Object rawItem = invoke(getCacheItemStack, cache);
        final ItemStack item = rawItem instanceof ItemStack stack && isUsable(stack) ? one(stack) : null;
        final int amount = invokeNumber(getCacheAmount, cache).intValue();
        final int cacheLimit = invokeNumber(getCacheLimit, cache).intValue();
        final int unitCapacity = invokeNumber(getCapacity, storageUnit).intValue();
        final int limit = cacheLimit > 0 ? cacheLimit : unitCapacity;

        return new StorageSnapshot(item, Math.max(0, amount), Math.max(1, limit));
    }

    private static int @NotNull [] sanitizeSlots(int @Nullable [] slots) {
        if (slots == null || slots.length == 0) {
            return new int[0];
        }
        return java.util.Arrays.stream(slots)
            .filter(slot -> slot >= 0 && slot < 54)
            .distinct()
            .toArray();
    }

    private static int @Nullable [] invokeIntArray(@NotNull Method method, @NotNull Object target)
        throws ReflectiveOperationException {
        final Object value = invoke(method, target);
        return value instanceof int[] slots ? slots : null;
    }

    private static @NotNull Number invokeNumber(@NotNull Method method, @NotNull Object target)
        throws ReflectiveOperationException {
        final Object value = invoke(method, target);
        if (value instanceof Number number) {
            return number;
        }
        throw new ReflectiveOperationException(method.getName() + " did not return a number");
    }

    private static @Nullable Object invoke(@NotNull Method method, @NotNull Object target)
        throws ReflectiveOperationException {
        try {
            return method.invoke(target);
        } catch (InvocationTargetException exception) {
            final Throwable cause = exception.getCause();
            if (cause instanceof ReflectiveOperationException reflective) {
                throw reflective;
            }
            throw new ReflectiveOperationException(
                "Infinity Expansion 2 API invocation failed: " + method.getName(),
                cause == null ? exception : cause
            );
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            throw new ReflectiveOperationException(
                "Infinity Expansion 2 API invocation failed: " + method.getName(),
                exception
            );
        }
    }

    private static boolean sameItem(@Nullable ItemStack first, @Nullable ItemStack second) {
        return first != null && second != null && first.isSimilar(second);
    }

    private static boolean isUsable(@Nullable ItemStack stack) {
        return stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0;
    }

    private static @NotNull ItemStack one(@NotNull ItemStack stack) {
        final ItemStack clone = stack.clone();
        clone.setAmount(1);
        return clone;
    }

    private record StorageSnapshot(@Nullable ItemStack itemStack, int amount, int limit) {
    }
}

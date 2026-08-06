package io.github.sefiraat.networks.integrations.infinityexpansion2;

import io.github.sefiraat.networks.network.barrel.InfinityExpansion2Barrel;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;
import java.util.Map;

/**
 * Reflection-backed bridge for Infinity Expansion 2 storage units.
 *
 * <p>IE2 is deliberately not a compile dependency. This keeps the same Networks JAR loadable against
 * Slimefun Legacy, United and Gugu, and it lets an incompatible IE2 preview fail soft instead of preventing
 * Networks from starting. Cache access is read-only; item movement always uses IE2's live input/output slots.</p>
 *
 * <p>The bridge intentionally does not require IE2's main class to have one exact package name. Unofficial
 * builds sometimes relocate or rename the plugin entry point while preserving the storage implementation.
 * Networks first checks the official storage class, then discovers a compatible storage base class from the
 * Slimefun registry using the IE2 plugin's own class loader.</p>
 */
public final class InfinityExpansion2Integration {

    public static final String PLUGIN_NAME = "InfinityExpansion2";
    public static final String STORAGE_UNIT_CLASS =
        "net.guizhanss.infinityexpansion2.implementation.items.storage.StorageUnit";
    public static final String STORAGE_CACHE_CLASS =
        "net.guizhanss.infinityexpansion2.implementation.items.storage.StorageCache";

    private static final List<String> STORAGE_UNIT_CANDIDATES = List.of(
        STORAGE_UNIT_CLASS,
        "net.guizhanss.infinityexpansion2.items.storage.StorageUnit"
    );
    private static final List<String> STORAGE_CACHE_CANDIDATES = List.of(
        STORAGE_CACHE_CLASS,
        "net.guizhanss.infinityexpansion2.items.storage.StorageCache"
    );
    private static final List<String> POSITION_KEY_CANDIDATES = List.of(
        "io.github.thebusybiscuit.slimefun4.libraries.dough.blocks.BlockPosition",
        "io.github.bakedlibs.dough.blocks.BlockPosition"
    );

    private final ClassLoader pluginClassLoader;
    private final Class<?> storageUnitClass;
    private final Method getCaches;
    private final Method getCapacity;
    private final Method getInputSlots;
    private final Method getOutputSlots;
    private final @Nullable PositionKeyFactory positionKeyFactory;
    private volatile @Nullable CacheAccessors cacheAccessors;

    public InfinityExpansion2Integration(@NotNull Plugin ie2Plugin) throws ReflectiveOperationException {
        pluginClassLoader = ie2Plugin.getClass().getClassLoader();
        storageUnitClass = resolveStorageUnitClass(ie2Plugin);

        getCaches = storageUnitClass.getMethod("getCaches");
        getCapacity = storageUnitClass.getMethod("getCapacity");
        getInputSlots = storageUnitClass.getMethod("getInputSlots");
        getOutputSlots = storageUnitClass.getMethod("getOutputSlots");
        positionKeyFactory = resolvePositionKeyFactory();

        final Class<?> cacheClass = resolveKnownClass(STORAGE_CACHE_CANDIDATES);
        if (cacheClass != null) {
            cacheAccessors = createCacheAccessors(cacheClass);
        } else {
            final Class<?> inferredCacheClass = inferMapValueClass(getCaches.getGenericReturnType());
            if (inferredCacheClass != null && inferredCacheClass.getClassLoader() == pluginClassLoader) {
                cacheAccessors = createCacheAccessors(inferredCacheClass);
            }
        }
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

    public @NotNull String getResolvedStorageClassName() {
        return storageUnitClass.getName();
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
        if (snapshot.itemStack() != null) {
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

    private @NotNull StorageSnapshot getSnapshot(
        @NotNull Location location,
        @NotNull SlimefunItem storageUnit
    ) throws ReflectiveOperationException {
        final int unitCapacity = Math.max(1, invokeNumber(getCapacity, storageUnit).intValue());
        final Object rawMap = invoke(getCaches, storageUnit);
        if (rawMap == null) {
            return new StorageSnapshot(null, 0, unitCapacity);
        }
        if (!(rawMap instanceof Map<?, ?> caches)) {
            throw new ReflectiveOperationException("Infinity Expansion 2 getCaches() did not return a Map");
        }

        final Object cache = findCache(caches, location);
        if (cache == null) {
            return new StorageSnapshot(null, 0, unitCapacity);
        }

        final CacheAccessors accessors = getCacheAccessors(cache.getClass());
        final Object rawItem = invoke(accessors.getItemStack(), cache);
        final ItemStack item = rawItem instanceof ItemStack stack && isUsable(stack) ? one(stack) : null;
        final int amount = invokeNumber(accessors.getAmount(), cache).intValue();
        final int cacheLimit = invokeNumber(accessors.getLimit(), cache).intValue();
        final int limit = cacheLimit > 0 ? cacheLimit : unitCapacity;

        return new StorageSnapshot(item, Math.max(0, amount), Math.max(1, limit));
    }

    private @Nullable Object findCache(@NotNull Map<?, ?> caches, @NotNull Location location)
        throws ReflectiveOperationException {
        if (positionKeyFactory != null) {
            final Object key = positionKeyFactory.create(location);
            final Object direct = caches.get(key);
            if (direct != null) {
                return direct;
            }
        }

        // Unofficial IE2 builds may relocate the position-key class. Fall back to matching
        // existing keys without linking Networks against any Dough implementation.
        for (Map.Entry<?, ?> entry : caches.entrySet()) {
            if (matchesLocation(entry.getKey(), location)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private @Nullable PositionKeyFactory resolvePositionKeyFactory() {
        Class<?> keyClass = inferMapKeyClass(getCaches.getGenericReturnType());
        if (keyClass == null || keyClass == Object.class) {
            keyClass = resolveKnownClass(POSITION_KEY_CANDIDATES);
        }
        if (keyClass == null) {
            return null;
        }

        try {
            return new PositionKeyFactory(keyClass.getConstructor(Block.class), true);
        } catch (NoSuchMethodException ignored) {
            // Try a Location constructor used by some relocated compatibility implementations.
        }
        try {
            return new PositionKeyFactory(keyClass.getConstructor(Location.class), false);
        } catch (NoSuchMethodException | SecurityException ignored) {
            return null;
        }
    }

    private static boolean matchesLocation(@Nullable Object key, @NotNull Location location) {
        if (key == null) {
            return false;
        }
        if (key instanceof Location keyLocation) {
            return sameBlock(keyLocation, location);
        }

        final Object block = invokeQuietly(key, "getBlock");
        if (block instanceof Block keyBlock) {
            return sameBlock(keyBlock.getLocation(), location);
        }
        final Object keyLocation = invokeQuietly(key, "getLocation");
        if (keyLocation instanceof Location resolved) {
            return sameBlock(resolved, location);
        }
        return false;
    }

    private static @Nullable Object invokeQuietly(@NotNull Object target, @NotNull String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean sameBlock(@NotNull Location first, @NotNull Location second) {
        return first.getWorld() == second.getWorld()
            && first.getBlockX() == second.getBlockX()
            && first.getBlockY() == second.getBlockY()
            && first.getBlockZ() == second.getBlockZ();
    }

    private @NotNull Class<?> resolveStorageUnitClass(@NotNull Plugin ie2Plugin)
        throws ReflectiveOperationException {
        final Class<?> knownClass = resolveKnownClass(STORAGE_UNIT_CANDIDATES);
        if (knownClass != null && isStorageUnitShape(knownClass)) {
            return knownClass;
        }

        for (SlimefunItem item : Slimefun.getRegistry().getAllSlimefunItems()) {
            if (!belongsToPlugin(item, ie2Plugin)) {
                continue;
            }

            final Class<?> compatibleClass = findStorageBaseClass(item.getClass());
            if (compatibleClass != null) {
                return compatibleClass;
            }
        }

        throw new ClassNotFoundException(
            "Infinity Expansion 2 storage implementation unavailable; checked official class names and "
                + "registered IE2 Slimefun items. Plugin main class was not used as a compatibility gate."
        );
    }

    private boolean belongsToPlugin(@NotNull SlimefunItem item, @NotNull Plugin plugin) {
        return item.getAddon() == (Object) plugin || item.getClass().getClassLoader() == pluginClassLoader;
    }

    private @Nullable Class<?> resolveKnownClass(@NotNull List<String> candidates) {
        for (String candidate : candidates) {
            try {
                return pluginClassLoader.loadClass(candidate);
            } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
                // Try the next official or legacy package, then use registry discovery.
            }
        }
        return null;
    }

    private static @Nullable Class<?> findStorageBaseClass(@NotNull Class<?> itemClass) {
        Class<?> current = itemClass;
        while (current != null && SlimefunItem.class.isAssignableFrom(current)) {
            if (isStorageUnitShape(current)) {
                return current;
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static boolean isStorageUnitShape(@NotNull Class<?> type) {
        try {
            return Map.class.isAssignableFrom(type.getMethod("getCaches").getReturnType())
                && Number.class.isAssignableFrom(box(type.getMethod("getCapacity").getReturnType()))
                && type.getMethod("getInputSlots").getReturnType() == int[].class
                && type.getMethod("getOutputSlots").getReturnType() == int[].class;
        } catch (NoSuchMethodException | SecurityException ignored) {
            return false;
        }
    }

    private @NotNull CacheAccessors getCacheAccessors(@NotNull Class<?> cacheClass)
        throws ReflectiveOperationException {
        CacheAccessors current = cacheAccessors;
        if (current != null && current.cacheClass().isAssignableFrom(cacheClass)) {
            return current;
        }

        synchronized (this) {
            current = cacheAccessors;
            if (current == null || !current.cacheClass().isAssignableFrom(cacheClass)) {
                current = createCacheAccessors(cacheClass);
                cacheAccessors = current;
            }
            return current;
        }
    }

    private static @NotNull CacheAccessors createCacheAccessors(@NotNull Class<?> cacheClass)
        throws ReflectiveOperationException {
        return new CacheAccessors(
            cacheClass,
            cacheClass.getMethod("getItemStack"),
            cacheClass.getMethod("getAmount"),
            cacheClass.getMethod("getLimit")
        );
    }

    private static @Nullable Class<?> inferMapKeyClass(@NotNull Type mapType) {
        if (!(mapType instanceof ParameterizedType parameterizedType)) {
            return null;
        }
        final Type[] arguments = parameterizedType.getActualTypeArguments();
        return arguments.length < 1 ? null : rawClass(arguments[0]);
    }

    private static @Nullable Class<?> inferMapValueClass(@NotNull Type mapType) {
        if (!(mapType instanceof ParameterizedType parameterizedType)) {
            return null;
        }
        final Type[] arguments = parameterizedType.getActualTypeArguments();
        return arguments.length < 2 ? null : rawClass(arguments[1]);
    }

    private static @Nullable Class<?> rawClass(@NotNull Type type) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType
            && parameterizedType.getRawType() instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof WildcardType wildcardType) {
            final Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length > 0) {
                return rawClass(upperBounds[0]);
            }
        }
        return null;
    }

    private static @NotNull Class<?> box(@NotNull Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == int.class) {
            return Integer.class;
        }
        if (type == long.class) {
            return Long.class;
        }
        if (type == short.class) {
            return Short.class;
        }
        if (type == byte.class) {
            return Byte.class;
        }
        if (type == float.class) {
            return Float.class;
        }
        if (type == double.class) {
            return Double.class;
        }
        return type;
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

    private record PositionKeyFactory(@NotNull Constructor<?> constructor, boolean fromBlock) {
        private @NotNull Object create(@NotNull Location location) throws ReflectiveOperationException {
            try {
                return constructor.newInstance(fromBlock ? location.getBlock() : location);
            } catch (InvocationTargetException exception) {
                final Throwable cause = exception.getCause();
                throw new ReflectiveOperationException(
                    "Infinity Expansion 2 position-key construction failed",
                    cause == null ? exception : cause
                );
            }
        }
    }

    private record CacheAccessors(
        @NotNull Class<?> cacheClass,
        @NotNull Method getItemStack,
        @NotNull Method getAmount,
        @NotNull Method getLimit
    ) {
    }

    private record StorageSnapshot(@Nullable ItemStack itemStack, int amount, int limit) {
    }
}

package io.github.sefiraat.networks.integrations.infinityexpansion2;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.integrations.storage.StorageAdapter;
import io.github.sefiraat.networks.network.barrel.InfinityExpansion2Barrel;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection-backed bridge for Infinity Expansion 2 storage units.
 *
 * <p>IE2 is deliberately not a compile dependency. The bridge is also deliberately lazy: merely enabling
 * InfinityExpansion2 never requires Networks to load one exact IE2 implementation class. Preview, unofficial
 * and relocated builds can therefore finish registering their Slimefun items normally. Networks learns the
 * storage contract from the real {@link SlimefunItem} instance when a storage unit is first encountered.</p>
 *
 * <p>Only the stable behavioural shape is required: a capacity plus input/output slots. Cache access is an
 * optional read optimization. If a preview does not expose the cache, Networks falls back to IE2's persisted
 * {@code stored_amount} value and still performs all writes through IE2's real menu slots. Networks never
 * writes IE2's cache or persisted amount directly.</p>
 */
public final class InfinityExpansion2Integration implements StorageAdapter {

    public static final String PLUGIN_NAME = "InfinityExpansion2";

    /** Current upstream package, retained as a diagnostic marker only. It is never a startup gate. */
    public static final String STORAGE_UNIT_CLASS =
        "net.guizhanss.infinityexpansion2.implementation.items.storage.StorageUnit";
    public static final String STORAGE_CACHE_CLASS =
        "net.guizhanss.infinityexpansion2.implementation.items.storage.StorageCache";

    private static final String BS_AMOUNT = "stored_amount";
    private static final String LAZY_DESCRIPTION = "lazy-runtime-discovery";

    private final Plugin ie2Plugin;
    private final ClassLoader pluginClassLoader;
    private final Map<Class<?>, StorageUnitAccessors> storageAccessors = new ConcurrentHashMap<>();
    private final Set<Class<?>> unsupportedItemClasses = ConcurrentHashMap.newKeySet();
    private final Map<Class<?>, CacheAccessors> cacheAccessors = new ConcurrentHashMap<>();
    private final Map<Class<?>, PositionKeyFactory> positionKeyFactories = new ConcurrentHashMap<>();
    private final Set<Class<?>> unsupportedPositionKeyClasses = ConcurrentHashMap.newKeySet();

    private volatile @Nullable Class<?> resolvedStorageUnitClass;
    private volatile @Nullable String discoveryDiagnostic;

    /**
     * Creates a lazy IE2 adapter. No IE2 implementation class is loaded here on purpose.
     *
     * @param ie2Plugin the enabled InfinityExpansion2 plugin
     */
    public InfinityExpansion2Integration(@NotNull Plugin ie2Plugin) throws ReflectiveOperationException {
        this.ie2Plugin = ie2Plugin;
        this.pluginClassLoader = ie2Plugin.getClass().getClassLoader();
    }

    @Override
    public @NotNull String integrationName() {
        return PLUGIN_NAME;
    }

    @Override
    public @NotNull String implementationDescription() {
        return getResolvedStorageClassName();
    }

    @Override
    public boolean supports(@Nullable SlimefunItem item) {
        return isStorageUnit(item);
    }

    @Override
    public @Nullable BarrelIdentity createBarrel(
        @NotNull Location location,
        @NotNull SlimefunItem item,
        boolean includeEmpty
    ) throws ReflectiveOperationException {
        return getBarrel(location, item, includeEmpty);
    }

    /**
     * Returns true only for an IE2 item that exposes the storage-unit transport contract.
     * Discovery is based on the already-loaded item instance, not ClassLoader.loadClass().
     */
    public boolean isStorageUnit(@Nullable SlimefunItem item) {
        if (item == null || !belongsToPlugin(item)) {
            return false;
        }

        final Class<?> itemClass = item.getClass();
        final Class<?> resolved = resolvedStorageUnitClass;
        if (resolved != null && resolved.isAssignableFrom(itemClass)) {
            return true;
        }
        if (storageAccessors.containsKey(itemClass)) {
            return true;
        }
        if (unsupportedItemClasses.contains(itemClass) || !looksLikeStorageCandidate(item)) {
            return false;
        }

        try {
            final StorageUnitAccessors accessors = createStorageAccessors(itemClass);
            storageAccessors.put(itemClass, accessors);
            resolvedStorageUnitClass = mostSpecificStorageClass(itemClass, accessors);
            discoveryDiagnostic = null;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            unsupportedItemClasses.add(itemClass);
            discoveryDiagnostic = describeFailure(exception);
            return false;
        }
    }

    public boolean isStorageUnitItem(@NotNull ItemStack stack) {
        return isStorageUnit(SlimefunItem.getByItem(stack));
    }

    public int @NotNull [] getInputSlots(@NotNull SlimefunItem storageUnit) throws ReflectiveOperationException {
        final StorageUnitAccessors accessors = accessorsFor(storageUnit);
        return sanitizeSlots(invokeIntArray(accessors.getInputSlots(), storageUnit));
    }

    public int @NotNull [] getOutputSlots(@NotNull SlimefunItem storageUnit) throws ReflectiveOperationException {
        final StorageUnitAccessors accessors = accessorsFor(storageUnit);
        return sanitizeSlots(invokeIntArray(accessors.getOutputSlots(), storageUnit));
    }

    public boolean hasResolvedStorageClass() {
        return resolvedStorageUnitClass != null;
    }

    public @NotNull String getResolvedStorageClassName() {
        final Class<?> resolved = resolvedStorageUnitClass;
        return resolved == null ? LAZY_DESCRIPTION : resolved.getName();
    }

    public @Nullable String getDiscoveryDiagnostic() {
        return discoveryDiagnostic;
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

        final var menu = StorageCacheUtils.getMenu(location);
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

        final var menu = StorageCacheUtils.getMenu(location);
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
        final StorageUnitAccessors accessors = accessorsFor(storageUnit);
        final int unitCapacity = Math.max(1, invokeNumber(accessors.getCapacity(), storageUnit).intValue());
        final StorageSnapshot persisted = persistedSnapshot(location, unitCapacity);

        final Method cachesMethod = accessors.getCaches();
        if (cachesMethod == null) {
            return persisted;
        }

        try {
            final Object rawMap = invoke(cachesMethod, storageUnit);
            if (!(rawMap instanceof Map<?, ?> caches)) {
                discoveryDiagnostic = "getCaches() did not return a Map; using persisted storage state";
                return persisted;
            }

            final Object cache = findCache(caches, location);
            if (cache == null) {
                return persisted;
            }

            final CacheAccessors cacheApi = getCacheAccessors(cache.getClass());
            if (cacheApi.getAmount() == null) {
                discoveryDiagnostic = "cache amount accessor unavailable; using persisted storage state";
                return persisted;
            }

            ItemStack item = null;
            if (cacheApi.getItemStack() != null) {
                final Object rawItem = invoke(cacheApi.getItemStack(), cache);
                if (rawItem instanceof ItemStack stack && isUsable(stack)) {
                    item = one(stack);
                }
            }

            final int amount = Math.max(0, invokeNumber(cacheApi.getAmount(), cache).intValue());
            int limit = unitCapacity;
            if (cacheApi.getLimit() != null) {
                final int cacheLimit = invokeNumber(cacheApi.getLimit(), cache).intValue();
                if (cacheLimit > 0) {
                    limit = cacheLimit;
                }
            }
            discoveryDiagnostic = null;
            return new StorageSnapshot(item, amount, Math.max(1, limit));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            // Cache internals are not part of the required bridge contract. Keep IE2 active and fall back
            // to its persisted amount rather than disabling the entire optional integration.
            discoveryDiagnostic = describeFailure(exception) + "; using persisted storage state";
            return persisted;
        }
    }

    private static @NotNull StorageSnapshot persistedSnapshot(@NotNull Location location, int unitCapacity) {
        int amount = 0;
        try {
            final SlimefunBlockData blockData = StorageCacheUtils.getBlock(location);
            if (blockData != null) {
                final String value = blockData.getData(BS_AMOUNT);
                if (value != null && !value.isBlank()) {
                    final long parsed = Long.parseLong(value);
                    amount = (int) Math.max(0L, Math.min(Integer.MAX_VALUE, parsed));
                }
            }
        } catch (RuntimeException | LinkageError ignored) {
            // A malformed or unavailable preview-specific persistence value simply means zero cached amount.
            // The real menu output is still counted by getBarrel().
        }
        return new StorageSnapshot(null, amount, Math.max(1, unitCapacity));
    }

    private @Nullable Object findCache(@NotNull Map<?, ?> caches, @NotNull Location location) {
        if (caches.isEmpty()) {
            return null;
        }

        Object sampleKey = null;
        for (Object key : caches.keySet()) {
            if (key != null) {
                sampleKey = key;
                break;
            }
        }

        if (sampleKey != null) {
            final Class<?> keyClass = sampleKey.getClass();
            PositionKeyFactory factory = positionKeyFactories.get(keyClass);
            if (factory == null && !unsupportedPositionKeyClasses.contains(keyClass)) {
                factory = createPositionKeyFactory(keyClass);
                if (factory == null) {
                    unsupportedPositionKeyClasses.add(keyClass);
                } else {
                    positionKeyFactories.put(keyClass, factory);
                }
            }

            if (factory != null) {
                try {
                    final Object direct = caches.get(factory.create(location));
                    if (direct != null) {
                        return direct;
                    }
                } catch (ReflectiveOperationException | RuntimeException | LinkageError ignored) {
                    positionKeyFactories.remove(keyClass);
                    unsupportedPositionKeyClasses.add(keyClass);
                }
            }
        }

        // Relocated/unofficial key types can still be matched without linking Networks against Dough.
        for (Map.Entry<?, ?> entry : caches.entrySet()) {
            if (matchesLocation(entry.getKey(), location)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static @Nullable PositionKeyFactory createPositionKeyFactory(@NotNull Class<?> keyClass) {
        try {
            return new PositionKeyFactory(keyClass.getConstructor(Block.class), true);
        } catch (NoSuchMethodException | SecurityException ignored) {
            // Try Location next.
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

        final Object worldValue = firstNonNull(invokeQuietly(key, "getWorld"), invokeQuietly(key, "world"));
        final Number x = asNumber(firstNonNull(invokeQuietly(key, "getX"), invokeQuietly(key, "x")));
        final Number y = asNumber(firstNonNull(invokeQuietly(key, "getY"), invokeQuietly(key, "y")));
        final Number z = asNumber(firstNonNull(invokeQuietly(key, "getZ"), invokeQuietly(key, "z")));
        return worldMatches(worldValue, location.getWorld())
            && x != null && x.intValue() == location.getBlockX()
            && y != null && y.intValue() == location.getBlockY()
            && z != null && z.intValue() == location.getBlockZ();
    }

    private static @Nullable Object firstNonNull(@Nullable Object first, @Nullable Object second) {
        return first != null ? first : second;
    }

    private static @Nullable Number asNumber(@Nullable Object value) {
        return value instanceof Number number ? number : null;
    }

    private static boolean worldMatches(@Nullable Object value, @Nullable World world) {
        if (value == null || world == null) {
            return false;
        }
        if (value == world) {
            return true;
        }
        if (value instanceof World keyWorld) {
            return keyWorld.getUID().equals(world.getUID());
        }
        return world.getUID().toString().equalsIgnoreCase(String.valueOf(value))
            || world.getName().equalsIgnoreCase(String.valueOf(value));
    }

    private static @Nullable Object invokeQuietly(@NotNull Object target, @NotNull String methodName) {
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException | LinkageError ignored) {
            return null;
        }
    }

    private static boolean sameBlock(@NotNull Location first, @NotNull Location second) {
        return first.getWorld() == second.getWorld()
            && first.getBlockX() == second.getBlockX()
            && first.getBlockY() == second.getBlockY()
            && first.getBlockZ() == second.getBlockZ();
    }

    private boolean belongsToPlugin(@NotNull SlimefunItem item) {
        final Object addon = item.getAddon();
        if (addon == (Object) ie2Plugin || item.getClass().getClassLoader() == pluginClassLoader) {
            return true;
        }

        final String itemClassName = item.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        if (itemClassName.contains("infinityexpansion2")) {
            return true;
        }

        if (addon != null) {
            final Class<?> addonClass = addon.getClass();
            return addonClass.getClassLoader() == pluginClassLoader
                || addonClass.getName().toLowerCase(java.util.Locale.ROOT).contains("infinityexpansion2");
        }
        return false;
    }

    private static boolean looksLikeStorageCandidate(@NotNull SlimefunItem item) {
        Class<?> current = item.getClass();
        while (current != null && SlimefunItem.class.isAssignableFrom(current)) {
            final String className = current.getName().toLowerCase(java.util.Locale.ROOT);
            if (className.contains("storageunit") || className.contains(".items.storage.")) {
                return true;
            }
            current = current.getSuperclass();
        }

        try {
            final String id = item.getId();
            return id != null && id.toLowerCase(java.util.Locale.ROOT).contains("storage_unit");
        } catch (RuntimeException | LinkageError ignored) {
            return false;
        }
    }

    private @NotNull StorageUnitAccessors accessorsFor(@NotNull SlimefunItem storageUnit)
        throws ReflectiveOperationException {
        StorageUnitAccessors accessors = storageAccessors.get(storageUnit.getClass());
        if (accessors != null) {
            return accessors;
        }
        if (!belongsToPlugin(storageUnit) || !looksLikeStorageCandidate(storageUnit)) {
            throw new ReflectiveOperationException("Slimefun item is not an Infinity Expansion 2 storage unit");
        }

        try {
            accessors = createStorageAccessors(storageUnit.getClass());
            storageAccessors.put(storageUnit.getClass(), accessors);
            resolvedStorageUnitClass = mostSpecificStorageClass(storageUnit.getClass(), accessors);
            discoveryDiagnostic = null;
            return accessors;
        } catch (RuntimeException | LinkageError exception) {
            discoveryDiagnostic = describeFailure(exception);
            throw new ReflectiveOperationException("Infinity Expansion 2 storage API discovery failed", exception);
        }
    }

    private static @NotNull StorageUnitAccessors createStorageAccessors(@NotNull Class<?> itemClass)
        throws ReflectiveOperationException {
        final Method capacity = itemClass.getMethod("getCapacity");
        final Method inputSlots = itemClass.getMethod("getInputSlots");
        final Method outputSlots = itemClass.getMethod("getOutputSlots");
        if (!Number.class.isAssignableFrom(box(capacity.getReturnType()))) {
            throw new NoSuchMethodException("getCapacity() does not return a number");
        }
        if (inputSlots.getReturnType() != int[].class || outputSlots.getReturnType() != int[].class) {
            throw new NoSuchMethodException("getInputSlots()/getOutputSlots() do not return int[]");
        }

        Method caches = null;
        try {
            final Method candidate = itemClass.getMethod("getCaches");
            if (Map.class.isAssignableFrom(candidate.getReturnType())) {
                caches = candidate;
            }
        } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
            // getCaches() is optional. Persisted storage state is the compatibility fallback.
        }
        return new StorageUnitAccessors(itemClass, capacity, inputSlots, outputSlots, caches);
    }

    private static @NotNull Class<?> mostSpecificStorageClass(
        @NotNull Class<?> itemClass,
        @NotNull StorageUnitAccessors accessors
    ) {
        Class<?> declared = accessors.getCapacity().getDeclaringClass();
        if (declared != null && SlimefunItem.class.isAssignableFrom(declared)) {
            return declared;
        }
        return itemClass;
    }

    private @NotNull CacheAccessors getCacheAccessors(@NotNull Class<?> cacheClass) {
        return cacheAccessors.computeIfAbsent(cacheClass, InfinityExpansion2Integration::createCacheAccessors);
    }

    private static @NotNull CacheAccessors createCacheAccessors(@NotNull Class<?> cacheClass) {
        return new CacheAccessors(
            cacheClass,
            findPublicNoArgMethod(cacheClass, "getItemStack"),
            findPublicNoArgMethod(cacheClass, "getAmount"),
            findPublicNoArgMethod(cacheClass, "getLimit")
        );
    }

    private static @Nullable Method findPublicNoArgMethod(@NotNull Class<?> type, @NotNull String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException | SecurityException | LinkageError ignored) {
            return null;
        }
    }

    private static @NotNull String describeFailure(@NotNull Throwable throwable) {
        final String message = throwable.getMessage();
        return throwable.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
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

    private record StorageUnitAccessors(
        @NotNull Class<?> itemClass,
        @NotNull Method getCapacity,
        @NotNull Method getInputSlots,
        @NotNull Method getOutputSlots,
        @Nullable Method getCaches
    ) {
    }

    private record CacheAccessors(
        @NotNull Class<?> cacheClass,
        @Nullable Method getItemStack,
        @Nullable Method getAmount,
        @Nullable Method getLimit
    ) {
    }

    private record StorageSnapshot(@Nullable ItemStack itemStack, int amount, int limit) {
    }
}

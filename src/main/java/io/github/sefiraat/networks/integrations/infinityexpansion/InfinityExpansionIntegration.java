package io.github.sefiraat.networks.integrations.infinityexpansion;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.integrations.storage.StorageAdapter;
import io.github.sefiraat.networks.network.barrel.LegacyInfinityExpansionBarrel;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reflection-backed storage bridge for the original Infinity Expansion plugin.
 *
 * <p>The original Networks implementation linked directly against IE1's StorageUnit and StorageCache
 * classes. That makes the JVM resolve an optional plugin class while loading NetworkRoot and can disable
 * Networks on servers that run IE2 without IE1. This adapter discovers the old storage contract from the
 * live Slimefun item instead, so IE1 remains supported without becoming a runtime dependency.</p>
 */
public final class InfinityExpansionIntegration implements StorageAdapter {

    public static final String PLUGIN_NAME = "InfinityExpansion";

    private static final String STORED_AMOUNT = "stored";
    private static final int OUTPUT_SLOT = 16;
    private static final String LAZY_DESCRIPTION = "reflection-runtime-discovery";

    private final Plugin plugin;
    private final ClassLoader pluginClassLoader;
    private final Map<Class<?>, StorageAccessors> accessors = new ConcurrentHashMap<>();
    private final Set<Class<?>> unsupported = ConcurrentHashMap.newKeySet();
    private volatile @Nullable Class<?> resolvedStorageUnitClass;

    public InfinityExpansionIntegration(@NotNull Plugin plugin) {
        this.plugin = plugin;
        this.pluginClassLoader = plugin.getClass().getClassLoader();
    }

    @Override
    public @NotNull String integrationName() {
        return PLUGIN_NAME;
    }

    @Override
    public @NotNull String implementationDescription() {
        final Class<?> resolved = resolvedStorageUnitClass;
        return resolved == null ? LAZY_DESCRIPTION : resolved.getName();
    }

    @Override
    public boolean supports(@Nullable SlimefunItem item) {
        if (item == null || !belongsToPlugin(item)) {
            return false;
        }

        final Class<?> itemClass = item.getClass();
        final Class<?> resolved = resolvedStorageUnitClass;
        if (resolved != null && resolved.isAssignableFrom(itemClass)) {
            return true;
        }
        if (accessors.containsKey(itemClass)) {
            return true;
        }
        if (unsupported.contains(itemClass) || !looksLikeStorageUnit(itemClass)) {
            return false;
        }

        try {
            accessors.put(itemClass, discoverAccessors(itemClass));
            resolvedStorageUnitClass = itemClass;
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
            unsupported.add(itemClass);
            return false;
        }
    }

    @Override
    public @Nullable BarrelIdentity createBarrel(
        @NotNull Location location,
        @NotNull SlimefunItem item,
        boolean includeEmpty
    ) throws ReflectiveOperationException {
        final StorageAccessors api = accessorsFor(item);
        final BlockMenu menu = StorageCacheUtils.getMenu(location);
        if (menu == null) {
            return null;
        }

        final SlimefunBlockData data = StorageCacheUtils.getBlock(location);
        if (data == null) {
            return null;
        }

        final long stored = parseStoredAmount(data.getData(STORED_AMOUNT));
        final ItemStack output = menu.getItemInSlot(OUTPUT_SLOT);
        final boolean hasOutput = output != null && output.getType() != Material.AIR && output.getAmount() > 0;

        if (!includeEmpty && !hasOutput) {
            return null;
        }

        final Object cache = api.getCache().invoke(item, location);
        if (cache == null) {
            return null;
        }

        final Method depositAll = cache.getClass().getMethod("depositAll", ItemStack[].class, boolean.class);
        final long capacity = Math.max(1L, ((Number) api.capacity().get(item)).longValue());

        final ItemStack template;
        if (hasOutput) {
            template = output.clone();
            template.setAmount(1);
        } else {
            template = null;
        }

        return new LegacyInfinityExpansionBarrel(
            location,
            template,
            Math.max(0L, stored) + (hasOutput ? output.getAmount() : 0L),
            capacity,
            cache,
            depositAll
        );
    }

    private @NotNull StorageAccessors accessorsFor(@NotNull SlimefunItem item) throws ReflectiveOperationException {
        final StorageAccessors cached = accessors.get(item.getClass());
        if (cached != null) {
            return cached;
        }
        if (!supports(item)) {
            throw new NoSuchMethodException("IE1 item does not expose the legacy storage-unit contract: "
                + item.getClass().getName());
        }
        final StorageAccessors discovered = accessors.get(item.getClass());
        if (discovered == null) {
            throw new NoSuchMethodException("IE1 storage accessors were not retained for " + item.getClass().getName());
        }
        return discovered;
    }

    private boolean belongsToPlugin(@NotNull SlimefunItem item) {
        if (item.getClass().getClassLoader() == pluginClassLoader) {
            return true;
        }
        final Object addon = item.getAddon();
        if (addon == plugin) {
            return true;
        }
        return addon != null && addon.getClass().getClassLoader() == pluginClassLoader;
    }

    private static boolean looksLikeStorageUnit(@NotNull Class<?> itemClass) {
        if (!"StorageUnit".equals(itemClass.getSimpleName())) {
            return false;
        }
        try {
            itemClass.getMethod("getCache", Location.class);
            findCapacityField(itemClass);
            return true;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return false;
        }
    }

    private static @NotNull StorageAccessors discoverAccessors(@NotNull Class<?> itemClass)
        throws ReflectiveOperationException {
        final Method getCache = itemClass.getMethod("getCache", Location.class);
        final Field capacity = findCapacityField(itemClass);
        if (!capacity.trySetAccessible() && !capacity.canAccess(null)) {
            capacity.setAccessible(true);
        }
        return new StorageAccessors(getCache, capacity);
    }

    private static @NotNull Field findCapacityField(@NotNull Class<?> type) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField("max");
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException("max");
    }

    private static long parseStoredAmount(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private record StorageAccessors(@NotNull Method getCache, @NotNull Field capacity) {
    }
}

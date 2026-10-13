package io.github.sefiraat.networks.slimefun.network;

import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Per-Quantum-cache lifecycle coordination. Never synchronizes unrelated storages.
 *
 * A tick or network transfer may have obtained a cache before a break removed it.
 * Acquiring that cache's monitor and checking map ownership together ensures a
 * stale reference cannot mutate already-serialized storage contents.
 */
public final class QuantumCacheLifecycle {
    // Used on cache startup, break and placement only; normal ticks use the cache monitor.
    private static final Object[] INITIALIZATION_LOCKS = new Object[256];
    private static final Map<Location, Object> RECENTLY_BROKEN = new ConcurrentHashMap<>();

    static {
        for (int i = 0; i < INITIALIZATION_LOCKS.length; i++) {
            INITIALIZATION_LOCKS[i] = new Object();
        }
    }

    private QuantumCacheLifecycle() {
    }

    public static @NotNull Object initializationLock(@NotNull Location location) {
        return INITIALIZATION_LOCKS[location.hashCode() & (INITIALIZATION_LOCKS.length - 1)];
    }

    public static @NotNull Object markRecentlyBroken(@NotNull Location location) {
        final Object token = new Object();
        RECENTLY_BROKEN.put(location.clone(), token);
        return token;
    }

    public static boolean isRecentlyBroken(@NotNull Location location) {
        return RECENTLY_BROKEN.containsKey(location);
    }

    public static void clearBreakMarker(@NotNull Location location) {
        RECENTLY_BROKEN.remove(location);
    }

    // Old scheduled cleanup cannot clear a later break marker at the same position.
    public static void clearBreakMarker(@NotNull Location location, @NotNull Object token) {
        RECENTLY_BROKEN.remove(location, token);
    }

    public static boolean withCurrent(
        @NotNull Map<Location, QuantumCache> caches,
        @NotNull Location location,
        @NotNull QuantumCache candidate,
        @NotNull Consumer<QuantumCache> operation
    ) {
        synchronized (candidate) {
            if (caches.get(location) != candidate) {
                return false;
            }
            operation.accept(candidate);
            return true;
        }
    }

    @Nullable
    public static <T> T readCurrent(
        @NotNull Map<Location, QuantumCache> caches,
        @NotNull Location location,
        @NotNull QuantumCache candidate,
        @NotNull Supplier<T> operation
    ) {
        synchronized (candidate) {
            if (caches.get(location) != candidate) {
                return null;
            }
            return operation.get();
        }
    }

    /**
     * Waits for an in-flight tick or withdrawal before detaching storage. The
     * callback serializes the final cache and drops physical slots while holding
     * its monitor. Callers must not republish detached caches afterward.
     */
    public static boolean retireCurrent(
        @NotNull Map<Location, QuantumCache> caches,
        @NotNull Location location,
        @NotNull Consumer<QuantumCache> onRetire
    ) {
        while (true) {
            final QuantumCache candidate = caches.get(location);
            if (candidate == null) {
                return false;
            }
            synchronized (candidate) {
                if (caches.get(location) != candidate) {
                    continue;
                }
                // Keep the old cache mapped during the break snapshot. A second
                // asynchronous tick should find this cache and block on its monitor,
                // rather than seeing a temporary empty entry and reinitializing it.
                try {
                    onRetire.accept(candidate);
                } finally {
                    // Detach only after the serialized drop/slot operations finish.
                    // A queued tick that held the old reference now fails ownership.
                    caches.remove(location, candidate);
                }
                return true;
            }
        }
    }
}

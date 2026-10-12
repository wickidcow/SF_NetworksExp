package io.github.sefiraat.networks.slimefun.network;

import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
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
    private QuantumCacheLifecycle() {
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
                if (!caches.remove(location, candidate)) {
                    continue;
                }
                onRetire.accept(candidate);
                return true;
            }
        }
    }
}

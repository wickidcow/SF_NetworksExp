package io.github.sefiraat.networks.integrations.storage;

import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/** Runtime registry for fail-soft optional storage adapters. */
public final class StorageAdapterRegistry {

    private final CopyOnWriteArrayList<StorageAdapter> adapters = new CopyOnWriteArrayList<>();
    private final Map<String, StorageAdapter> byIntegration = new ConcurrentHashMap<>();
    private final BiConsumer<String, Throwable> failureHandler;

    public StorageAdapterRegistry(@NotNull BiConsumer<String, Throwable> failureHandler) {
        this.failureHandler = failureHandler;
    }

    public void register(@NotNull StorageAdapter adapter) {
        StorageAdapter previous = byIntegration.put(adapter.integrationName(), adapter);
        if (previous != null) {
            adapters.remove(previous);
        }
        adapters.add(adapter);
    }

    public void unregister(@NotNull String integrationName) {
        StorageAdapter adapter = byIntegration.remove(integrationName);
        if (adapter != null) {
            adapters.remove(adapter);
        }
    }

    public @Nullable BarrelIdentity findBarrel(
        @NotNull Location location,
        @Nullable SlimefunItem item,
        boolean includeEmpty
    ) {
        if (item == null) {
            return null;
        }

        for (StorageAdapter adapter : adapters) {
            try {
                if (!adapter.supports(item)) {
                    continue;
                }
                return adapter.createBarrel(location, item, includeEmpty);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError exception) {
                unregister(adapter.integrationName());
                failureHandler.accept(adapter.integrationName(), exception);
            }
        }
        return null;
    }

    public @NotNull List<String> descriptions() {
        List<String> descriptions = new ArrayList<>();
        for (StorageAdapter adapter : adapters) {
            descriptions.add(adapter.integrationName() + '=' + adapter.implementationDescription());
        }
        Collections.sort(descriptions);
        return descriptions;
    }

    public int size() {
        return adapters.size();
    }

    public void clear() {
        adapters.clear();
        byIntegration.clear();
    }
}

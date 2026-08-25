package io.github.sefiraat.networks.integrations.storage;

import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Stable internal contract for optional storage plugins.
 *
 * <p>Adapters are discovered at runtime and are never bundled with their target plugin API. A broken adapter
 * is removed independently so the native Networks storage path remains available.</p>
 */
public interface StorageAdapter {

    @NotNull String integrationName();

    @NotNull String implementationDescription();

    boolean supports(@Nullable SlimefunItem item);

    @Nullable BarrelIdentity createBarrel(
        @NotNull Location location,
        @NotNull SlimefunItem item,
        boolean includeEmpty
    ) throws ReflectiveOperationException;
}

package io.github.sefiraat.networks.internal.ie1link;

import org.bukkit.Location;

/**
 * Inert bytecode linkage target for obsolete direct IE1 references.
 *
 * <p>The Shadow relocation rewrites the old optional IE1 class descriptor to this local type. Real IE1
 * objects never extend this class, so the obsolete direct branch is skipped and the reflection adapter is
 * used instead. Do not use this class for runtime integration.</p>
 */
@Deprecated
public abstract class StorageUnit {

    public abstract StorageCache getCache(Location location);
}

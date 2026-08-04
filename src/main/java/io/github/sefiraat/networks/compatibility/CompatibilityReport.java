package io.github.sefiraat.networks.compatibility;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable runtime compatibility diagnosis. */
public final class CompatibilityReport {

    private final CoreVariant coreVariant;
    private final String coreVersion;
    private final String minecraftVersion;
    private final int javaFeature;
    private final boolean supported;
    private final List<String> warnings;

    CompatibilityReport(
        @NotNull CoreVariant coreVariant,
        @NotNull String coreVersion,
        @NotNull String minecraftVersion,
        int javaFeature,
        boolean supported,
        @NotNull List<String> warnings
    ) {
        this.coreVariant = coreVariant;
        this.coreVersion = coreVersion;
        this.minecraftVersion = minecraftVersion;
        this.javaFeature = javaFeature;
        this.supported = supported;
        this.warnings = Collections.unmodifiableList(new ArrayList<>(warnings));
    }

    public @NotNull CoreVariant getCoreVariant() {
        return coreVariant;
    }

    public @NotNull String getCoreVersion() {
        return coreVersion;
    }

    public @NotNull String getMinecraftVersion() {
        return minecraftVersion;
    }

    public int getJavaFeature() {
        return javaFeature;
    }

    public boolean isSupported() {
        return supported;
    }

    public @NotNull List<String> getWarnings() {
        return warnings;
    }
}

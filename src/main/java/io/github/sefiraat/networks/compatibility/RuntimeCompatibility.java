package io.github.sefiraat.networks.compatibility;

import io.github.sefiraat.networks.Networks;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Detects supported Slimefun cores and enforces the Java/Minecraft floor before item registration. */
public final class RuntimeCompatibility {

    public static final String MINIMUM_MINECRAFT = "1.21.11";
    public static final int MINIMUM_JAVA = 21;
    private static final Pattern VERSION_COMPONENT = Pattern.compile("(\\d+)");
    private static final String GUGU_MARKER_CLASS = "city.norain.slimefun4.api.menu.UniversalMenu";
    private static final String LEGACY_MARKER_CLASS =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctor";

    private RuntimeCompatibility() {
    }

    public static @NotNull CompatibilityReport inspect(@NotNull Networks plugin) {
        Plugin slimefun = Bukkit.getPluginManager().getPlugin("Slimefun");
        String coreVersion = slimefun == null ? "missing" : slimefun.getDescription().getVersion();
        CoreVariant coreVariant = detectCore(slimefun);
        String minecraftVersion = Bukkit.getMinecraftVersion();
        int javaFeature = Runtime.version().feature();
        List<String> warnings = new ArrayList<>();

        boolean supported = true;
        if (slimefun == null || !slimefun.isEnabled()) {
            warnings.add("The Slimefun plugin is missing or disabled.");
            supported = false;
        }
        if (javaFeature < MINIMUM_JAVA) {
            warnings.add("Java " + MINIMUM_JAVA + "+ is required; detected Java " + javaFeature + '.');
            supported = false;
        }
        if (compareVersions(minecraftVersion, MINIMUM_MINECRAFT) < 0) {
            warnings.add("Minecraft " + MINIMUM_MINECRAFT + "+ is required; detected " + minecraftVersion + '.');
            supported = false;
        }
        if (!coreVariant.isExplicitlySupported()) {
            warnings.add("The detected Slimefun core is not in the tested Legacy/United/Gugu compatibility set.");
            if (!plugin.getConfig().getBoolean("compatibility.allow-unknown-slimefun-core", false)) {
                supported = false;
            }
        }

        if (plugin.getConfig().getBoolean("compatibility.allow-unsupported-runtime", false)) {
            supported = slimefun != null && slimefun.isEnabled();
            if (!warnings.isEmpty()) {
                warnings.add("Unsupported-runtime override is enabled; startup is continuing at your own risk.");
            }
        }

        return new CompatibilityReport(coreVariant, coreVersion, minecraftVersion, javaFeature, supported, warnings);
    }

    static @NotNull CoreVariant detectCore(Plugin plugin) {
        if (plugin == null) {
            return CoreVariant.OFFICIAL_OR_UNKNOWN;
        }

        PluginDescriptionFile description = plugin.getDescription();
        String fingerprint = String.join(" ",
            description.getName(),
            description.getVersion(),
            nullToEmpty(description.getDescription()),
            nullToEmpty(description.getWebsite()))
            .toLowerCase(Locale.ROOT);

        return classifyCore(
            fingerprint,
            hasPluginClass(plugin, LEGACY_MARKER_CLASS),
            hasUnitedCommandAlias(description),
            hasPluginClass(plugin, GUGU_MARKER_CLASS));
    }

    /** Package-private pure classifier so core fingerprint behavior can be regression-tested without Bukkit mocks. */
    static @NotNull CoreVariant classifyCore(
        @NotNull String fingerprint,
        boolean legacyMarker,
        boolean unitedCommandAlias,
        boolean guguMarker
    ) {
        String normalized = fingerprint.toLowerCase(Locale.ROOT);
        if (normalized.contains("slimefun legacy")
            || normalized.contains("slimefun-legacy")
            || normalized.contains("wickidcow")) {
            return CoreVariant.SLIMEFUN_LEGACY;
        }
        if (normalized.contains("slimefun united")
            || normalized.contains("slimefun-united")
            || normalized.contains("slimefun_united")
            || unitedCommandAlias) {
            return CoreVariant.SLIMEFUN_UNITED;
        }
        if (normalized.contains("gugu")
            || normalized.contains("slimefunguguproject")
            || guguMarker) {
            return CoreVariant.SLIMEFUN_GUGU;
        }
        if (legacyMarker) {
            return CoreVariant.SLIMEFUN_LEGACY;
        }
        return CoreVariant.OFFICIAL_OR_UNKNOWN;
    }

    /**
     * Checks a core-owned class without initializing it. Gugu retains the original Slimefun plugin metadata,
     * so its unique API package is the reliable runtime fingerprint. Legacy exposes an optional Doctor API.
     */
    static boolean hasPluginClass(@NotNull Plugin plugin, @NotNull String className) {
        try {
            Class.forName(className, false, plugin.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError | SecurityException ignored) {
            return false;
        }
    }

    /** Slimefun United publishes the unique aliases "sfu" and "slimefununited". */
    static boolean hasUnitedCommandAlias(@NotNull PluginDescriptionFile description) {
        Map<String, Map<String, Object>> commands = description.getCommands();
        if (commands == null) {
            return false;
        }
        Map<String, Object> slimefunCommand = commands.get("slimefun");
        if (slimefunCommand == null) {
            return false;
        }
        Object aliases = slimefunCommand.get("aliases");
        if (aliases instanceof String alias) {
            return isUnitedAlias(alias);
        }
        if (aliases instanceof Collection<?> collection) {
            for (Object alias : collection) {
                if (alias != null && isUnitedAlias(alias.toString())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean isUnitedAlias(@NotNull String alias) {
        String normalized = alias.toLowerCase(Locale.ROOT);
        return normalized.equals("sfu") || normalized.equals("slimefununited");
    }

    static int compareVersions(@NotNull String left, @NotNull String right) {
        int[] leftParts = numericParts(left, 3);
        int[] rightParts = numericParts(right, 3);
        for (int index = 0; index < Math.max(leftParts.length, rightParts.length); index++) {
            int leftValue = index < leftParts.length ? leftParts[index] : 0;
            int rightValue = index < rightParts.length ? rightParts[index] : 0;
            int comparison = Integer.compare(leftValue, rightValue);
            if (comparison != 0) {
                return comparison;
            }
        }
        return 0;
    }

    private static int[] numericParts(String version, int maximumParts) {
        Matcher matcher = VERSION_COMPONENT.matcher(version);
        int[] parts = new int[maximumParts];
        int count = 0;
        while (matcher.find() && count < maximumParts) {
            try {
                parts[count++] = Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException ignored) {
                parts[count - 1] = 0;
            }
        }
        if (count == parts.length) {
            return parts;
        }
        int[] trimmed = new int[count];
        System.arraycopy(parts, 0, trimmed, 0, count);
        return trimmed;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

package io.github.sefiraat.networks.diagnostics;

import com.ytdd9527.networksexpansion.core.items.unusable.Blueprint;
import io.github.sefiraat.networks.Networks;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/** Optional schema migration for legacy translated Networks crafting blueprints. */
final class LegacyBlueprintDoctorBridge {

    private static final String ITEM_ID = "NTW_CRAFTING_BLUEPRINT";
    private static final String CANDIDATE_TYPE = "legacy-networks-blueprint-presentation";
    private static final String SCHEMA_PROBE_API =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaProbe";
    private static final String SCHEMA_CANDIDATE_API =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate";
    private static final String SCHEMA_READINESS_API =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate$Readiness";
    private static final String SCHEMA_MIGRATOR_API =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaMigrator";

    private LegacyBlueprintDoctorBridge() {
    }

    static void register(Networks plugin, ClassLoader loader) {
        registerProbe(plugin, loader);
        registerMigrator(plugin, loader);
    }

    private static void registerProbe(Networks plugin, ClassLoader loader) {
        try {
            Class<?> probeInterface = Class.forName(SCHEMA_PROBE_API, false, loader);
            Class<?> candidateClass = Class.forName(SCHEMA_CANDIDATE_API, false, loader);
            Class<?> readinessClass = Class.forName(SCHEMA_READINESS_API, false, loader);
            Constructor<?> candidateConstructor;
            boolean supportsClaim;
            try {
                candidateConstructor = candidateClass.getConstructor(
                    String.class, readinessClass, String.class, String.class);
                supportsClaim = true;
            } catch (NoSuchMethodException ignored) {
                candidateConstructor = candidateClass.getConstructor(String.class, readinessClass, String.class);
                supportsClaim = false;
            }
            Method readinessValueOf = readinessClass.getMethod("valueOf", String.class);
            Constructor<?> finalConstructor = candidateConstructor;
            boolean finalSupportsClaim = supportsClaim;
            InvocationHandler handler = (proxy, method, arguments) -> invokeProbe(
                proxy, method, arguments, finalConstructor, readinessValueOf, finalSupportsClaim);
            Object provider = Proxy.newProxyInstance(loader, new Class<?>[] {probeInterface}, handler);
            registerRaw(Bukkit.getServicesManager(), probeInterface, provider, plugin);
            plugin.getLogger().info("Registered Networks blueprint schema probe with Slimefun Doctor.");
        } catch (ClassNotFoundException ignored) {
            // Optional Slimefun Legacy API.
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                "Could not register the optional Networks blueprint Doctor probe.", exception);
        }
    }

    private static void registerMigrator(Networks plugin, ClassLoader loader) {
        try {
            Class<?> migratorInterface = Class.forName(SCHEMA_MIGRATOR_API, false, loader);
            Object provider = Proxy.newProxyInstance(
                loader,
                new Class<?>[] {migratorInterface},
                LegacyBlueprintDoctorBridge::invokeMigrator);
            registerRaw(Bukkit.getServicesManager(), migratorInterface, provider, plugin);
            plugin.getLogger().info("Registered Networks blueprint schema migrator with Slimefun Doctor.");
        } catch (ClassNotFoundException ignored) {
            // Optional Slimefun Legacy API.
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                "Could not register the optional Networks blueprint Doctor migrator.", exception);
        }
    }

    private static Object invokeProbe(
        Object proxy,
        Method method,
        Object[] arguments,
        Constructor<?> candidateConstructor,
        Method readinessValueOf,
        boolean supportsClaim
    ) throws ReflectiveOperationException {
        return switch (method.getName()) {
            case "getMigrationName" -> "Networks legacy blueprint presentation";
            case "getSupportedItemIds" -> Set.of(ITEM_ID);
            case "probeItem" -> {
                if (arguments == null
                    || arguments.length < 2
                    || !(arguments[0] instanceof ItemStack item)
                    || !ITEM_ID.equals(arguments[1])
                    || !containsCjkPresentation(item)) {
                    yield null;
                }

                ItemStack preview = item.clone();
                boolean canRegenerateEnglish = refreshPresentation(preview) && !containsCjkPresentation(preview);
                if (!canRegenerateEnglish || !supportsClaim) {
                    Object readiness = readinessValueOf.invoke(null, "MANUAL_ONLY");
                    yield candidateConstructor.newInstance(
                        CANDIDATE_TYPE,
                        readiness,
                        canRegenerateEnglish
                            ? "Legacy translated blueprint detected; this Slimefun Legacy build cannot fingerprint it automatically."
                            : "Legacy translated blueprint detected, but the current Networks language cannot regenerate a fully English presentation.");
                }

                Object readiness = readinessValueOf.invoke(null, "READY");
                yield candidateConstructor.newInstance(
                    CANDIDATE_TYPE,
                    readiness,
                    "Stored blueprint recipe/output can regenerate English lore without rewriting its PDC payload.",
                    fingerprint(item));
            }
            case "toString" -> "NetworksLegacyBlueprintSchemaProbe";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> arguments != null && arguments.length == 1 && arguments[0] == proxy;
            default -> throw new UnsupportedOperationException(
                "Unsupported LegacyItemSchemaProbe method: " + method.getName());
        };
    }

    private static Object invokeMigrator(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "getSupportedCandidateTypes" -> Set.of(CANDIDATE_TYPE);
            case "migrateItem" -> {
                if (arguments == null
                    || arguments.length < 5
                    || !(arguments[0] instanceof ItemStack item)
                    || !ITEM_ID.equals(arguments[1])
                    || !CANDIDATE_TYPE.equals(arguments[2])
                    || !(arguments[3] instanceof String approvedClaim)
                    || !approvedClaim.equals(fingerprint(item))
                    || !containsCjkPresentation(item)) {
                    yield false;
                }
                boolean changed = refreshPresentation(item);
                yield changed && !containsCjkPresentation(item);
            }
            case "toString" -> "NetworksLegacyBlueprintSchemaMigrator";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> arguments != null && arguments.length == 1 && arguments[0] == proxy;
            default -> throw new UnsupportedOperationException(
                "Unsupported LegacyItemSchemaMigrator method: " + method.getName());
        };
    }

    private static boolean refreshPresentation(ItemStack item) {
        boolean changed = false;
        SlimefunItem slimefunItem = SlimefunItem.getById(ITEM_ID);
        if (slimefunItem != null) {
            ItemMeta current = item.getItemMeta();
            ItemMeta canonical = slimefunItem.getItem().getItemMeta();
            if (current.hasDisplayName()
                && containsCjk(current.getDisplayName())
                && canonical.hasDisplayName()
                && !containsCjk(canonical.getDisplayName())) {
                current.setDisplayName(canonical.getDisplayName());
                item.setItemMeta(current);
                changed = true;
            }
        }
        return Blueprint.refreshStoredBlueprintLore(item) || changed;
    }

    private static boolean containsCjkPresentation(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        return (meta.hasDisplayName() && containsCjk(meta.getDisplayName()))
            || (meta.hasLore() && containsCjk(meta.getLore()));
    }

    private static boolean containsCjk(List<String> lines) {
        if (lines == null) {
            return false;
        }
        for (String line : lines) {
            if (containsCjk(line)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsCjk(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        for (int offset = 0; offset < text.length();) {
            int codePoint = text.codePointAt(offset);
            Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
            if (script == Character.UnicodeScript.HAN
                || script == Character.UnicodeScript.HIRAGANA
                || script == Character.UnicodeScript.KATAKANA
                || script == Character.UnicodeScript.HANGUL) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private static String fingerprint(ItemStack item) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(item.serializeAsBytes()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void registerRaw(
        ServicesManager services,
        Class service,
        Object provider,
        Networks plugin
    ) {
        services.register(service, provider, plugin, ServicePriority.Normal);
    }
}

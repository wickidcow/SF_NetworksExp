package io.github.sefiraat.networks.diagnostics;

import io.github.sefiraat.networks.Networks;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.ServicesManager;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;

/** Reflective bridge to Slimefun Legacy's optional Doctor service APIs. */
public final class LegacyDoctorBridge {

    private static final String DOCTOR_API = "io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctor";
    private static final String REPORT_API = "io.github.thebusybiscuit.slimefun4.api.diagnostics.AddonDoctorReport";
    private static final String SCHEMA_PROBE_API =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaProbe";
    private static final String SCHEMA_CANDIDATE_API =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate";
    private static final String SCHEMA_READINESS_API =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaCandidate$Readiness";
    private static final String SCHEMA_MIGRATOR_API =
        "io.github.thebusybiscuit.slimefun4.api.diagnostics.LegacyItemSchemaMigrator";

    private LegacyDoctorBridge() {
    }

    public static void register(@NotNull Networks plugin) {
        Plugin slimefun = Bukkit.getPluginManager().getPlugin("Slimefun");
        if (slimefun == null) {
            return;
        }

        ClassLoader loader = slimefun.getClass().getClassLoader();
        registerAddonDoctor(plugin, loader);
        registerSchemaProbe(plugin, loader);
        registerSchemaMigrator(plugin, loader);
    }

    private static void registerAddonDoctor(@NotNull Networks plugin, @NotNull ClassLoader loader) {
        try {
            Class<?> doctorInterface = Class.forName(DOCTOR_API, false, loader);
            Class<?> reportClass = Class.forName(REPORT_API, false, loader);
            Constructor<?> reportConstructor = reportClass.getConstructor(
                String.class,
                boolean.class,
                long.class,
                long.class,
                long.class,
                long.class,
                List.class);

            InvocationHandler handler = (proxy, method, arguments) ->
                invokeDoctor(proxy, method, arguments, reportConstructor);
            Object provider = Proxy.newProxyInstance(loader, new Class<?>[] {doctorInterface}, handler);
            registerRaw(Bukkit.getServicesManager(), doctorInterface, provider, plugin);
            plugin.getLogger().info("Registered Networks with Slimefun Legacy Addon Doctor.");
        } catch (ClassNotFoundException ignored) {
            // United and Gugu do not currently expose this optional Legacy API.
        } catch (ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not register the optional Slimefun Doctor bridge.", exception);
        }
    }

    private static void registerSchemaProbe(@NotNull Networks plugin, @NotNull ClassLoader loader) {
        try {
            Class<?> probeInterface = Class.forName(SCHEMA_PROBE_API, false, loader);
            Class<?> candidateClass = Class.forName(SCHEMA_CANDIDATE_API, false, loader);
            Class<?> readinessClass = Class.forName(SCHEMA_READINESS_API, false, loader);
            Constructor<?> candidateConstructor = candidateClass.getConstructor(
                String.class, readinessClass, String.class, String.class);
            Method readinessValueOf = readinessClass.getMethod("valueOf", String.class);

            InvocationHandler handler = (proxy, method, arguments) ->
                invokeSchemaProbe(proxy, method, arguments, candidateConstructor, readinessValueOf);
            Object provider = Proxy.newProxyInstance(loader, new Class<?>[] {probeInterface}, handler);
            registerRaw(Bukkit.getServicesManager(), probeInterface, provider, plugin);
            plugin.getLogger().info("Registered Networks Crafting Blueprint schema probe with Slimefun Doctor.");
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // Older Slimefun implementations do not expose claim-backed same-ID migration APIs.
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not register the optional Slimefun schema probe.", exception);
        }
    }

    private static void registerSchemaMigrator(@NotNull Networks plugin, @NotNull ClassLoader loader) {
        try {
            Class<?> migratorInterface = Class.forName(SCHEMA_MIGRATOR_API, false, loader);
            InvocationHandler handler = LegacyDoctorBridge::invokeSchemaMigrator;
            Object provider = Proxy.newProxyInstance(loader, new Class<?>[] {migratorInterface}, handler);
            registerRaw(Bukkit.getServicesManager(), migratorInterface, provider, plugin);
            plugin.getLogger().info("Registered Networks Crafting Blueprint schema migrator with Slimefun Doctor.");
        } catch (ClassNotFoundException ignored) {
            // Schema mutation is optional and only available on newer Slimefun Legacy builds.
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Could not register the optional Slimefun schema migrator.", exception);
        }
    }

    public static void unregister(@NotNull Networks plugin) {
        Bukkit.getServicesManager().unregisterAll(plugin);
    }

    private static Object invokeDoctor(
        Object proxy,
        Method method,
        Object[] arguments,
        Constructor<?> reportConstructor
    ) throws ReflectiveOperationException {
        return switch (method.getName()) {
            case "getAddonName" -> "Networks";
            case "runDoctor" -> {
                boolean repair = arguments != null && arguments.length > 0 && Boolean.TRUE.equals(arguments[0]);
                NetworksDoctorReport report = NetworksDoctor.run(repair);
                yield reportConstructor.newInstance(
                    "Networks",
                    repair,
                    report.getScannedEntries(),
                    report.getIssuesFound(),
                    report.getRepairedEntries(),
                    report.getFailures(),
                    report.getDetails());
            }
            case "toString" -> "NetworksAddonDoctor";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> arguments != null && arguments.length == 1 && arguments[0] == proxy;
            default -> throw new UnsupportedOperationException("Unsupported AddonDoctor method: " + method.getName());
        };
    }

    private static Object invokeSchemaProbe(
        Object proxy,
        Method method,
        Object[] arguments,
        Constructor<?> candidateConstructor,
        Method readinessValueOf
    ) throws ReflectiveOperationException {
        return switch (method.getName()) {
            case "getMigrationName" -> "Networks legacy item presentation";
            case "getSupportedItemIds" -> Set.of(LegacyBlueprintSchemaMigration.ITEM_ID);
            case "probeItem" -> {
                if (arguments == null
                    || arguments.length < 2
                    || !(arguments[0] instanceof ItemStack item)
                    || !LegacyBlueprintSchemaMigration.ITEM_ID.equals(arguments[1])) {
                    yield null;
                }

                LegacyBlueprintSchemaMigration.Result result = LegacyBlueprintSchemaMigration.inspect(item);
                if (result == null) {
                    yield null;
                }
                Object readiness = readinessValueOf.invoke(null, result.readiness());
                yield candidateConstructor.newInstance(
                    LegacyBlueprintSchemaMigration.CANDIDATE_TYPE,
                    readiness,
                    result.detail(),
                    result.validationClaim());
            }
            case "toString" -> "NetworksLegacyItemSchemaProbe";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> arguments != null && arguments.length == 1 && arguments[0] == proxy;
            default -> throw new UnsupportedOperationException(
                "Unsupported LegacyItemSchemaProbe method: " + method.getName());
        };
    }

    private static Object invokeSchemaMigrator(Object proxy, Method method, Object[] arguments) {
        return switch (method.getName()) {
            case "getSupportedCandidateTypes" -> Set.of(LegacyBlueprintSchemaMigration.CANDIDATE_TYPE);
            case "migrateItem" -> {
                if (arguments == null
                    || arguments.length < 5
                    || !(arguments[0] instanceof ItemStack item)
                    || !LegacyBlueprintSchemaMigration.ITEM_ID.equals(arguments[1])
                    || !LegacyBlueprintSchemaMigration.CANDIDATE_TYPE.equals(arguments[2])
                    || !(arguments[3] instanceof String claim)) {
                    yield false;
                }
                yield LegacyBlueprintSchemaMigration.migrate(item, claim);
            }
            case "toString" -> "NetworksLegacyItemSchemaMigrator";
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> arguments != null && arguments.length == 1 && arguments[0] == proxy;
            default -> throw new UnsupportedOperationException(
                "Unsupported LegacyItemSchemaMigrator method: " + method.getName());
        };
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

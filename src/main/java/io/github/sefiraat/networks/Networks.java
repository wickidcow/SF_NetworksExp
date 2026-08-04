package io.github.sefiraat.networks;

import com.balugaq.netex.api.algorithm.ID;
import com.balugaq.netex.api.data.ItemFlowRecord;
import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.api.keybind.Keybinds;
import com.balugaq.netex.core.guide.GridNewStyleCustomAmountGuideOption;
import com.balugaq.netex.utils.Debug;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import com.ytdd9527.networksexpansion.core.services.LocalizationService;
import com.ytdd9527.networksexpansion.setup.SetupUtil;
import com.ytdd9527.networksexpansion.utils.databases.DataSource;
import com.ytdd9527.networksexpansion.utils.databases.DataStorage;
import com.ytdd9527.networksexpansion.utils.databases.QueryQueue;
import io.github.sefiraat.networks.commands.NetworksMain;
import io.github.sefiraat.networks.compatibility.CompatibilityReport;
import io.github.sefiraat.networks.compatibility.RuntimeCompatibility;
import io.github.sefiraat.networks.diagnostics.LegacyDoctorBridge;
import io.github.sefiraat.networks.diagnostics.NetworksDoctor;
import io.github.sefiraat.networks.integrations.HudCallbacks;
import io.github.sefiraat.networks.integrations.NetheoPlants;
import io.github.sefiraat.networks.managers.ListenerManager;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.slimefun.network.AdminDebuggable;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.slimefun.network.NetworkObject;
import io.github.thebusybiscuit.slimefun4.api.SlimefunAddon;
import io.github.thebusybiscuit.slimefun4.core.guide.options.SlimefunGuideSettings;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.paperlib.PaperLib;
import lombok.Getter;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.yaml.snakeyaml.error.YAMLException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class Networks extends JavaPlugin implements SlimefunAddon {

    private static final String DEFAULT_LANGUAGE = "en-US";
    private static Networks instance;

    @Getter
    private static DataSource dataSource;

    @Getter
    private static QueryQueue queryQueue;

    /** Kept under its historical name for binary compatibility with existing integrations. */
    @Getter
    private static BukkitRunnable autoSaveThread;

    @Getter
    private static CompatibilityReport compatibilityReport;

    private static MinecraftVersion minecraftVersion = MinecraftVersion.UNKNOWN;
    private ConfigManager configManager;
    private ListenerManager listenerManager;
    private SupportedPluginManager supportedPluginManager;
    private LocalizationService localizationService;
    private long slimefunTickCount;
    private boolean startupComplete;

    public static ConfigManager getConfigManager() {
        return Networks.getInstance().configManager;
    }

    public static Networks getInstance() {
        return Networks.instance;
    }

    @NotNull
    public static PluginManager getPluginManager() {
        return Networks.getInstance().getServer().getPluginManager();
    }

    public static SupportedPluginManager getSupportedPluginManager() {
        return Networks.getInstance().supportedPluginManager;
    }

    public static LocalizationService getLocalizationService() {
        return Networks.getInstance().localizationService;
    }

    public static ListenerManager getListenerManager() {
        return Networks.getInstance().listenerManager;
    }

    public static long getSlimefunTickCount() {
        return getInstance().slimefunTickCount;
    }

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        loadLanguage();
        superHead();

        compatibilityReport = RuntimeCompatibility.inspect(this);
        logCompatibility(compatibilityReport);
        if (!compatibilityReport.isSupported()) {
            getLogger().severe("Networks cannot start on this runtime. Correct the errors above or use the explicit unsupported-runtime override.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        environmentCheck();
        getLogger().info(getLocalizationService().getString("messages.startup.loaded-language"));
        getLogger().info(getLocalizationService().getString("messages.startup.getting-config"));
        getLogger().info(getLocalizationService().getString("messages.startup.trying-auto-update"));

        supportedPluginManager = new SupportedPluginManager();

        getLogger().info(getLocalizationService().getString("messages.startup.creating-query-queue"));
        queryQueue = new QueryQueue();
        queryQueue.startThread();

        getLogger().info(getLocalizationService().getString("messages.startup.connecting-database"));
        try {
            dataSource = new DataSource();
        } catch (ClassNotFoundException | SQLException | RuntimeException exception) {
            getLogger().warning(getLocalizationService().getString("messages.startup.failed-to-connect-database"));
            Debug.trace(exception);
            queryQueue.shutdown(1000L);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        startAutoSave();

        getLogger().info(getLocalizationService().getString("messages.startup.registering-items"));
        SetupUtil.setupAll();
        NetworkObject.startSharedTicker();

        getLogger().info(getLocalizationService().getString("messages.startup.registering-listeners"));
        listenerManager = new ListenerManager();

        getLogger().info(getLocalizationService().getString("messages.startup.registering-commands"));
        PluginCommand command = getCommand("networks");
        if (command == null) {
            getLogger().severe("The networks command is missing from plugin.yml.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        NetworksMain executor = new NetworksMain();
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        setupMetrics();
        startMaintenanceTasks();

        AdminDebuggable.load();
        SlimefunGuideSettings.addOption(GridNewStyleCustomAmountGuideOption.instance());
        LegacyDoctorBridge.register(this);

        Bukkit.getScheduler().runTaskLater(this, Keybinds::distinctAll, 1L);
        ID.fetchId();
        Keybinds.fetchScripts();

        startupComplete = true;
        getLogger().info(getLocalizationService().getString("messages.startup.enabled-successfully"));
    }

    @Override
    public void onDisable() {
        LegacyDoctorBridge.unregister(this);
        NetworkObject.stopSharedTicker();

        if (autoSaveThread != null) {
            autoSaveThread.cancel();
            autoSaveThread = null;
        }

        try {
            if (localizationService != null) {
                getLogger().info(getLocalizationService().getString("messages.shutdown.saving-config"));
            }
            ID.saveId();
            if (configManager != null) {
                configManager.saveAll();
            }
        } catch (Throwable throwable) {
            Debug.trace(throwable);
        }

        if (dataSource != null && queryQueue != null && queryQueue.isAcceptingTasks()) {
            try {
                DataStorage.saveAmountChange();
            } catch (RuntimeException exception) {
                Debug.trace(exception);
            }
        }

        long timeoutMillis = Math.max(1000L, getConfig().getLong("database.shutdown-timeout-seconds", 15L) * 1000L);
        boolean databaseWorkerStopped = true;
        if (queryQueue != null) {
            int pending = queryQueue.getTaskAmount();
            if (pending > 0) {
                getLogger().info("Waiting for " + pending + " Networks database task(s) to finish.");
            }
            boolean drained = queryQueue.shutdown(timeoutMillis);
            databaseWorkerStopped = !queryQueue.isWorkerRunning();
            if (!drained) {
                getLogger().warning("Networks database shutdown exceeded its drain deadline. "
                    + "Unstarted tasks were cancelled to prevent writes after disable.");
            }
            if (!databaseWorkerStopped) {
                getLogger().severe("Networks database worker did not stop after interruption. "
                    + "The SQLite connection will not be closed concurrently; make a backup before restarting.");
            }
        }

        if (dataSource != null && databaseWorkerStopped) {
            dataSource.close();
            dataSource = null;
        }
        queryQueue = null;
        DataStorage.clearRuntimeCache();
        NetworkStorage.clear();
        NetworkController.getNetworks().clear();
        NetworkController.getRecords().clear();
        NetworkController.getRecordFlow().clear();
        NetworkController.getCrayons().clear();

        if (localizationService != null) {
            getLogger().info(getLocalizationService().getString("messages.shutdown.saved-all-data"));
            getLogger().info(getLocalizationService().getString("messages.shutdown.disabled-successfully"));
        } else if (startupComplete) {
            getLogger().info("Networks disabled.");
        }
        startupComplete = false;
    }

    private void loadLanguage() {
        getLogger().info("Loading language");
        configManager = new ConfigManager();
        localizationService = new LocalizationService(this);
        String language = configManager.getLanguage();
        try {
            localizationService.addLanguage(language);
            getLogger().info("Language " + language + " loaded successfully.");
        } catch (ClassCastException | IllegalArgumentException | YAMLException exception) {
            getLogger().log(Level.WARNING, "Failed to load language " + language, exception);
        }

        localizationService.addDefaultLanguage(DEFAULT_LANGUAGE);
        getLogger().info("Default language " + DEFAULT_LANGUAGE + " loaded successfully.");
    }

    private void logCompatibility(@NotNull CompatibilityReport report) {
        getLogger().info("Compatibility target: " + report.getCoreVariant().getDisplayName()
            + " " + report.getCoreVersion()
            + ", Minecraft " + report.getMinecraftVersion()
            + ", Java " + report.getJavaFeature());
        for (String warning : report.getWarnings()) {
            getLogger().warning(warning);
        }
    }

    private void startAutoSave() {
        getLogger().info(getLocalizationService().getString("messages.startup.creating-auto-save-thread"));
        autoSaveThread = new BukkitRunnable() {
            @Override
            public void run() {
                if (queryQueue != null && queryQueue.isAcceptingTasks()) {
                    DataStorage.saveAmountChange();
                }
            }
        };
        int seconds = getConfig().getInt("drawer-auto-save-period");
        seconds = seconds <= 0 ? 300 : seconds;
        long period = 20L * seconds;
        autoSaveThread.runTaskTimer(this, 2L * period, period);
    }

    private void startMaintenanceTasks() {
        long tickRate = Math.max(1L, Slimefun.getTickerTask().getTickRate());
        Bukkit.getScheduler().runTaskTimer(this, () -> slimefunTickCount++, 1L, tickRate);

        long doctorPeriod = Math.max(200L, getConfig().getLong("doctor.auto-scan-period-ticks", 1200L));
        if (getConfig().getBoolean("doctor.auto-repair-stale-runtime-state", true)) {
            Bukkit.getScheduler().runTaskTimer(this, () -> {
                try {
                    var report = NetworksDoctor.run(true);
                    if (report.getRepairedEntries() > 0 || report.getFailures() > 0) {
                        getLogger().info("Networks Doctor repaired " + report.getRepairedEntries()
                            + " stale runtime entry/entries; failures=" + report.getFailures());
                    }
                } catch (RuntimeException exception) {
                    getLogger().log(Level.WARNING, "Automatic Networks Doctor pass failed.", exception);
                }
            }, doctorPeriod, doctorPeriod);
        }

        Bukkit.getScheduler().runTaskTimer(
            this,
            () -> NetworkController.getRecords().values().forEach(ItemFlowRecord::gc),
            tickRate,
            tickRate);
    }

    public void superHead() {
        List<String> superHead = getLocalizationService().getStringList("messages.super-head");
        for (String line : superHead) {
            getLogger().info(line);
        }
    }

    public void environmentCheck() {
        try {
            minecraftVersion = MinecraftVersion.current();
        } catch (NoClassDefFoundError | NoSuchFieldError error) {
            for (int i = 0; i < 20; i++) {
                getLogger().severe(getLocalizationService().getString("messages.depend.suggest-download-newer-slimefun"));
            }
        }

        if (minecraftVersion == MinecraftVersion.UNKNOWN) {
            int major = PaperLib.getMinecraftVersion();
            int minor = PaperLib.getMinecraftPatchVersion();
            minecraftVersion = MinecraftVersion.of(major, minor);
        }
    }

    public void setupIntegrations() {
        if (supportedPluginManager.isSlimeHud()) {
            getLogger().info(getLocalizationService().getString("messages.integrations.found-slimehud"));
            try {
                HudCallbacks.setup();
            } catch (NoClassDefFoundError error) {
                getLogger().warning(getLocalizationService().getString("messages.integrations.not-found-slimehud"));
            }
        }
        if (supportedPluginManager.isNetheopoiesis()) {
            getLogger().info(getLocalizationService().getString("messages.integrations.found-netheopoiesis"));
            try {
                NetheoPlants.setup();
            } catch (NoClassDefFoundError error) {
                getLogger().warning(getLocalizationService().getString("messages.integrations.not-found-netheopoiesis"));
            }
        }
    }

    public MinecraftVersion getMCVersion() {
        return minecraftVersion;
    }

    public void setupMetrics() {
        Metrics metrics = new Metrics(this, 13644);
        AdvancedPie networksChart = new AdvancedPie("networks", () -> {
            Map<String, Integer> networksMap = new HashMap<>();
            networksMap.put("Number of networks", NetworkController.getNetworks().size());
            return networksMap;
        });
        metrics.addCustomChart(networksChart);
    }

    @NotNull
    @Override
    public JavaPlugin getJavaPlugin() {
        return this;
    }

    @Nullable
    @Override
    public String getBugTrackerURL() {
        return "https://github.com/wickidcow/SF_NetworksExp/issues";
    }

    @NotNull
    public String getWikiURL() {
        return "https://github.com/wickidcow/SF_NetworksExp#readme";
    }

    public void debug(String message) {
        if (getConfigManager().isDebug()) {
            getLogger().warning("[DEBUG] " + message);
        }
    }
}

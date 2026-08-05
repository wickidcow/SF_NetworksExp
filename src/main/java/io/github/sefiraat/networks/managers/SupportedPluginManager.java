package io.github.sefiraat.networks.managers;

import com.balugaq.netex.integrations.logitech.LogitechIntegration;
import com.bgsoftware.wildstacker.api.WildStackerAPI;
import dev.rosewood.rosestacker.api.RoseStackerAPI;
import io.github.sefiraat.networks.Networks;
import org.bukkit.Bukkit;
import org.bukkit.entity.Item;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Detects optional plugins without allowing an incompatible optional API to disable Networks.
 * Slimefun itself remains a hard dependency; every other integration is fail-soft.
 */
public final class SupportedPluginManager {

    private static volatile SupportedPluginManager instance;

    private final Networks plugin;
    private final boolean infinityExpansion;
    private final boolean fluffyMachines;
    private volatile boolean netheopoiesis;
    private volatile boolean slimeHud;
    private final boolean guguSlimefunLib;
    private final boolean finalTECH;
    private final boolean mcMMO;
    private final boolean wildChests;

    private volatile boolean roseStacker;
    private volatile boolean wildStacker;
    private volatile boolean justEnoughGuide;
    private volatile boolean logitech;
    private volatile @Nullable RoseStackerAPI roseStackerAPI;
    private volatile @Nullable LogitechIntegration logitechIntegration;
    private volatile @Nullable BukkitTask deferredRegistrationTask;

    public SupportedPluginManager() {
        this.plugin = Networks.getInstance();
        synchronized (SupportedPluginManager.class) {
            if (instance != null) {
                throw new IllegalStateException("SupportedPluginManager is already initialized");
            }
            instance = this;
        }

        try {
            this.infinityExpansion = isEnabled("InfinityExpansion");
            this.fluffyMachines = isEnabled("FluffyMachines");
            this.netheopoiesis = isEnabled("Netheopoiesis");
            this.slimeHud = isEnabled("SlimeHUD") || isEnabled("SlimeHUDPlus");
            this.guguSlimefunLib = isEnabled("GuguSlimefunLib");
            this.finalTECH = isEnabled("FinalTECH") || isEnabled("FinalTECH-Changed");
            this.mcMMO = isEnabled("mcMMO");
            this.wildChests = isEnabled("WildChests");

            this.roseStacker = isEnabled("RoseStacker")
                && hasPluginClass("RoseStacker", "dev.rosewood.rosestacker.api.RoseStackerAPI");
            this.wildStacker = isEnabled("WildStacker")
                && hasPluginClass("WildStacker", "com.bgsoftware.wildstacker.api.WildStackerAPI");
            this.justEnoughGuide = isEnabled("JustEnoughGuide")
                && hasPluginClass("JustEnoughGuide", "com.balugaq.jeg.api.objects.events.GuideEvents");
            this.logitech = isEnabled("LogiTech");

            deferredRegistrationTask = Bukkit.getScheduler().runTaskLater(plugin, this::initializeDeferredApis, 1L);
        } catch (RuntimeException | LinkageError exception) {
            synchronized (SupportedPluginManager.class) {
                instance = null;
            }
            throw exception;
        }
    }

    public static @NotNull SupportedPluginManager getInstance() {
        SupportedPluginManager current = instance;
        if (current == null) {
            throw new IllegalStateException("SupportedPluginManager is not initialized");
        }
        return current;
    }

    public static void shutdown() {
        SupportedPluginManager current;
        synchronized (SupportedPluginManager.class) {
            current = instance;
            instance = null;
        }
        if (current != null) {
            BukkitTask task = current.deferredRegistrationTask;
            if (task != null) {
                task.cancel();
            }
            current.deferredRegistrationTask = null;
            current.roseStackerAPI = null;
            current.logitechIntegration = null;
        }
    }

    public static int getStackAmount(@NotNull Item item) {
        SupportedPluginManager manager = instance;
        if (manager == null) {
            return item.getItemStack().getAmount();
        }

        if (manager.wildStacker && manager.isEnabled("WildStacker")) {
            try {
                return WildStackerAPI.getItemAmount(item);
            } catch (RuntimeException | LinkageError exception) {
                manager.disableIntegration("WildStacker", exception);
            }
        }

        RoseStackerAPI roseApi = manager.roseStackerAPI;
        if (manager.roseStacker && roseApi != null && manager.isEnabled("RoseStacker")) {
            try {
                dev.rosewood.rosestacker.stack.StackedItem stackedItem = roseApi.getStackedItem(item);
                return stackedItem == null ? item.getItemStack().getAmount() : stackedItem.getStackSize();
            } catch (RuntimeException | LinkageError exception) {
                manager.disableIntegration("RoseStacker", exception);
            }
        }

        return item.getItemStack().getAmount();
    }

    public static void setStackAmount(@NotNull Item item, int amount) {
        SupportedPluginManager manager = instance;
        if (manager == null) {
            setVanillaItemAmount(item, amount);
            return;
        }

        if (manager.wildStacker && manager.isEnabled("WildStacker")) {
            try {
                com.bgsoftware.wildstacker.api.objects.StackedItem stackedItem = WildStackerAPI.getStackedItem(item);
                if (stackedItem != null) {
                    stackedItem.setStackAmount(amount, true);
                } else {
                    setVanillaItemAmount(item, amount);
                }
                return;
            } catch (RuntimeException | LinkageError exception) {
                manager.disableIntegration("WildStacker", exception);
            }
        }

        RoseStackerAPI roseApi = manager.roseStackerAPI;
        if (manager.roseStacker && roseApi != null && manager.isEnabled("RoseStacker")) {
            try {
                dev.rosewood.rosestacker.stack.StackedItem stackedItem = roseApi.getStackedItem(item);
                if (stackedItem != null) {
                    stackedItem.setStackSize(amount);
                } else {
                    setVanillaItemAmount(item, amount);
                }
                return;
            } catch (RuntimeException | LinkageError exception) {
                manager.disableIntegration("RoseStacker", exception);
            }
        }

        setVanillaItemAmount(item, amount);
    }

    private static void setVanillaItemAmount(@NotNull Item item, int amount) {
        if (amount <= 0) {
            item.remove();
            return;
        }
        var stack = item.getItemStack();
        stack.setAmount(amount);
        item.setItemStack(stack);
    }

    private void initializeDeferredApis() {
        deferredRegistrationTask = null;

        if (roseStacker) {
            try {
                roseStackerAPI = RoseStackerAPI.getInstance();
                if (roseStackerAPI == null) {
                    roseStacker = false;
                    plugin.getLogger().warning("RoseStacker is enabled, but its API instance is unavailable. Integration disabled.");
                }
            } catch (RuntimeException | LinkageError exception) {
                disableIntegration("RoseStacker", exception);
            }
        }

        if (logitech) {
            try {
                logitechIntegration = new LogitechIntegration();
            } catch (RuntimeException | LinkageError exception) {
                disableIntegration("LogiTech", exception);
            }
        }
    }

    private boolean hasPluginClass(@NotNull String pluginName, @NotNull String className) {
        Plugin target = Bukkit.getPluginManager().getPlugin(pluginName);
        if (target == null) {
            return false;
        }
        try {
            Class.forName(className, false, target.getClass().getClassLoader());
            return true;
        } catch (ClassNotFoundException | LinkageError | SecurityException exception) {
            plugin.getLogger().log(
                Level.WARNING,
                pluginName + " is enabled, but the expected API class is unavailable. Networks will ignore this integration.",
                exception);
            return false;
        }
    }

    private boolean isEnabled(@NotNull String pluginName) {
        return Bukkit.getPluginManager().isPluginEnabled(pluginName);
    }

    private void disableIntegration(@NotNull String integration, @NotNull Throwable throwable) {
        switch (integration) {
            case "SlimeHUD" -> slimeHud = false;
            case "Netheopoiesis" -> netheopoiesis = false;
            case "RoseStacker" -> {
                roseStacker = false;
                roseStackerAPI = null;
            }
            case "WildStacker" -> wildStacker = false;
            case "JustEnoughGuide" -> justEnoughGuide = false;
            case "LogiTech" -> {
                logitech = false;
                logitechIntegration = null;
            }
            default -> {
                // No mutable runtime flag is associated with this integration.
            }
        }
        plugin.getLogger().log(
            Level.WARNING,
            integration + " integration was disabled after an API compatibility failure. Networks will continue running.",
            throwable);
    }

    public void disableOptionalIntegration(@NotNull String integration, @NotNull Throwable throwable) {
        disableIntegration(integration, throwable);
    }

    public @NotNull List<String> getIntegrationSummary() {
        List<String> summary = new ArrayList<>();
        addStatus(summary, "InfinityExpansion", infinityExpansion);
        addStatus(summary, "FluffyMachines", fluffyMachines);
        addStatus(summary, "Netheopoiesis", netheopoiesis);
        addStatus(summary, "SlimeHUD", slimeHud);
        addStatus(summary, "RoseStacker", roseStacker && roseStackerAPI != null);
        addStatus(summary, "WildStacker", wildStacker);
        addStatus(summary, "WildChests", wildChests);
        addStatus(summary, "mcMMO", mcMMO);
        addStatus(summary, "JustEnoughGuide", justEnoughGuide);
        addStatus(summary, "LogiTech", logitech && logitechIntegration != null);
        addStatus(summary, "GuguSlimefunLib", guguSlimefunLib);
        addStatus(summary, "FinalTECH", finalTECH);
        return summary;
    }

    private static void addStatus(@NotNull List<String> summary, @NotNull String name, boolean enabled) {
        summary.add(name + '=' + (enabled ? "active" : "inactive"));
    }

    public boolean isInfinityExpansion() {
        return infinityExpansion;
    }

    public boolean isFluffyMachines() {
        return fluffyMachines;
    }

    public boolean isNetheopoiesis() {
        return netheopoiesis;
    }

    public boolean isSlimeHud() {
        return slimeHud;
    }

    public boolean isRoseStacker() {
        return roseStacker && roseStackerAPI != null;
    }

    public boolean isWildStacker() {
        return wildStacker;
    }

    public boolean isGuguSlimefunLib() {
        return guguSlimefunLib;
    }

    public @Nullable RoseStackerAPI getRoseStackerAPI() {
        return roseStackerAPI;
    }

    public boolean isFinalTECH() {
        return finalTECH;
    }

    public boolean isJustEnoughGuide() {
        return justEnoughGuide;
    }

    public void setJustEnoughGuide(boolean justEnoughGuide) {
        this.justEnoughGuide = justEnoughGuide;
    }

    public boolean isMcMMO() {
        return mcMMO;
    }

    public boolean isWildChests() {
        return wildChests;
    }

    public boolean isLogitech() {
        return logitech && logitechIntegration != null;
    }

    public @Nullable LogitechIntegration getLogitechIntegration() {
        return logitechIntegration;
    }
}

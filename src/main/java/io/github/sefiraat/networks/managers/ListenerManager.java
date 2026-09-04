package io.github.sefiraat.networks.managers;

import com.balugaq.netex.core.listeners.HangingBlockInteractListener;
import com.balugaq.netex.core.listeners.JEGCompatibleListener;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.listeners.ExplosiveToolListener;
import io.github.sefiraat.networks.listeners.NetworkIntegrityListener;
import io.github.sefiraat.networks.listeners.SyncListener;
import io.github.sefiraat.networks.utils.QuantumStorageDisplayManager;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;

public class ListenerManager {

    public ListenerManager() {
        QuantumStorageDisplayManager.initialize();
        addListener(new ExplosiveToolListener());
        addListener(new NetworkIntegrityListener());
        addListener(new SyncListener());
        if (Networks.getSupportedPluginManager().isJustEnoughGuide()) {
            // todo: remove and deprecate
            try {
                addListener(new JEGCompatibleListener());
            } catch (Throwable ignored) {
                Networks.getSupportedPluginManager().setJustEnoughGuide(false);
            }
        }
        addListener(new HangingBlockInteractListener());
    }

    private void addListener(@NotNull Listener listener) {
        Networks.getPluginManager().registerEvents(listener, Networks.getInstance());
    }
}

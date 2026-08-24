package io.github.sefiraat.networks.utils;

import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;

/**
 * Debounces topology-dirty signals caused by new node registrations.
 *
 * <p>Chunk activation can register hundreds of nodes over several server ticks. Rebuilding after each batch wastes
 * work, so registration-driven marks wait for a short quiet window. Destructive changes such as block removal and
 * chunk unload still call {@link NetworkController#markTopologyDirty(Location)} directly and are never delayed.</p>
 */
public final class TopologyDirtyQueue {

    private static final int DEFAULT_DEBOUNCE_SF_TICKS = 2;
    private static final Map<Location, Long> PENDING = new ConcurrentHashMap<>();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final LongAdder MARKS = new LongAdder();
    private static final LongAdder COALESCED = new LongAdder();
    private static final LongAdder FLUSHED = new LongAdder();

    private static volatile BukkitTask task;
    private static volatile int debounceSfTicks = DEFAULT_DEBOUNCE_SF_TICKS;

    private TopologyDirtyQueue() {
    }

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        Networks plugin = Networks.getInstance();
        debounceSfTicks = Math.max(0, plugin.getConfig().getInt(
            "stability.topology.rebuild-debounce-sf-ticks",
            DEFAULT_DEBOUNCE_SF_TICKS));
        PENDING.clear();
        MARKS.reset();
        COALESCED.reset();
        FLUSHED.reset();

        long period = Math.max(1L, Slimefun.getTickerTask().getTickRate());
        task = Bukkit.getScheduler().runTaskTimer(plugin, TopologyDirtyQueue::flushReady, period, period);
    }

    public static void stop() {
        BukkitTask current = task;
        if (current != null) {
            current.cancel();
            task = null;
        }
        PENDING.clear();
        STARTED.set(false);
    }

    /** Records or refreshes a registration-driven dirty mark for one controller. */
    public static void mark(@NotNull Location controllerLocation) {
        final Location key = normalize(controllerLocation);
        MARKS.increment();
        if (debounceSfTicks <= 0 || !STARTED.get()) {
            NetworkController.markTopologyDirty(key);
            FLUSHED.increment();
            return;
        }

        Long previous = PENDING.put(key, Networks.getSlimefunTickCount());
        if (previous != null) {
            COALESCED.increment();
        }
    }

    public static int getPendingCount() {
        return PENDING.size();
    }

    public static long getMarkCount() {
        return MARKS.sum();
    }

    public static long getCoalescedCount() {
        return COALESCED.sum();
    }

    public static long getFlushedCount() {
        return FLUSHED.sum();
    }

    public static int getDebounceSfTicks() {
        return debounceSfTicks;
    }

    private static void flushReady() {
        final long now = Networks.getSlimefunTickCount();
        for (Map.Entry<Location, Long> entry : PENDING.entrySet()) {
            if (now - entry.getValue() < debounceSfTicks) {
                continue;
            }
            if (PENDING.remove(entry.getKey(), entry.getValue())) {
                NetworkController.markTopologyDirty(entry.getKey());
                FLUSHED.increment();
            }
        }
    }

    private static @NotNull Location normalize(@NotNull Location location) {
        Location normalized = location.clone();
        normalized.setX(location.getBlockX());
        normalized.setY(location.getBlockY());
        normalized.setZ(location.getBlockZ());
        normalized.setYaw(0.0F);
        normalized.setPitch(0.0F);
        return normalized;
    }
}

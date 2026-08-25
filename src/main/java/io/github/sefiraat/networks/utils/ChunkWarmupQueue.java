package io.github.sefiraat.networks.utils;

import io.github.sefiraat.networks.Networks;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.logging.Level;

/**
 * Shared main-thread warmup queue for Network nodes that become active after a chunk load.
 *
 * <p>Slimefun can wake hundreds or thousands of Network block tickers in the same server tick. Scheduling one
 * Bukkit task per block turns that wakeup into a second task burst on the main thread. This queue keeps the same
 * synchronous initialization semantics but spreads the work across ticks using both a count and wall-time budget.</p>
 */
public final class ChunkWarmupQueue {

    private static final int DEFAULT_MAX_INITIALIZATIONS_PER_TICK = 128;
    private static final long DEFAULT_MAX_WORK_MILLIS_PER_TICK = 2L;

    private static final Queue<WarmupTask> QUEUE = new ConcurrentLinkedQueue<>();
    private static final Set<Location> PENDING_LOCATIONS = ConcurrentHashMap.newKeySet();
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static final AtomicInteger PENDING_COUNT = new AtomicInteger();
    private static final AtomicLong PEAK_PENDING = new AtomicLong();
    private static final LongAdder PROCESSED = new LongAdder();
    private static final LongAdder BUDGET_YIELDS = new LongAdder();

    private static volatile BukkitTask workerTask;
    private static volatile int maxInitializationsPerTick = DEFAULT_MAX_INITIALIZATIONS_PER_TICK;
    private static volatile long maxWorkNanosPerTick = DEFAULT_MAX_WORK_MILLIS_PER_TICK * 1_000_000L;

    private ChunkWarmupQueue() {
    }

    public static void start() {
        if (!STARTED.compareAndSet(false, true)) {
            return;
        }

        Networks plugin = Networks.getInstance();
        maxInitializationsPerTick = Math.max(1, plugin.getConfig().getInt(
            "stability.chunk-warmup.max-node-initializations-per-tick",
            DEFAULT_MAX_INITIALIZATIONS_PER_TICK));
        long maxWorkMillis = Math.max(1L, plugin.getConfig().getLong(
            "stability.chunk-warmup.max-work-millis-per-tick",
            DEFAULT_MAX_WORK_MILLIS_PER_TICK));
        maxWorkNanosPerTick = maxWorkMillis * 1_000_000L;

        PENDING_COUNT.set(0);
        PEAK_PENDING.set(0L);
        PROCESSED.reset();
        BUDGET_YIELDS.reset();

        workerTask = Bukkit.getScheduler().runTaskTimer(plugin, ChunkWarmupQueue::drainOneTick, 1L, 1L);
        TopologyDirtyQueue.start();
    }

    public static void stop() {
        TopologyDirtyQueue.stop();
        BukkitTask task = workerTask;
        if (task != null) {
            task.cancel();
            workerTask = null;
        }
        QUEUE.clear();
        PENDING_LOCATIONS.clear();
        PENDING_COUNT.set(0);
        STARTED.set(false);
    }

    /**
     * Queues one block-location initialization. Duplicate wakeups for the same location collapse into one task.
     */
    public static boolean enqueue(@NotNull Location location, @NotNull Runnable initialization) {
        final Location key = normalize(location);
        if (!PENDING_LOCATIONS.add(key)) {
            return false;
        }

        QUEUE.add(new WarmupTask(key, initialization));
        int pending = PENDING_COUNT.incrementAndGet();
        PEAK_PENDING.accumulateAndGet(pending, Math::max);
        return true;
    }

    public static int getPendingCount() {
        return Math.max(0, PENDING_COUNT.get());
    }

    public static long getPeakPendingCount() {
        return PEAK_PENDING.get();
    }

    public static long getProcessedCount() {
        return PROCESSED.sum();
    }

    public static long getBudgetYieldCount() {
        return BUDGET_YIELDS.sum();
    }

    public static int getMaxInitializationsPerTick() {
        return maxInitializationsPerTick;
    }

    public static long getMaxWorkMillisPerTick() {
        return Math.max(1L, maxWorkNanosPerTick / 1_000_000L);
    }

    private static void drainOneTick() {
        final long startedAt = System.nanoTime();
        int processedThisTick = 0;

        while (processedThisTick < maxInitializationsPerTick
            && System.nanoTime() - startedAt < maxWorkNanosPerTick) {
            WarmupTask warmup = QUEUE.poll();
            if (warmup == null) {
                break;
            }

            PENDING_LOCATIONS.remove(warmup.location());
            PENDING_COUNT.updateAndGet(value -> Math.max(0, value - 1));

            try {
                warmup.initialization().run();
            } catch (RuntimeException | LinkageError exception) {
                Networks.getInstance().getLogger().log(
                    Level.WARNING,
                    "Network node warmup failed at " + format(warmup.location())
                        + "; the node may retry on a later Slimefun tick.",
                    exception);
            }

            processedThisTick++;
            PROCESSED.increment();
        }

        if (!QUEUE.isEmpty()) {
            BUDGET_YIELDS.increment();
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

    private static @NotNull String format(@NotNull Location location) {
        return (location.getWorld() == null ? "<unloaded>" : location.getWorld().getName())
            + ':' + location.getBlockX() + ',' + location.getBlockY() + ',' + location.getBlockZ();
    }

    private record WarmupTask(@NotNull Location location, @NotNull Runnable initialization) {
    }
}

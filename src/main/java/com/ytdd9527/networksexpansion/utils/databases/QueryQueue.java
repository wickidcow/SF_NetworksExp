package com.ytdd9527.networksexpansion.utils.databases;

import com.balugaq.netex.utils.Debug;
import com.balugaq.netex.utils.Lang;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * Serial database task queue.
 *
 * <p>Networks stores drawer data in one SQLite connection. Every query and update is deliberately
 * ordered through one worker so reads cannot overtake writes and shutdown never closes the
 * connection while a queued statement is still expected to run.</p>
 */
public final class QueryQueue {

    private static final QueuedTask STOP_TASK = () -> true;

    private final @NotNull BlockingQueue<QueuedTask> tasks = new LinkedBlockingQueue<>();
    private final @NotNull AtomicBoolean started = new AtomicBoolean();
    private final @NotNull AtomicBoolean accepting = new AtomicBoolean(true);
    private final @NotNull AtomicInteger inFlight = new AtomicInteger();
    private final @NotNull LongAdder scheduled = new LongAdder();
    private final @NotNull LongAdder executed = new LongAdder();
    private final @NotNull LongAdder failed = new LongAdder();
    private final @NotNull LongAdder rejected = new LongAdder();
    private final @NotNull LongAdder cancelled = new LongAdder();
    private final @NotNull Object lifecycleMonitor = new Object();
    private final @NotNull Object drainMonitor = new Object();
    private volatile Thread worker;
    private volatile String lastFailure = "none";

    public void scheduleUpdate(@NotNull QueuedTask task) {
        schedule(task);
    }

    public void scheduleQuery(@NotNull QueuedTask task) {
        schedule(task);
    }

    private void schedule(@NotNull QueuedTask task) {
        synchronized (lifecycleMonitor) {
            if (!accepting.get()) {
                rejected.increment();
                throw new IllegalStateException("Networks database queue is shutting down");
            }
            if (!tasks.offer(task)) {
                rejected.increment();
                throw new IllegalStateException(
                    Lang.getString("messages.unsupported-operation.comprehensive.invalid_queue"));
            }
            scheduled.increment();
        }
        signalStateChanged();
    }

    public synchronized void startThread() {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        accepting.set(true);
        worker = new Thread(this::processTasks, "Networks-Database-Worker");
        worker.setDaemon(true);
        worker.setUncaughtExceptionHandler((thread, throwable) -> {
            failed.increment();
            lastFailure = compactFailure(throwable);
            Debug.trace(throwable);
        });
        worker.start();
    }

    private void processTasks() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                QueuedTask task = tasks.take();
                if (task == STOP_TASK) {
                    break;
                }

                inFlight.incrementAndGet();
                try {
                    boolean callbackRequested = task.execute();
                    if (callbackRequested && task.callback()) {
                        accepting.set(false);
                        executed.increment();
                        break;
                    }
                    executed.increment();
                } catch (Throwable throwable) {
                    failed.increment();
                    lastFailure = compactFailure(throwable);
                    Debug.trace(throwable);
                } finally {
                    inFlight.decrementAndGet();
                    signalStateChanged();
                }
            }
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        } finally {
            accepting.set(false);
            started.set(false);
            tasks.remove(STOP_TASK);
            signalStateChanged();
        }
    }

    public int getTaskAmount() {
        int queued = tasks.size();
        if (tasks.contains(STOP_TASK)) {
            queued--;
        }
        return Math.max(0, queued) + inFlight.get();
    }

    public int getQueuedTaskAmount() {
        return Math.max(0, getTaskAmount() - inFlight.get());
    }

    public int getInFlightTaskAmount() {
        return inFlight.get();
    }

    public boolean isAcceptingTasks() {
        return accepting.get();
    }

    public boolean isWorkerRunning() {
        Thread currentWorker = worker;
        return currentWorker != null && currentWorker.isAlive();
    }

    public boolean isAllDone() {
        return getTaskAmount() == 0;
    }

    public @NotNull QueueSnapshot snapshot() {
        return new QueueSnapshot(
            getQueuedTaskAmount(),
            getInFlightTaskAmount(),
            scheduled.sum(),
            executed.sum(),
            failed.sum(),
            rejected.sum(),
            cancelled.sum(),
            accepting.get(),
            isWorkerRunning(),
            lastFailure);
    }

    /** Waits for all work queued before and during the wait to finish. */
    public boolean awaitDrained(long timeoutMillis) {
        long remainingNanos = TimeUnit.MILLISECONDS.toNanos(Math.max(0L, timeoutMillis));
        long deadline = System.nanoTime() + remainingNanos;

        synchronized (drainMonitor) {
            while (!isAllDone()) {
                if (remainingNanos <= 0L) {
                    return false;
                }
                try {
                    TimeUnit.NANOSECONDS.timedWait(drainMonitor, remainingNanos);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return false;
                }
                remainingNanos = deadline - System.nanoTime();
            }
        }
        return true;
    }

    /**
     * Stops accepting work, drains within the configured deadline, and terminates the worker.
     *
     * <p>If the deadline expires, work which has not started is discarded and the worker is
     * interrupted. The return value is only {@code true} when all scheduled work completed and the
     * worker fully stopped, making it safe for the caller to close the SQLite connection.</p>
     */
    public boolean shutdown(long timeoutMillis) {
        long safeTimeoutMillis = Math.max(0L, timeoutMillis);
        long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(safeTimeoutMillis);

        synchronized (lifecycleMonitor) {
            accepting.set(false);
        }

        boolean drained = awaitDrained(safeTimeoutMillis);
        if (!drained) {
            cancelQueuedTasks();
        }

        tasks.offer(STOP_TASK);
        signalStateChanged();

        Thread currentWorker = worker;
        if (currentWorker == null || currentWorker == Thread.currentThread()) {
            return drained && !isWorkerRunning();
        }

        joinUntil(currentWorker, deadlineNanos);
        if (currentWorker.isAlive()) {
            currentWorker.interrupt();
            cancelQueuedTasks();
            tasks.offer(STOP_TASK);
            joinFor(currentWorker, 2000L);
        }

        return drained && !currentWorker.isAlive();
    }

    /** Compatibility bridge for older shutdown code. */
    public void scheduleAbort() {
        shutdown(0L);
    }

    private void cancelQueuedTasks() {
        int before = getQueuedTaskAmount();
        tasks.clear();
        if (before > 0) {
            cancelled.add(before);
        }
        signalStateChanged();
    }

    private static void joinUntil(@NotNull Thread thread, long deadlineNanos) {
        long remainingNanos = deadlineNanos - System.nanoTime();
        if (remainingNanos <= 0L) {
            return;
        }
        joinFor(thread, Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos)));
    }

    private static void joinFor(@NotNull Thread thread, long millis) {
        try {
            thread.join(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    private void signalStateChanged() {
        synchronized (drainMonitor) {
            drainMonitor.notifyAll();
        }
    }

    private static @NotNull String compactFailure(@NotNull Throwable throwable) {
        String type = throwable.getClass().getSimpleName();
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return type;
        }
        String compact = message.replace('\n', ' ').replace('\r', ' ').trim();
        if (compact.length() > 160) {
            compact = compact.substring(0, 157) + "...";
        }
        return type + ": " + compact;
    }

    public record QueueSnapshot(
        int queued,
        int inFlight,
        long scheduled,
        long executed,
        long failed,
        long rejected,
        long cancelled,
        boolean accepting,
        boolean workerRunning,
        @NotNull String lastFailure
    ) {
    }
}

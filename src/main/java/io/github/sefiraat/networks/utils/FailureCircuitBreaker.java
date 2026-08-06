package io.github.sefiraat.networks.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Small thread-safe circuit breaker for runtime work keyed by a stable identity.
 *
 * <p>Failures are retained until a successful attempt clears them. Once the configured failure threshold is
 * reached, attempts are paused for an exponentially increasing cooldown. The breaker stores only a compact
 * failure description and never retains the original throwable.</p>
 */
public final class FailureCircuitBreaker<K> {

    private static final int MAX_FAILURE_MESSAGE_LENGTH = 180;

    private final int failureThreshold;
    private final long initialCooldownMillis;
    private final long maximumCooldownMillis;
    private final Map<K, MutableState> states = new ConcurrentHashMap<>();
    private final LongAdder totalFailures = new LongAdder();
    private final LongAdder totalTrips = new LongAdder();

    public FailureCircuitBreaker(int failureThreshold, long initialCooldownMillis, long maximumCooldownMillis) {
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be at least 1");
        }
        if (initialCooldownMillis < 1L) {
            throw new IllegalArgumentException("initialCooldownMillis must be positive");
        }
        if (maximumCooldownMillis < initialCooldownMillis) {
            throw new IllegalArgumentException("maximumCooldownMillis must be at least the initial cooldown");
        }
        this.failureThreshold = failureThreshold;
        this.initialCooldownMillis = initialCooldownMillis;
        this.maximumCooldownMillis = maximumCooldownMillis;
    }

    public boolean canAttempt(@NotNull K key, long nowMillis) {
        MutableState state = states.get(key);
        return state == null || nowMillis >= state.blockedUntilMillis;
    }

    public @NotNull FailureSnapshot recordFailure(
        @NotNull K key,
        @NotNull Throwable throwable,
        long nowMillis
    ) {
        totalFailures.increment();
        final boolean[] trippedNow = {false};
        MutableState state = states.compute(key, (ignored, current) -> {
            MutableState next = current == null ? new MutableState() : current;
            next.consecutiveFailures++;
            next.lastFailureAtMillis = nowMillis;
            next.failureType = throwable.getClass().getSimpleName();
            next.failureMessage = compactMessage(throwable.getMessage());

            if (next.consecutiveFailures >= failureThreshold && nowMillis >= next.blockedUntilMillis) {
                next.tripCount++;
                next.blockedUntilMillis = saturatingAdd(nowMillis, cooldownForTrip(next.tripCount));
                trippedNow[0] = true;
            }
            return next;
        });

        if (trippedNow[0]) {
            totalTrips.increment();
        }
        return state.snapshot(trippedNow[0]);
    }

    /** Clears a prior failure state and returns its final snapshot, if one existed. */
    public @Nullable FailureSnapshot recordSuccess(@NotNull K key) {
        MutableState removed = states.remove(key);
        return removed == null ? null : removed.snapshot(false);
    }

    public void clear(@NotNull K key) {
        states.remove(key);
    }

    public void clearAll() {
        states.clear();
        totalFailures.reset();
        totalTrips.reset();
    }

    public int getTrackedKeyCount() {
        return states.size();
    }

    public int getBlockedKeyCount(long nowMillis) {
        int count = 0;
        for (MutableState state : states.values()) {
            if (nowMillis < state.blockedUntilMillis) {
                count++;
            }
        }
        return count;
    }

    public long getTotalFailures() {
        return totalFailures.sum();
    }

    public long getTotalTrips() {
        return totalTrips.sum();
    }

    public @NotNull Map<K, FailureSnapshot> snapshot() {
        Map<K, FailureSnapshot> snapshot = new HashMap<>();
        states.forEach((key, state) -> snapshot.put(key, state.snapshot(false)));
        return Collections.unmodifiableMap(snapshot);
    }

    private long cooldownForTrip(int tripCount) {
        int shift = Math.min(30, Math.max(0, tripCount - 1));
        long multiplier = 1L << shift;
        if (initialCooldownMillis > maximumCooldownMillis / multiplier) {
            return maximumCooldownMillis;
        }
        return Math.min(maximumCooldownMillis, initialCooldownMillis * multiplier);
    }

    private static long saturatingAdd(long left, long right) {
        if (right > 0L && left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    private static @NotNull String compactMessage(@Nullable String message) {
        if (message == null || message.isBlank()) {
            return "no message";
        }
        String compact = message.replaceAll("\\s+", " ").trim();
        if (compact.length() <= MAX_FAILURE_MESSAGE_LENGTH) {
            return compact;
        }
        return compact.substring(0, MAX_FAILURE_MESSAGE_LENGTH - 3) + "...";
    }

    public record FailureSnapshot(
        int consecutiveFailures,
        int tripCount,
        long blockedUntilMillis,
        long lastFailureAtMillis,
        @NotNull String failureType,
        @NotNull String failureMessage,
        boolean trippedNow
    ) {
        public boolean isBlocked(long nowMillis) {
            return nowMillis < blockedUntilMillis;
        }

        public long remainingCooldownMillis(long nowMillis) {
            return Math.max(0L, blockedUntilMillis - nowMillis);
        }
    }

    private static final class MutableState {
        private int consecutiveFailures;
        private int tripCount;
        private long blockedUntilMillis;
        private long lastFailureAtMillis;
        private String failureType = "unknown";
        private String failureMessage = "no message";

        private @NotNull FailureSnapshot snapshot(boolean trippedNow) {
            return new FailureSnapshot(
                consecutiveFailures,
                tripCount,
                blockedUntilMillis,
                lastFailureAtMillis,
                failureType,
                failureMessage,
                trippedNow);
        }
    }
}

package io.github.sefiraat.networks.slimefun.network;

import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Controlled tick-versus-break interleavings for a cache that has already been initialized.
 * No actual server state, world block, or asynchronous Bukkit API is needed.
 */
class QuantumCacheLifecycleTest {
    private final Location location = new Location(null, 417, 64, 429);
    private final Map<Location, QuantumCache> active = new ConcurrentHashMap<>();
    private final QuantumCache cache = mock(QuantumCache.class);

    @Test
    void breakWaitsForInFlightTickThenSerializesTheFinalQuantity() throws Exception {
        AtomicInteger stored = new AtomicInteger(20);
        AtomicInteger physicalOutput = new AtomicInteger();
        AtomicInteger droppedCache = new AtomicInteger(-1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        CountDownLatch breakStarted = new CountDownLatch(1);
        active.put(location, cache);

        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> tick = workers.submit(() ->
                QuantumCacheLifecycle.withCurrent(active, location, cache, current -> {
                    entered.countDown();
                    await(proceed);
                    physicalOutput.addAndGet(7);
                    stored.addAndGet(-7);
                }));
            assertTrue(entered.await(5, TimeUnit.SECONDS));

            Future<Boolean> broken = workers.submit(() -> {
                breakStarted.countDown();
                return QuantumCacheLifecycle.retireCurrent(active, location,
                    current -> droppedCache.set(stored.get()));
            });
            assertTrue(breakStarted.await(5, TimeUnit.SECONDS));
            assertFalse(broken.isDone(), "A break must not serialize while the tick holds the cache");

            proceed.countDown();
            assertTrue(tick.get(5, TimeUnit.SECONDS));
            assertTrue(broken.get(5, TimeUnit.SECONDS));
            assertEquals(13, droppedCache.get());
            assertEquals(20, physicalOutput.get() + droppedCache.get());
            assertFalse(active.containsKey(location));
        } finally {
            proceed.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void alreadyDetachedCacheCannotBeReusedByQueuedTickOrNetworkTransfer() {
        active.put(location, cache);
        AtomicInteger dropAmount = new AtomicInteger();
        assertTrue(QuantumCacheLifecycle.retireCurrent(active, location,
            current -> dropAmount.set(20)));

        AtomicInteger accidentalMutation = new AtomicInteger();
        assertFalse(QuantumCacheLifecycle.withCurrent(active, location, cache,
            current -> accidentalMutation.incrementAndGet()));
        assertNull(QuantumCacheLifecycle.readCurrent(active, location, cache,
            () -> accidentalMutation.incrementAndGet()));
        assertEquals(0, accidentalMutation.get());
        assertEquals(20, dropAmount.get());
    }

    @Test
    void replacementAtSamePositionDoesNotGiveOldTickAccessToNewStorage() {
        active.put(location, cache);
        assertTrue(QuantumCacheLifecycle.retireCurrent(active, location, current -> {}));

        QuantumCache replacement = mock(QuantumCache.class);
        active.put(location, replacement);

        assertFalse(QuantumCacheLifecycle.withCurrent(active, location, cache,
            current -> fail("Stale tick must not mutate a replaced storage")));
        assertTrue(QuantumCacheLifecycle.withCurrent(active, location, replacement,
            current -> assertSame(replacement, current)));
        assertSame(replacement, active.get(location));
    }

    @Test
    void independentQuantumStoragesDoNotShareOneGlobalLock() throws Exception {
        final Location otherLocation = location.clone().add(1, 0, 0);
        final QuantumCache otherCache = mock(QuantumCache.class);
        active.put(location, cache);
        active.put(otherLocation, otherCache);
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch firstRelease = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<Boolean> first = workers.submit(() ->
                QuantumCacheLifecycle.withCurrent(active, location, cache, current -> {
                    firstEntered.countDown();
                    await(firstRelease);
                }));
            assertTrue(firstEntered.await(5, TimeUnit.SECONDS));
            Future<Boolean> second = workers.submit(() ->
                QuantumCacheLifecycle.withCurrent(active, otherLocation, otherCache,
                    current -> assertSame(otherCache, current)));

            assertTrue(second.get(5, TimeUnit.SECONDS), "Unrelated storage should remain accessible");
            firstRelease.countDown();
            assertTrue(first.get(5, TimeUnit.SECONDS));
        } finally {
            firstRelease.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void breakSnapshotKeepsCacheMappedUntilSerializationIsComplete() {
        active.put(location, cache);
        assertTrue(QuantumCacheLifecycle.retireCurrent(active, location, current -> {
            assertSame(cache, active.get(location),
                "Do not expose a cache-empty window while the break serializes contents");
            assertSame(cache, current);
        }));
        assertFalse(active.containsKey(location));
    }

    @Test
    void noCacheIsNotRetiredOrInventedByBreak() {
        assertFalse(QuantumCacheLifecycle.retireCurrent(active, location, c -> fail("No cache")));
        assertTrue(active.isEmpty());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for controlled interleaving");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted during controlled interleaving", e);
        }
    }
}

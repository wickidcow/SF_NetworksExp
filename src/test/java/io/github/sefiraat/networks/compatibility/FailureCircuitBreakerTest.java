package io.github.sefiraat.networks.compatibility;

import io.github.sefiraat.networks.utils.FailureCircuitBreaker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailureCircuitBreakerTest {

    @Test
    void tripsAtThresholdAndRecoversAfterCooldown() {
        FailureCircuitBreaker<String> breaker = new FailureCircuitBreaker<>(3, 1_000L, 8_000L);

        breaker.recordFailure("controller", new IllegalStateException("one"), 0L);
        breaker.recordFailure("controller", new IllegalStateException("two"), 10L);
        var third = breaker.recordFailure("controller", new IllegalStateException("three"), 20L);

        assertTrue(third.trippedNow());
        assertTrue(third.isBlocked(20L));
        assertFalse(breaker.canAttempt("controller", 999L));
        assertTrue(breaker.canAttempt("controller", 1_020L));
        assertEquals(3L, breaker.getTotalFailures());
        assertEquals(1L, breaker.getTotalTrips());
    }

    @Test
    void repeatedTripsBackOffAndRespectMaximum() {
        FailureCircuitBreaker<String> breaker = new FailureCircuitBreaker<>(1, 1_000L, 2_000L);

        var first = breaker.recordFailure("controller", new RuntimeException("first"), 0L);
        var second = breaker.recordFailure("controller", new RuntimeException("second"), 1_000L);
        var third = breaker.recordFailure("controller", new RuntimeException("third"), 3_000L);

        assertEquals(1_000L, first.blockedUntilMillis());
        assertEquals(3_000L, second.blockedUntilMillis());
        assertEquals(5_000L, third.blockedUntilMillis());
        assertEquals(3, third.tripCount());
    }

    @Test
    void successClearsFailureState() {
        FailureCircuitBreaker<String> breaker = new FailureCircuitBreaker<>(2, 1_000L, 4_000L);
        breaker.recordFailure("controller", new RuntimeException("temporary"), 0L);

        var previous = breaker.recordSuccess("controller");

        assertNotNull(previous);
        assertEquals(1, previous.consecutiveFailures());
        assertNull(breaker.recordSuccess("controller"));
        assertTrue(breaker.canAttempt("controller", 0L));
        assertEquals(0, breaker.getTrackedKeyCount());
    }

    @Test
    void storesOnlyCompactFailureDescription() {
        FailureCircuitBreaker<String> breaker = new FailureCircuitBreaker<>(1, 1_000L, 1_000L);
        String noisy = "line one\n" + "x".repeat(300);

        var snapshot = breaker.recordFailure("controller", new IllegalArgumentException(noisy), 0L);

        assertEquals("IllegalArgumentException", snapshot.failureType());
        assertFalse(snapshot.failureMessage().contains("\n"));
        assertTrue(snapshot.failureMessage().length() <= 180);
    }
}

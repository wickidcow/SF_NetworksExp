package io.github.sefiraat.networks.integrations.infinityexpansion2;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class PositionAccessorCacheTest {

    @Test
    void resolvesARepeatedPublicAccessorOnceButReadsEveryLiveValue() {
        var discoveries = new AtomicInteger();
        var cache = counting(discoveries);
        var key = new MutableKey();
        for (int index = 0; index < 10_000; index++) {
            key.value = index;
            assertEquals(index, cache.invoke(key, "getX"));
        }
        assertEquals(1, discoveries.get());
        assertEquals(10_000, key.calls.get());
    }

    @Test
    void cachesMissingMethodsWithoutRepeatedExceptions() {
        var discoveries = new AtomicInteger();
        var cache = counting(discoveries);
        for (int index = 0; index < 10_000; index++) {
            assertNull(cache.invoke(new MutableKey(), "getBlock"));
        }
        assertEquals(1, discoveries.get());
    }

    @Test
    void neverSharesAValueBetweenInstancesOrAccessorNames() {
        var discoveries = new AtomicInteger();
        var cache = counting(discoveries);
        var first = new MutableKey();
        var second = new MutableKey();
        first.value = 42;
        second.value = -17;
        assertEquals(42, cache.invoke(first, "getX"));
        assertEquals(-17, cache.invoke(second, "getX"));
        assertEquals(43, cache.invoke(first, "x"));
        assertEquals(2, discoveries.get());
        assertEquals(2, first.calls.get());
        assertEquals(1, second.calls.get());
    }

    @Test
    void resolvesInheritedPublicMethodsAndKeepsVirtualDispatch() {
        var cache = new PositionAccessorCache();
        assertEquals(0, cache.invoke(new InheritedKey(), "getX"));
        assertEquals(81, cache.invoke(new OverriddenKey(), "getX"));
        assertEquals("default-world", cache.invoke(new DefaultKey(), "world"));
    }

    @Test
    void doesNotUsePrivateOrArgumentTakingMethodsAsNoArgumentAccessors() {
        var cache = new PositionAccessorCache();
        assertNull(cache.invoke(new NonPublicAccessor(), "getX"));
        assertNull(cache.invoke(new ArgumentAccessor(), "getX"));
    }

    @Test
    void separatesMethodAbsenceByExactRuntimeClass() {
        var discoveries = new AtomicInteger();
        var cache = counting(discoveries);
        assertNull(cache.invoke(new ArgumentAccessor(), "getX"));
        assertEquals(0, cache.invoke(new MutableKey(), "getX"));
        assertNull(cache.invoke(new ArgumentAccessor(), "getX"));
        assertEquals(2, discoveries.get());
    }

    @Test
    void nullAccessorResultsAreNeverNegativeCached() {
        var cache = new PositionAccessorCache();
        var key = new NullableKey();
        assertNull(cache.invoke(key, "world"));
        key.world = "restored-world";
        assertEquals("restored-world", cache.invoke(key, "world"));
        assertEquals(2, key.calls);
    }

    @Test
    void invocationFailuresCanRecoverWithoutRediscoveringMetadata() {
        var discoveries = new AtomicInteger();
        var cache = counting(discoveries);
        var key = new RecoverableKey();
        for (int mode = 0; mode < 3; mode++) {
            key.mode = mode;
            assertNull(cache.invoke(key, "getX"));
        }
        key.mode = 3;
        assertEquals(73, cache.invoke(key, "getX"));
        assertEquals(1, discoveries.get());
        assertEquals(4, key.calls);
    }

    @Test
    void temporarySecurityFailureDuringDiscoveryIsRetried() {
        var discoveries = new AtomicInteger();
        var cache = new PositionAccessorCache((type, name) -> {
            if (discoveries.incrementAndGet() == 1) throw new SecurityException("temporary refusal");
            return type.getMethod(name);
        });
        var key = new MutableKey();
        assertNull(cache.invoke(key, "getX"));
        assertEquals(0, cache.invoke(key, "getX"));
        assertEquals(0, cache.invoke(key, "getX"));
        assertEquals(2, discoveries.get());
        assertEquals(2, key.calls.get());
    }

    @Test
    void temporaryLinkageFailureDuringDiscoveryIsRetried() {
        var discoveries = new AtomicInteger();
        var cache = new PositionAccessorCache((type, name) -> {
            if (discoveries.incrementAndGet() == 1) throw new NoClassDefFoundError("optional dependency unavailable");
            return type.getMethod(name);
        });
        assertNull(cache.invoke(new MutableKey(), "getX"));
        assertEquals(0, cache.invoke(new MutableKey(), "getX"));
        assertEquals(2, discoveries.get());
    }

    @Test
    void preservesEagerFallbackCallbackOrdering() {
        var cache = new PositionAccessorCache();
        var key = new OrderedKey();
        assertEquals("preferred", firstNonNull(cache.invoke(key, "getWorld"), cache.invoke(key, "world")));
        assertEquals(List.of("getWorld", "world"), key.calls);
        key.calls.clear();
        assertEquals("preferred", firstNonNull(cache.invoke(key, "getWorld"), cache.invoke(key, "world")));
        assertEquals(List.of("getWorld", "world"), key.calls);
    }

    @Test
    void concurrentDiscoveryIsSharedWithoutCachingValuesOrMissingInvocations() throws Exception {
        var discoveries = new AtomicInteger();
        var cache = counting(discoveries);
        var key = new MutableKey();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var tasks = new ArrayList<java.util.concurrent.Future<?>>();
            for (int worker = 0; worker < 8; worker++) {
                tasks.add(executor.submit(() -> {
                    assertTrue(start.await(5, TimeUnit.SECONDS));
                    for (int iteration = 0; iteration < 1000; iteration++) {
                        assertEquals(0, cache.invoke(key, "getX"));
                        assertNull(cache.invoke(key, "getLocation"));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (var task : tasks) task.get(10, TimeUnit.SECONDS);
        }
        assertEquals(2, discoveries.get());
        assertEquals(8000, key.calls.get());
    }

    @Test
    void supportsPublicStaticAccessorsLikeTheOriginalReflectionPath() {
        assertEquals(9, new PositionAccessorCache().invoke(new StaticKey(), "getX"));
    }

    private static PositionAccessorCache counting(AtomicInteger discoveries) {
        return new PositionAccessorCache((type, name) -> {
            discoveries.incrementAndGet();
            return type.getMethod(name);
        });
    }

    private static Object firstNonNull(Object first, Object second) {
        return first != null ? first : second;
    }

    public static class MutableKey {
        int value;
        final AtomicInteger calls = new AtomicInteger();
        public int getX() { calls.incrementAndGet(); return value; }
        public int x() { calls.incrementAndGet(); return value + 1; }
    }

    public static final class InheritedKey extends MutableKey { }
    public static final class OverriddenKey extends MutableKey {
        @Override public int getX() { return 81; }
    }
    public interface DefaultWorld { default String world() { return "default-world"; } }
    public static final class DefaultKey implements DefaultWorld { }
    public static final class NonPublicAccessor { private int getX() { return 7; } }
    public static final class ArgumentAccessor { public int getX(int offset) { return offset; } }
    public static final class StaticKey { public static int getX() { return 9; } }
    public static final class NullableKey {
        String world;
        int calls;
        public String world() { calls++; return world; }
    }
    public static final class RecoverableKey {
        int mode;
        int calls;
        public int getX() throws ReflectiveOperationException {
            calls++;
            if (mode == 0) throw new IllegalStateException("not ready");
            if (mode == 1) throw new NoClassDefFoundError("optional dependency");
            if (mode == 2) throw new ReflectiveOperationException("retry read");
            return 73;
        }
    }
    public static final class OrderedKey {
        final List<String> calls = new ArrayList<>();
        public String getWorld() { calls.add("getWorld"); return "preferred"; }
        public String world() { calls.add("world"); return "fallback"; }
    }
}

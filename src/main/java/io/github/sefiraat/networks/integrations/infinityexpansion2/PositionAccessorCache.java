package io.github.sefiraat.networks.integrations.infinityexpansion2;

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Caches only public no-argument method discovery for optional storage position keys.
 * Values are invoked afresh on the supplied key; no location or inventory state is cached.
 * ClassValue avoids retaining obsolete addon class loaders in a global Class-keyed map.
 */
final class PositionAccessorCache {

    @FunctionalInterface
    interface Lookup {
        Method find(Class<?> type, String name) throws NoSuchMethodException;
    }

    private final Lookup lookup;
    private final ClassValue<ConcurrentMap<String, Optional<Method>>> methods = new ClassValue<>() {
        @Override
        protected ConcurrentMap<String, Optional<Method>> computeValue(Class<?> type) {
            return new ConcurrentHashMap<>();
        }
    };

    PositionAccessorCache() {
        this((type, name) -> type.getMethod(name));
    }

    // Package-private injection verifies discovery counts without changing reflection access rules.
    PositionAccessorCache(Lookup lookup) {
        this.lookup = Objects.requireNonNull(lookup, "lookup");
    }

    Object invoke(Object target, String name) {
        try {
            final Class<?> type = target.getClass();
            final Optional<Method> method = methods.get(type).computeIfAbsent(name, key -> discover(type, key));
            return method.isPresent() ? method.get().invoke(target) : null;
        } catch (ReflectiveOperationException | SecurityException | IllegalArgumentException | LinkageError ignored) {
            // Preserve the existing optional-key fallback. Invocation failures are not cached.
            return null;
        }
    }

    private Optional<Method> discover(Class<?> type, String name) {
        try {
            return Optional.of(lookup.find(type, name));
        } catch (NoSuchMethodException ignored) {
            // Absence is stable for this class. Security/linkage failures instead retry next time.
            return Optional.empty();
        }
    }
}

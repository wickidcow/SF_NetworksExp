package io.github.sefiraat.networks.integrations.infinityexpansion2;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

/** Optional IE2 cache hold contract; keep IE2 an optional, lazily discovered integration. */
public final class IE2CacheTransferHold {
    private record GuardMethods(Optional<Method> transfer, Optional<Method> unassigned) {
    }

    // Method handles are reusable, but guard VALUES must always be live.
    private static final ClassValue<GuardMethods> GUARDS = new ClassValue<>() {
        @Override
        protected GuardMethods computeValue(Class<?> type) {
            return new GuardMethods(discover(type, "hasBlockedTransfer"),
                discover(type, "hasUnassignedItems"));
        }
    };

    private IE2CacheTransferHold() {
    }

    private static Optional<Method> discover(Class<?> type, String name) {
        final Method method;
        try {
            method = type.getMethod(name);
        } catch (NoSuchMethodException ignored) {
            return Optional.empty();
        } catch (SecurityException | LinkageError failure) {
            throw new IllegalStateException("Cannot discover IE2 cache guard " + name, failure);
        }
        if (Modifier.isStatic(method.getModifiers())
            || (method.getReturnType() != boolean.class && method.getReturnType() != Boolean.class)) {
            throw new IllegalStateException("Invalid IE2 cache guard type: " + name);
        }
        return Optional.of(method);
    }

    private static boolean read(Object cache, Method method) throws ReflectiveOperationException {
        try {
            final Object result = method.invoke(cache);
            if (!(result instanceof Boolean blocked)) {
                throw new ReflectiveOperationException("IE2 cache guard returned no boolean: " + method.getName());
            }
            return blocked;
        } catch (InvocationTargetException failure) {
            throw new ReflectiveOperationException("IE2 cache guard threw: " + method.getName(), failure.getCause());
        } catch (IllegalAccessException | IllegalArgumentException | SecurityException | LinkageError failure) {
            throw new ReflectiveOperationException("Cannot read IE2 cache guard: " + method.getName(), failure);
        }
    }

    /**
     * Both guards are independent: IE2 can hold a transaction in flight OR have
     * a positive stored balance without an assignable typed item. Neither permits
     * Networks to access physical input/output slots or advertise that balance.
     *
     * Truly old caches with neither guard keep legacy handling. A partially
     * exposed modern guard is not enough to establish a safe transaction.
     */
    public static boolean isBlocked(Object cache) throws ReflectiveOperationException {
        if (cache == null) {
            throw new ReflectiveOperationException("IE2 storage cache unavailable");
        }
        final GuardMethods methods;
        try {
            methods = GUARDS.get(cache.getClass());
        } catch (RuntimeException | LinkageError failure) {
            throw new ReflectiveOperationException("IE2 guard discovery failed", failure);
        }
        if (methods.transfer().isEmpty() && methods.unassigned().isEmpty()) {
            return false; // Pre-receipt IE2 version, no guard API at all.
        }
        if (methods.transfer().isEmpty() || methods.unassigned().isEmpty()) {
            return true; // Cannot verify the FULL modern IE2 safety contract.
        }
        return read(cache, methods.transfer().get())
            || read(cache, methods.unassigned().get());
    }
}

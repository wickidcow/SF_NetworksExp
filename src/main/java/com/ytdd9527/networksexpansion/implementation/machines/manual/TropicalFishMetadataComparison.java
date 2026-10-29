package com.ytdd9527.networksexpansion.implementation.machines.manual;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.function.ToIntFunction;
import org.bukkit.inventory.meta.TropicalFishBucketMeta;

/** Read-only bridge between packed 1.21.11 variants and independent 26.2+ components. */
final class TropicalFishMetadataComparison {
    private static final int PATTERN = 1;
    private static final int BODY_COLOR = 2;
    private static final int PATTERN_COLOR = 4;
    private static final int COMPLETE = PATTERN | BODY_COLOR | PATTERN_COLOR;
    private static final ToIntFunction<TropicalFishBucketMeta> PRESENCE =
        presenceReader(TropicalFishBucketMeta.class);

    private TropicalFishMetadataComparison() {}

    static String difference(TropicalFishBucketMeta first, TropicalFishBucketMeta second) {
        return difference(first, second, PRESENCE);
    }

    static String difference(TropicalFishBucketMeta first, TropicalFishBucketMeta second,
        ToIntFunction<TropicalFishBucketMeta> presence) {
        int firstFields = presence.applyAsInt(first);
        int secondFields = presence.applyAsInt(second);
        if (firstFields != secondFields) {
            return "neq.tropical.variant.has";
        }
        // Reading an absent component throws on supported server APIs. Do not invent defaults.
        if ((firstFields & PATTERN) != 0 && !first.getPattern().equals(second.getPattern())) {
            return "neq.tropical.pattern.get";
        }
        if ((firstFields & BODY_COLOR) != 0 && !first.getBodyColor().equals(second.getBodyColor())) {
            return "neq.tropical.bodycolor.get";
        }
        if ((firstFields & PATTERN_COLOR) != 0 && !first.getPatternColor().equals(second.getPatternColor())) {
            return "neq.tropical.patterncolor.get";
        }
        return null;
    }

    static ToIntFunction<TropicalFishBucketMeta> presenceReader(Class<?> api) {
        Method pattern = optional(api, "hasPattern");
        Method bodyColor = optional(api, "hasBodyColor");
        Method patternColor = optional(api, "hasPatternColor");
        if (pattern != null && bodyColor != null && patternColor != null) {
            return meta -> (read(pattern, meta) ? PATTERN : 0)
                | (read(bodyColor, meta) ? BODY_COLOR : 0)
                | (read(patternColor, meta) ? PATTERN_COLOR : 0);
        }
        if (pattern != null || bodyColor != null || patternColor != null) {
            throw new IllegalStateException("Incomplete tropical-fish component presence API");
        }
        // The 1.21.11 packed-variant API remains supported. Cache the public interface method;
        // avoid hard linkage to the accessor marked for removal on newer server APIs.
        Method legacy = optional(api, "hasVariant");
        if (legacy == null) {
            throw new IllegalStateException("No tropical-fish component presence API");
        }
        return meta -> read(legacy, meta) ? COMPLETE : 0;
    }

    private static Method optional(Class<?> api, String name) {
        try {
            Method method = api.getMethod(name);
            if (method.getReturnType() != boolean.class) {
                throw new IllegalStateException("Invalid tropical-fish presence method: " + name);
            }
            return method;
        } catch (NoSuchMethodException absent) {
            return null;
        }
    }

    private static boolean read(Method method, TropicalFishBucketMeta meta) {
        try {
            return (boolean) method.invoke(meta);
        } catch (IllegalAccessException | InvocationTargetException failure) {
            // Never classify two unreadable items as equal or fall back after a getter fails.
            throw new IllegalStateException("Cannot read tropical-fish presence: " + method.getName(), failure);
        }
    }
}

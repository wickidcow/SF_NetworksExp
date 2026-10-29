package com.ytdd9527.networksexpansion.implementation.machines.manual;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import org.bukkit.DyeColor;
import org.bukkit.entity.TropicalFish;
import org.bukkit.inventory.meta.TropicalFishBucketMeta;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/** Both API shapes are explicit: never silently fall back when a modern getter fails. */
class TropicalFishMetadataComparisonTest {
    public interface Components {
        boolean hasPattern();
        boolean hasBodyColor();
        boolean hasPatternColor();
    }
    public interface PackedVariant {
        boolean hasVariant();
    }
    public interface PartialApi {
        boolean hasPattern();
        boolean hasVariant();
    }
    public interface InvalidApi {
        String hasPattern();
        boolean hasBodyColor();
        boolean hasPatternColor();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void everyComponentPresenceCombinationIsComparedWithoutUndefinedReads(int firstMask) {
        var reader = TropicalFishMetadataComparison.presenceReader(Components.class);
        for (int secondMask = 0; secondMask < 8; secondMask++) {
            assertEquals(firstMask == secondMask ? null : "neq.tropical.variant.has",
                TropicalFishMetadataComparison.difference(
                    fish(firstMask, 0, true), fish(secondMask, 0, true), reader));
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5, 6, 7})
    void valuesKeepPatternThenBodyThenPatternColorMismatchPriority(int presentMask) {
        var reader = TropicalFishMetadataComparison.presenceReader(Components.class);
        for (int changed = 0; changed < 8; changed++) {
            int effective = presentMask & changed;
            String expected = (effective & 1) != 0 ? "neq.tropical.pattern.get"
                : (effective & 2) != 0 ? "neq.tropical.bodycolor.get"
                : (effective & 4) != 0 ? "neq.tropical.patterncolor.get" : null;
            assertEquals(expected, TropicalFishMetadataComparison.difference(
                fish(presentMask, 0, true), fish(presentMask, changed, true), reader));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void legacyPackedVariantReportsAllOrNoFieldsWithoutCallingNewMethods(boolean present) {
        var reader = TropicalFishMetadataComparison.presenceReader(PackedVariant.class);
        int mask = present ? 7 : 0;
        TropicalFishBucketMeta first = fish(mask, 0, false);
        assertEquals(mask, reader.applyAsInt(first));
        assertNull(TropicalFishMetadataComparison.difference(first, fish(mask, 0, false), reader));
    }

    @Test
    void incompleteOrMissingApiIsRejectedRatherThanClaimingEquality() {
        assertThrows(IllegalStateException.class,
            () -> TropicalFishMetadataComparison.presenceReader(PartialApi.class));
        assertThrows(IllegalStateException.class,
            () -> TropicalFishMetadataComparison.presenceReader(Object.class));
        assertThrows(IllegalStateException.class,
            () -> TropicalFishMetadataComparison.presenceReader(InvalidApi.class));
    }

    @Test
    void modernReadFailureDoesNotFallBackToDeprecatedPackedVariant() {
        TropicalFishBucketMeta broken = (TropicalFishBucketMeta) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{TropicalFishBucketMeta.class, Components.class},
            (proxy, method, args) -> {
                if (method.getName().equals("hasPattern")) {
                    throw new IllegalStateException("Cannot decode component");
                }
                throw new AssertionError("Unexpected fallback or subsequent getter: " + method.getName());
            });
        var reader = TropicalFishMetadataComparison.presenceReader(Components.class);
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> reader.applyAsInt(broken));
        assertInstanceOf(InvocationTargetException.class, failure.getCause());
        assertEquals("Cannot decode component", failure.getCause().getCause().getMessage());
    }

    @Test
    void aPresentButUnreadableValueIsNotReportedEqual() {
        var reader = TropicalFishMetadataComparison.presenceReader(Components.class);
        TropicalFishBucketMeta broken = (TropicalFishBucketMeta) Proxy.newProxyInstance(
            getClass().getClassLoader(), new Class<?>[]{TropicalFishBucketMeta.class, Components.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "hasPattern" -> true;
                case "hasBodyColor", "hasPatternColor" -> false;
                case "getPattern" -> throw new IllegalStateException("Unreadable present pattern");
                default -> throw new AssertionError("Unexpected call: " + method.getName());
            });
        IllegalStateException failure = assertThrows(IllegalStateException.class,
            () -> TropicalFishMetadataComparison.difference(broken, fish(1, 0, true), reader));
        assertEquals("Unreadable present pattern", failure.getMessage());
    }

    private static TropicalFishBucketMeta fish(int mask, int changed, boolean modern) {
        Class<?> shape = modern ? Components.class : PackedVariant.class;
        return (TropicalFishBucketMeta) Proxy.newProxyInstance(
            TropicalFishMetadataComparisonTest.class.getClassLoader(),
            new Class<?>[]{TropicalFishBucketMeta.class, shape},
            (proxy, method, args) -> switch (method.getName()) {
                case "hasVariant" -> {
                    assertFalse(modern, "Modern comparison must not use deprecated hasVariant");
                    yield mask == 7;
                }
                case "hasPattern", "hasBodyColor", "hasPatternColor" -> {
                    assertTrue(modern, "Packed API must not call newer presence methods");
                    int bit = method.getName().equals("hasPattern") ? 1
                        : method.getName().equals("hasBodyColor") ? 2 : 4;
                    yield (mask & bit) != 0;
                }
                case "getPattern" -> {
                    assertTrue((mask & 1) != 0, "Absent pattern getter must not be called");
                    yield (changed & 1) == 0 ? TropicalFish.Pattern.KOB : TropicalFish.Pattern.SUNSTREAK;
                }
                case "getBodyColor" -> {
                    assertTrue((mask & 2) != 0, "Absent body color getter must not be called");
                    yield (changed & 2) == 0 ? DyeColor.ORANGE : DyeColor.BLACK;
                }
                case "getPatternColor" -> {
                    assertTrue((mask & 4) != 0, "Absent pattern color getter must not be called");
                    yield (changed & 4) == 0 ? DyeColor.WHITE : DyeColor.BLACK;
                }
                default -> throw new AssertionError("Unexpected or mutating metadata call: " + method.getName());
            });
    }
}

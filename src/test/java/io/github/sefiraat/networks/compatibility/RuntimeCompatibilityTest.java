package io.github.sefiraat.networks.compatibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class RuntimeCompatibilityTest {

    @Test
    void comparesMinecraftPatchVersionsNumerically() {
        assertTrue(RuntimeCompatibility.compareVersions("1.21.11", "1.21.10") > 0);
        assertTrue(RuntimeCompatibility.compareVersions("1.22", "1.21.99") > 0);
        assertEquals(0, RuntimeCompatibility.compareVersions("1.21.11-R0.1-SNAPSHOT", "1.21.11"));
    }

    @Test
    void exposesAllSupportedCoreFamilies() {
        assertTrue(CoreVariant.SLIMEFUN_LEGACY.isExplicitlySupported());
        assertTrue(CoreVariant.SLIMEFUN_UNITED.isExplicitlySupported());
        assertTrue(CoreVariant.SLIMEFUN_GUGU.isExplicitlySupported());
    }

    @Test
    void classifiesLegacyFromMetadataOrDoctorMarker() {
        assertEquals(
            CoreVariant.SLIMEFUN_LEGACY,
            RuntimeCompatibility.classifyCore("Slimefun Legacy wickidcow", false, false, false));
        assertEquals(
            CoreVariant.SLIMEFUN_LEGACY,
            RuntimeCompatibility.classifyCore("ordinary slimefun metadata", true, false, false));
    }

    @Test
    void classifiesUnitedFromMetadataOrCommandAliases() {
        assertEquals(
            CoreVariant.SLIMEFUN_UNITED,
            RuntimeCompatibility.classifyCore("https://github.com/Slimefun-United", false, false, false));
        assertEquals(
            CoreVariant.SLIMEFUN_UNITED,
            RuntimeCompatibility.classifyCore("ordinary slimefun metadata", false, true, false));
    }

    @Test
    void classifiesGuguFromItsUniqueApiMarker() {
        assertEquals(
            CoreVariant.SLIMEFUN_GUGU,
            RuntimeCompatibility.classifyCore("original Slimefun metadata", false, false, true));
    }

    @Test
    void prefersUnitedAndGuguFingerprintsOverTheLegacyFallbackMarker() {
        assertEquals(
            CoreVariant.SLIMEFUN_UNITED,
            RuntimeCompatibility.classifyCore("ordinary slimefun metadata", true, true, false));
        assertEquals(
            CoreVariant.SLIMEFUN_GUGU,
            RuntimeCompatibility.classifyCore("ordinary slimefun metadata", true, false, true));
    }

    @Test
    void failsClosedForUnrecognizedCore() {
        assertEquals(
            CoreVariant.OFFICIAL_OR_UNKNOWN,
            RuntimeCompatibility.classifyCore("unrecognized core", false, false, false));
    }
}

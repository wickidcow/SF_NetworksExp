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
}

package com.ytdd9527.networksexpansion.core.services;

import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

final class QuantumStorageNameMigration {

    private static final Map<String, String> LEGACY_NAMES = Map.ofEntries(
        Map.entry("items.NTW_QUANTUM_STORAGE_9.name", "Network Quantum Storage (9)"),
        Map.entry("items.NTW_QUANTUM_STORAGE_10.name", "Network Quantum Storage (10)"),
        Map.entry("items.NTW_QUANTUM_STORAGE_1.name", "Network Quantum Storage (1)"),
        Map.entry("items.NTW_QUANTUM_STORAGE_2.name", "Network Quantum Storage (2)"),
        Map.entry("items.NTW_QUANTUM_STORAGE_3.name", "Network Quantum Storage (3)"),
        Map.entry("items.NTW_QUANTUM_STORAGE_4.name", "Network Quantum Storage (4)"),
        Map.entry("items.NTW_QUANTUM_STORAGE_5.name", "Network Quantum Storage (5)"),
        Map.entry("items.NTW_QUANTUM_STORAGE_6.name", "Network Quantum Storage (6)"),
        Map.entry("items.NTW_QUANTUM_STORAGE_7.name", "Network Quantum Storage (7)"),
        Map.entry("items.NTW_QUANTUM_STORAGE_8.name", "Network Quantum Storage (8)"));

    private QuantumStorageNameMigration() {}

    static Set<String> paths() {
        return LEGACY_NAMES.keySet();
    }

    static @Nullable String migrate(
        String langFilename,
        String path,
        @Nullable String current,
        @Nullable String corrected) {

        if (!"en-US".equals(langFilename)) {
            return current;
        }

        String legacy = LEGACY_NAMES.get(path);
        if (legacy == null || !legacy.equals(current) || corrected == null || corrected.equals(current)) {
            return current;
        }

        return corrected;
    }
}

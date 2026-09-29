package com.ytdd9527.networksexpansion.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LocalizationServiceQuantumMigrationTest {

    @Test
    void migratesOnlyStockLegacyQuantumNames() {
        assertEquals(
            "Network Quantum Storage (1)",
            QuantumStorageNameMigration.migrate(
                "en-US",
                "items.NTW_QUANTUM_STORAGE_9.name",
                "Network Quantum Storage (9)",
                "Network Quantum Storage (1)"));

        assertEquals(
            "Network Quantum Storage (2)",
            QuantumStorageNameMigration.migrate(
                "en-US",
                "items.NTW_QUANTUM_STORAGE_10.name",
                "Network Quantum Storage (10)",
                "Network Quantum Storage (2)"));

        assertEquals(
            "Network Quantum Storage (3)",
            QuantumStorageNameMigration.migrate(
                "en-US",
                "items.NTW_QUANTUM_STORAGE_1.name",
                "Network Quantum Storage (1)",
                "Network Quantum Storage (3)"));

        assertEquals(
            "My Custom Quantum Box",
            QuantumStorageNameMigration.migrate(
                "en-US",
                "items.NTW_QUANTUM_STORAGE_2.name",
                "My Custom Quantum Box",
                "Network Quantum Storage (4)"));
    }

    @Test
    void ignoresOtherLanguages() {
        assertEquals(
            "Network Quantum Storage (9)",
            QuantumStorageNameMigration.migrate(
                "de-DE",
                "items.NTW_QUANTUM_STORAGE_9.name",
                "Network Quantum Storage (9)",
                "Network Quantum Storage (1)"));
    }
}

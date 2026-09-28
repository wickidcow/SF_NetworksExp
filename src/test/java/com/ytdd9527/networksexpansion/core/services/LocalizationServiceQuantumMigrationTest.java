package com.ytdd9527.networksexpansion.core.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class LocalizationServiceQuantumMigrationTest {

    @Test
    void migratesOnlyStockLegacyQuantumNames() {
        YamlConfiguration current = new YamlConfiguration();
        YamlConfiguration defaults = new YamlConfiguration();

        current.set("items.NTW_QUANTUM_STORAGE_9.name", "Network Quantum Storage (9)");
        current.set("items.NTW_QUANTUM_STORAGE_10.name", "Network Quantum Storage (10)");
        current.set("items.NTW_QUANTUM_STORAGE_1.name", "Network Quantum Storage (1)");
        current.set("items.NTW_QUANTUM_STORAGE_2.name", "My Custom Quantum Box");

        defaults.set("items.NTW_QUANTUM_STORAGE_9.name", "Network Quantum Storage (1)");
        defaults.set("items.NTW_QUANTUM_STORAGE_10.name", "Network Quantum Storage (2)");
        defaults.set("items.NTW_QUANTUM_STORAGE_1.name", "Network Quantum Storage (3)");
        defaults.set("items.NTW_QUANTUM_STORAGE_2.name", "Network Quantum Storage (4)");

        assertTrue(LocalizationService.migrateLegacyQuantumStorageNames("en-US", current, defaults));

        assertEquals("Network Quantum Storage (1)", current.getString("items.NTW_QUANTUM_STORAGE_9.name"));
        assertEquals("Network Quantum Storage (2)", current.getString("items.NTW_QUANTUM_STORAGE_10.name"));
        assertEquals("Network Quantum Storage (3)", current.getString("items.NTW_QUANTUM_STORAGE_1.name"));
        assertEquals("My Custom Quantum Box", current.getString("items.NTW_QUANTUM_STORAGE_2.name"));
    }

    @Test
    void ignoresOtherLanguages() {
        YamlConfiguration current = new YamlConfiguration();
        YamlConfiguration defaults = new YamlConfiguration();

        current.set("items.NTW_QUANTUM_STORAGE_9.name", "Network Quantum Storage (9)");
        defaults.set("items.NTW_QUANTUM_STORAGE_9.name", "Network Quantum Storage (1)");

        assertFalse(LocalizationService.migrateLegacyQuantumStorageNames("de-DE", current, defaults));
        assertEquals("Network Quantum Storage (9)", current.getString("items.NTW_QUANTUM_STORAGE_9.name"));
    }
}

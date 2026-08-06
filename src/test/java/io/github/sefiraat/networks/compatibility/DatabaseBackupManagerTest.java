package io.github.sefiraat.networks.compatibility;

import com.ytdd9527.networksexpansion.utils.databases.DatabaseBackupManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseBackupManagerTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void copiesDatabaseAndSidecarsWithoutChangingLivePaths() throws Exception {
        Files.writeString(temporaryDirectory.resolve("CargoStorageUnits.db"), "db");
        Files.writeString(temporaryDirectory.resolve("CargoStorageUnits.db-wal"), "wal");
        Files.writeString(temporaryDirectory.resolve("CargoStorageUnits.db-shm"), "shm");

        Path backup = DatabaseBackupManager.createStartupBackup(
            temporaryDirectory, 3, Logger.getLogger("NetworksBackupTest"));

        assertNotNull(backup);
        assertTrue(Files.isRegularFile(backup.resolve("CargoStorageUnits.db")));
        assertEquals("wal", Files.readString(backup.resolve("CargoStorageUnits.db-wal")));
        assertEquals("shm", Files.readString(backup.resolve("CargoStorageUnits.db-shm")));
        assertTrue(Files.isRegularFile(temporaryDirectory.resolve("CargoStorageUnits.db")));
    }
}

package com.ytdd9527.networksexpansion.utils.databases;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

/** Creates bounded, pre-open backups of the drawer database and SQLite sidecar files. */
public final class DatabaseBackupManager {

    private static final DateTimeFormatter DIRECTORY_FORMAT =
        DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC);

    private DatabaseBackupManager() {
    }

    public static @Nullable Path createStartupBackup(
        @NotNull Path dataFolder,
        int retained,
        @NotNull Logger logger
    ) throws IOException {
        Path database = dataFolder.resolve("CargoStorageUnits.db");
        if (!Files.isRegularFile(database)) {
            return null;
        }

        Path backupRoot = dataFolder.resolve("database-backups");
        Files.createDirectories(backupRoot);
        Path backup = uniqueBackupDirectory(backupRoot);
        Files.createDirectories(backup);

        copyIfPresent(database, backup.resolve(database.getFileName()));
        copyIfPresent(database.resolveSibling(database.getFileName() + "-wal"),
            backup.resolve(database.getFileName() + "-wal"));
        copyIfPresent(database.resolveSibling(database.getFileName() + "-shm"),
            backup.resolve(database.getFileName() + "-shm"));

        prune(backupRoot, Math.max(1, retained), logger);
        return backup;
    }

    private static @NotNull Path uniqueBackupDirectory(@NotNull Path root) {
        String base = DIRECTORY_FORMAT.format(Instant.now());
        Path candidate = root.resolve(base);
        int suffix = 1;
        while (Files.exists(candidate)) {
            candidate = root.resolve(base + '-' + suffix++);
        }
        return candidate;
    }

    private static void copyIfPresent(@NotNull Path source, @NotNull Path target) throws IOException {
        if (Files.isRegularFile(source)) {
            Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void prune(@NotNull Path root, int retained, @NotNull Logger logger) throws IOException {
        List<Path> directories;
        try (Stream<Path> stream = Files.list(root)) {
            directories = stream.filter(Files::isDirectory)
                .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                .toList();
        }

        for (int index = retained; index < directories.size(); index++) {
            Path obsolete = directories.get(index);
            try (Stream<Path> walk = Files.walk(obsolete)) {
                for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            } catch (IOException exception) {
                logger.log(Level.WARNING, "Could not remove old Networks database backup " + obsolete, exception);
            }
        }
    }
}

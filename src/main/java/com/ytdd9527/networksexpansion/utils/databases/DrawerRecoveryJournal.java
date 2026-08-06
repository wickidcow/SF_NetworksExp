package com.ytdd9527.networksexpansion.utils.databases;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Small, idempotent write-ahead journal for delayed drawer amount updates.
 *
 * <p>Each row stores the latest absolute amount for one container/item pair. Replaying a row is therefore
 * safe even when SQLite committed the update immediately before a process crash but the journal cleanup did
 * not run yet.</p>
 */
public final class DrawerRecoveryJournal {

    public static final String FILE_NAME = "CargoStorageUnits.recovery.tsv";
    private static final String HEADER = "# Networks drawer amount recovery journal v1";

    private DrawerRecoveryJournal() {
    }

    public static @NotNull Path pathFor(@NotNull Path dataFolder) {
        return dataFolder.resolve(FILE_NAME);
    }

    public static synchronized @NotNull Map<Key, Integer> read(@NotNull Path journal) throws IOException {
        if (!Files.isRegularFile(journal)) {
            return Collections.emptyMap();
        }

        Map<Key, Integer> entries = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(journal, StandardCharsets.UTF_8)) {
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }

                String[] parts = trimmed.split("\\t", -1);
                if (parts.length != 3) {
                    throw new IOException("Invalid Networks recovery journal row at line " + lineNumber);
                }
                try {
                    int containerId = Integer.parseInt(parts[0]);
                    int itemId = Integer.parseInt(parts[1]);
                    int amount = Integer.parseInt(parts[2]);
                    if (containerId < 0 || itemId < 0) {
                        throw new NumberFormatException("negative id");
                    }
                    entries.put(new Key(containerId, itemId), amount);
                } catch (NumberFormatException exception) {
                    throw new IOException("Invalid Networks recovery journal value at line " + lineNumber, exception);
                }
            }
        }
        return Collections.unmodifiableMap(entries);
    }

    public static synchronized void merge(
        @NotNull Path journal,
        @NotNull Map<Integer, ? extends Map<Integer, Integer>> changes
    ) throws IOException {
        Map<Key, Integer> merged = new LinkedHashMap<>(read(journal));
        flattenInto(merged, changes);
        write(journal, merged);
    }

    public static synchronized void writeSnapshot(
        @NotNull Path journal,
        @NotNull Map<Integer, ? extends Map<Integer, Integer>> changes
    ) throws IOException {
        Map<Key, Integer> flattened = new LinkedHashMap<>();
        flattenInto(flattened, changes);
        write(journal, flattened);
    }

    /** Removes only rows whose value still matches the successfully committed snapshot. */
    public static synchronized void removeCommitted(
        @NotNull Path journal,
        @NotNull Map<Integer, ? extends Map<Integer, Integer>> committed
    ) throws IOException {
        Map<Key, Integer> remaining = new LinkedHashMap<>(read(journal));
        for (Map.Entry<Integer, ? extends Map<Integer, Integer>> container : committed.entrySet()) {
            for (Map.Entry<Integer, Integer> item : container.getValue().entrySet()) {
                Key key = new Key(container.getKey(), item.getKey());
                remaining.remove(key, item.getValue());
            }
        }
        write(journal, remaining);
    }

    public static synchronized int count(@NotNull Path journal) throws IOException {
        return read(journal).size();
    }

    public static @NotNull Map<Integer, Map<Integer, Integer>> expand(@NotNull Map<Key, Integer> flat) {
        Map<Integer, Map<Integer, Integer>> expanded = new LinkedHashMap<>();
        for (Map.Entry<Key, Integer> entry : flat.entrySet()) {
            expanded.computeIfAbsent(entry.getKey().containerId(), ignored -> new LinkedHashMap<>())
                .put(entry.getKey().itemId(), entry.getValue());
        }
        return expanded;
    }

    private static void flattenInto(
        @NotNull Map<Key, Integer> target,
        @NotNull Map<Integer, ? extends Map<Integer, Integer>> changes
    ) {
        for (Map.Entry<Integer, ? extends Map<Integer, Integer>> container : changes.entrySet()) {
            if (container.getKey() == null || container.getKey() < 0 || container.getValue() == null) {
                continue;
            }
            for (Map.Entry<Integer, Integer> item : container.getValue().entrySet()) {
                if (item.getKey() != null && item.getKey() >= 0 && item.getValue() != null) {
                    target.put(new Key(container.getKey(), item.getKey()), item.getValue());
                }
            }
        }
    }

    private static void write(@NotNull Path journal, @NotNull Map<Key, Integer> entries) throws IOException {
        Path parent = journal.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("Networks recovery journal has no parent directory");
        }
        Files.createDirectories(parent);

        if (entries.isEmpty()) {
            Files.deleteIfExists(journal);
            Files.deleteIfExists(temporaryPath(journal));
            return;
        }

        Path temporary = temporaryPath(journal);
        TreeMap<Key, Integer> sorted = new TreeMap<>(entries);
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (Map.Entry<Key, Integer> entry : sorted.entrySet()) {
                writer.write(Integer.toString(entry.getKey().containerId()));
                writer.write('\t');
                writer.write(Integer.toString(entry.getKey().itemId()));
                writer.write('\t');
                writer.write(Integer.toString(entry.getValue()));
                writer.newLine();
            }
        }

        // Force the temporary file before the rename so a successful journal write survives an abrupt process exit.
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }

        try {
            Files.move(temporary, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, journal, StandardCopyOption.REPLACE_EXISTING);
        }

        // Directory fsync is supported on Unix-like systems and makes the rename durable. It is best-effort on
        // platforms that do not permit opening directory channels.
        try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ)) {
            directory.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // The file itself was already forced; keep cross-platform behavior when directory fsync is unavailable.
        }
    }

    private static @NotNull Path temporaryPath(@NotNull Path journal) {
        return journal.resolveSibling(journal.getFileName() + ".tmp");
    }

    public record Key(int containerId, int itemId) implements Comparable<Key> {
        @Override
        public int compareTo(@NotNull Key other) {
            int containerComparison = Integer.compare(containerId, other.containerId);
            return containerComparison != 0 ? containerComparison : Integer.compare(itemId, other.itemId);
        }
    }
}

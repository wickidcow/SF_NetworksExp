package com.ytdd9527.networksexpansion.utils.databases;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * Durable ownership of the original serialized bytes for newly registered drawer item types.
 * This is separate from the historical amount journal and never infers an item identity.
 */
public final class DrawerTemplateJournal {

    public static final String FILE_NAME = "CargoStorageUnits.templates.tsv";
    private static final String HEADER = "# Networks drawer item-template recovery journal v1";

    private DrawerTemplateJournal() {
    }

    public static Path pathFor(Path dataFolder) {
        return dataFolder.resolve(FILE_NAME);
    }

    public static synchronized Map<Integer, String> read(Path journal) throws IOException {
        if (Files.notExists(journal)) {
            return Collections.emptyMap();
        }
        if (!Files.isRegularFile(journal)) {
            throw new IOException("Networks item-template journal is not a regular file: " + journal);
        }
        Map<Integer, String> entries = new LinkedHashMap<>();
        try (BufferedReader reader = Files.newBufferedReader(journal, StandardCharsets.UTF_8)) {
            if (!HEADER.equals(reader.readLine())) {
                throw new IOException("Invalid Networks item-template journal header");
            }
            String line;
            int lineNumber = 1;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                String[] parts = line.split("\\t", -1);
                if (parts.length != 3) {
                    throw new IOException("Invalid Networks item-template journal row at line " + lineNumber);
                }
                try {
                    int id = Integer.parseInt(parts[0]);
                    validate(id, parts[2]);
                    if (!digest(id, parts[2]).equals(parts[1])) {
                        throw new IOException("Networks item-template journal checksum mismatch at line " + lineNumber);
                    }
                    String previous = entries.putIfAbsent(id, parts[2]);
                    if (previous != null && !previous.equals(parts[2])) {
                        throw new IOException("Conflicting Networks item-template journal identity " + id);
                    }
                } catch (IllegalArgumentException exception) {
                    throw new IOException("Invalid Networks item-template journal value at line " + lineNumber, exception);
                }
            }
        }
        return Collections.unmodifiableMap(entries);
    }

    public static synchronized void record(Path journal, int id, String serialized) throws IOException {
        validate(id, serialized);
        Map<Integer, String> entries = new LinkedHashMap<>(read(journal));
        String previous = entries.putIfAbsent(id, serialized);
        if (previous != null) {
            if (!previous.equals(serialized)) {
                throw new IOException("Refused to replace Networks item-template identity " + id);
            }
            return;
        }
        write(journal, entries);
    }

    /** Remove only exact identities after their SQLite transaction has durably committed. */
    public static synchronized void removeCommitted(Path journal, Map<Integer, String> committed) throws IOException {
        Map<Integer, String> entries = new LinkedHashMap<>(read(journal));
        for (Map.Entry<Integer, String> entry : committed.entrySet()) {
            entries.remove(entry.getKey(), entry.getValue());
        }
        write(journal, entries);
    }

    private static void validate(int id, String serialized) {
        if (id < 0 || serialized == null || serialized.isEmpty()) {
            throw new IllegalArgumentException("Invalid Networks item-template identity");
        }
        Base64.getDecoder().decode(serialized);
    }

    private static String digest(int id, String serialized) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Include the ID: a damaged ID must not relabel otherwise intact item bytes.
            return HexFormat.of().formatHex(digest.digest((id + "\t" + serialized).getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("The Java runtime lacks SHA-256", exception);
        }
    }

    private static void write(Path journal, Map<Integer, String> entries) throws IOException {
        Path parent = journal.toAbsolutePath().getParent();
        if (parent == null) {
            throw new IOException("Networks item-template journal has no parent directory");
        }
        Files.createDirectories(parent);
        Path temporary = journal.resolveSibling(journal.getFileName() + ".tmp");
        if (entries.isEmpty()) {
            Files.deleteIfExists(journal);
            Files.deleteIfExists(temporary);
            forceDirectory(parent);
            return;
        }
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            writer.newLine();
            for (Map.Entry<Integer, String> entry : new TreeMap<>(entries).entrySet()) {
                writer.write(Integer.toString(entry.getKey()));
                writer.write('\t');
                writer.write(digest(entry.getKey(), entry.getValue()));
                writer.write('\t');
                writer.write(entry.getValue());
                writer.newLine();
            }
        }
        try (FileChannel channel = FileChannel.open(temporary, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
        try {
            Files.move(temporary, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, journal, StandardCopyOption.REPLACE_EXISTING);
        }
        forceDirectory(parent);
    }

    private static void forceDirectory(Path parent) {
        try (FileChannel directory = FileChannel.open(parent, StandardOpenOption.READ)) {
            directory.force(true);
        } catch (IOException | UnsupportedOperationException ignored) {
            // Match the existing recovery journal on platforms without directory fsync.
        }
    }
}

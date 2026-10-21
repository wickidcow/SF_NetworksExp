package com.ytdd9527.networksexpansion.utils.databases;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class DrawerTemplateJournalTest {
    @TempDir
    Path directory;

    @Test
    void exactPayloadSurvivesReopenAndOnlyMatchingCommitCanRemoveIt() throws Exception {
        Path journal = DrawerTemplateJournal.pathFor(directory);
        String first = payload("original typed metadata");
        String second = payload("other identity");
        DrawerTemplateJournal.record(journal, 1, first);
        DrawerTemplateJournal.record(journal, 2, second);
        assertEquals(Map.of(1, first, 2, second), DrawerTemplateJournal.read(journal));
        DrawerTemplateJournal.removeCommitted(journal, Map.of(1, second));
        assertEquals(Map.of(1, first, 2, second), DrawerTemplateJournal.read(journal));
        DrawerTemplateJournal.removeCommitted(journal, Map.of(1, first));
        assertEquals(Map.of(2, second), DrawerTemplateJournal.read(journal));
        DrawerTemplateJournal.removeCommitted(journal, Map.of(2, second));
        assertFalse(Files.exists(journal));
    }

    @Test
    void conflictingRegistrationLeavesOriginalBytesUntouched() throws Exception {
        Path journal = DrawerTemplateJournal.pathFor(directory);
        DrawerTemplateJournal.record(journal, 7, payload("original"));
        byte[] before = Files.readAllBytes(journal);
        assertThrows(IOException.class, () -> DrawerTemplateJournal.record(journal, 7, payload("replacement")));
        assertArrayEquals(before, Files.readAllBytes(journal));
    }

    @Test
    void checksumBindsTheIdAsWellAsThePayload() throws Exception {
        Path journal = DrawerTemplateJournal.pathFor(directory);
        DrawerTemplateJournal.record(journal, 7, payload("original"));
        String original = Files.readString(journal);
        Files.writeString(journal, original.replace("\n7\t", "\n8\t"));
        byte[] damaged = Files.readAllBytes(journal);
        assertThrows(IOException.class, () -> DrawerTemplateJournal.read(journal));
        assertThrows(IOException.class, () -> DrawerTemplateJournal.record(journal, 9, payload("new")));
        assertArrayEquals(damaged, Files.readAllBytes(journal));
    }

    @Test
    void truncatedPayloadIsRefusedWithoutReplacingTheEvidence() throws Exception {
        Path journal = DrawerTemplateJournal.pathFor(directory);
        DrawerTemplateJournal.record(journal, 3, payload("full payload"));
        String original = Files.readString(journal);
        Files.writeString(journal, original.substring(0, original.length() - 5));
        byte[] truncated = Files.readAllBytes(journal);
        assertThrows(IOException.class, () -> DrawerTemplateJournal.read(journal));
        assertArrayEquals(truncated, Files.readAllBytes(journal));
    }

    @Test
    void missingJournalIsEmptyButAnExistingDirectoryIsAnError() throws Exception {
        Path journal = DrawerTemplateJournal.pathFor(directory);
        assertEquals(Map.of(), DrawerTemplateJournal.read(journal));
        Files.createDirectory(journal);
        assertThrows(IOException.class, () -> DrawerTemplateJournal.read(journal));
        assertThrows(IOException.class, () -> DrawerTemplateJournal.record(journal, 1, payload("new")));
        assertTrue(Files.isDirectory(journal));
    }

    @Test
    void unacceptedTemporaryFileCannotReplaceTheLastCompleteJournal() throws Exception {
        Path journal = DrawerTemplateJournal.pathFor(directory);
        DrawerTemplateJournal.record(journal, 1, payload("accepted"));
        Files.writeString(journal.resolveSibling(journal.getFileName() + ".tmp"), "interrupted write");
        assertEquals(Map.of(1, payload("accepted")), DrawerTemplateJournal.read(journal));
        DrawerTemplateJournal.record(journal, 2, payload("next"));
        assertEquals(Map.of(1, payload("accepted"), 2, payload("next")), DrawerTemplateJournal.read(journal));
    }

    @Test
    void concurrentWritersAndCleanupPreserveEveryOutstandingIdentity() throws Exception {
        Path journal = DrawerTemplateJournal.pathFor(directory);
        var executor = Executors.newFixedThreadPool(4);
        try {
            var futures = new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for (int value = 1; value <= 40; value++) {
                final int id = value;
                futures.add(executor.submit(() -> {
                    try {
                        DrawerTemplateJournal.record(journal, id, payload("item-" + id));
                        if ((id & 1) == 0) {
                            DrawerTemplateJournal.removeCommitted(journal, Map.of(id, payload("item-" + id)));
                        }
                    } catch (IOException exception) {
                        throw new IllegalStateException(exception);
                    }
                }));
            }
            for (var future : futures) future.get(10, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        Map<Integer, String> pending = DrawerTemplateJournal.read(journal);
        assertEquals(20, pending.size());
        for (int id = 1; id <= 40; id++) {
            assertEquals((id & 1) == 1 ? payload("item-" + id) : null, pending.get(id));
        }
    }

    private static String payload(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }
}

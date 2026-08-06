package io.github.sefiraat.networks.compatibility;

import com.ytdd9527.networksexpansion.utils.databases.DrawerRecoveryJournal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DrawerRecoveryJournalTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void mergesLatestAbsoluteAmountAndReplaysIdempotently() throws Exception {
        Path journal = DrawerRecoveryJournal.pathFor(temporaryDirectory);
        DrawerRecoveryJournal.merge(journal, Map.of(7, Map.of(3, 40, 4, 2)));
        DrawerRecoveryJournal.merge(journal, Map.of(7, Map.of(3, 41)));

        Map<DrawerRecoveryJournal.Key, Integer> values = DrawerRecoveryJournal.read(journal);
        assertEquals(2, values.size());
        assertEquals(41, values.get(new DrawerRecoveryJournal.Key(7, 3)).intValue());
        assertEquals(2, values.get(new DrawerRecoveryJournal.Key(7, 4)).intValue());
    }

    @Test
    void committedCleanupDoesNotDeleteNewerJournalValue() throws Exception {
        Path journal = DrawerRecoveryJournal.pathFor(temporaryDirectory);
        DrawerRecoveryJournal.merge(journal, Map.of(1, Map.of(2, 10)));
        DrawerRecoveryJournal.merge(journal, Map.of(1, Map.of(2, 11)));

        DrawerRecoveryJournal.removeCommitted(journal, Map.of(1, Map.of(2, 10)));
        assertEquals(11, DrawerRecoveryJournal.read(journal)
            .get(new DrawerRecoveryJournal.Key(1, 2)).intValue());

        DrawerRecoveryJournal.removeCommitted(journal, Map.of(1, Map.of(2, 11)));
        assertFalse(java.nio.file.Files.exists(journal));
    }

    @Test
    void expandsFlatRowsForTransactionalReplay() throws Exception {
        Path journal = DrawerRecoveryJournal.pathFor(temporaryDirectory);
        DrawerRecoveryJournal.writeSnapshot(journal, Map.of(2, Map.of(8, -1), 4, Map.of(9, 200)));

        Map<Integer, Map<Integer, Integer>> expanded =
            DrawerRecoveryJournal.expand(DrawerRecoveryJournal.read(journal));
        assertTrue(expanded.containsKey(2));
        assertEquals(-1, expanded.get(2).get(8).intValue());
        assertEquals(200, expanded.get(4).get(9).intValue());
    }
}

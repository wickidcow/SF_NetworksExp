package com.ytdd9527.networksexpansion.utils.databases;

import com.balugaq.netex.utils.Lang;
import io.github.sefiraat.networks.Networks;
import org.bukkit.configuration.file.FileConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * TEST FIRST: exercises the real DataStorage autosave and real QueryQueue ordering.
 *
 * The DataSource boundary uses a deliberately simple in-memory SQL-row model,
 * not actual SQLite. A mocked journal-path lookup stalls journal I/O AFTER
 * the generation swap. In the old code that happens before SQL FIFO reservation;
 * in a corrected worker-journal design it happens after FIFO reservation.
 * The same conservation assertions must pass in both scheduling environments.
 *
 * A complete correction must journal BEFORE SQL on the reserved FIFO task,
 * preserve legacy journal replay and handle shutdown/failure/zero-row intent.
 * These tests alone cannot certify any of those additional contracts.
 */
class DrawerAutosaveEnqueueOrderTest {

    @Test
    void olderAutosaveMustNotResurrectNewerDelete(@TempDir Path temp) throws Exception {
        runDelayedSnapshot(temp, false);
    }

    @Test
    void olderAutosaveMustNotOverwriteNewSameIdFive(@TempDir Path temp) throws Exception {
        runDelayedSnapshot(temp, true);
    }

    private static void runDelayedSnapshot(Path temp, boolean reinsert) throws Exception {
        DataStorage.clearRuntimeCache();

        final QueryQueue queue = new QueryQueue();
        queue.startThread();
        final DataSource source = mock(DataSource.class);
        final Networks plugin = mock(Networks.class);
        final FileConfiguration config = mock(FileConfiguration.class);
        final Logger logger = Logger.getLogger("DrawerAutosaveEnqueueOrderTest");
        final AtomicReference<Integer> physicalDatabaseAmount = new AtomicReference<>(10);
        final AtomicReference<Throwable> saverFailure = new AtomicReference<>();
        final AtomicBoolean journalPathBlockedOnce = new AtomicBoolean();
        final CountDownLatch afterSnapshotSwap = new CountDownLatch(1);
        final CountDownLatch releaseJournal = new CountDownLatch(1);
        final Path journal = DrawerRecoveryJournal.pathFor(temp);

        when(plugin.getConfig()).thenReturn(config);
        when(config.getBoolean("database.recovery-journal", true)).thenReturn(true);
        when(plugin.getLogger()).thenReturn(logger);

        when(source.getRecoveryJournalFile()).thenAnswer(call -> {
            if (journalPathBlockedOnce.compareAndSet(false, true)) {
                afterSnapshotSwap.countDown();
                if (!releaseJournal.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("Journal barrier not released");
                }
            }
            return journal;
        });

        // A mock SQL boundary still submits ALL mutations to the actual FIFO QueryQueue worker.
        doAnswer(call -> {
            queue.scheduleUpdate(() -> {
                physicalDatabaseAmount.set(null);
                return false;
            });
            return null;
        }).when(source).deleteStoredItem(eq(21), eq(9));

        doAnswer(call -> {
            final int added = call.getArgument(2);
            queue.scheduleUpdate(() -> {
                physicalDatabaseAmount.updateAndGet(old -> (old == null ? 0 : old) + added);
                return false;
            });
            return null;
        }).when(source).addStoredItem(eq(21), eq(9), anyInt());

        doAnswer(call -> {
            @SuppressWarnings("unchecked")
            final Map<Integer, ? extends Map<Integer, Integer>> saved =
                (Map<Integer, ? extends Map<Integer, Integer>>) call.getArgument(0);
            // Simulate the worker's mandatory journal-before-SQL gate using the REAL
            // original production journal implementation and its fsync/rename path.
            // In the baseline the saver has ALREADY journaled before this queued task.
            queue.scheduleUpdate(() -> {
                DrawerRecoveryJournal.merge(source.getRecoveryJournalFile(), saved);
                physicalDatabaseAmount.set(saved.get(21).get(9));
                return false;
            });
            return null;
        }).when(source).applyAmountChanges(anyMap(), any());

        Thread saver = new Thread(() -> {
            try (MockedStatic<Networks> networks = mockStatic(Networks.class);
                 MockedStatic<Lang> language = mockStatic(Lang.class)) {
                networks.when(Networks::getDataSource).thenReturn(source);
                networks.when(Networks::getInstance).thenReturn(plugin);
                language.when(() -> Lang.getString("messages.data-saving.saving-drawer"))
                    .thenReturn("Saving drawer amounts");
                DataStorage.saveAmountChange();
            } catch (Throwable failure) {
                saverFailure.set(failure);
            }
        }, "Drawer-Journal-Delayed-Test-Saver");

        try (MockedStatic<Networks> networks = mockStatic(Networks.class)) {
            networks.when(Networks::getDataSource).thenReturn(source);
            networks.when(Networks::getInstance).thenReturn(plugin);

            DataStorage.setStoredAmount(21, 9, 7);
            saver.start();
            assertTrue(afterSnapshotSwap.await(10, TimeUnit.SECONDS),
                "The autosave must reach journal I/O after taking the older snapshot");

            DataStorage.deleteStoredItem(21, 9);
            if (reinsert) {
                DataStorage.addStoredItem(21, 9, 5);
            }
            releaseJournal.countDown();
            saver.join(10000);
            assertFalse(saver.isAlive(), "The saver must exit, not deadlock");
            assertNull(saverFailure.get(), "Unexpected autosave failure");
            assertTrue(queue.awaitDrained(10000), "The actual SQL-task FIFO must drain");

            assertEquals(reinsert ? 5 : null, physicalDatabaseAmount.get(),
                "A newer delete/reinsertion must not be overwritten by an older, delayed UPSERT7");
            assertEquals(0, queue.snapshot().failed(), "No QueueWorker task should throw");
        } finally {
            releaseJournal.countDown();
            saver.join(10000);
            queue.shutdown(10000);
            DataStorage.clearRuntimeCache();
        }
    }
}

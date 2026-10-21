package com.ytdd9527.networksexpansion.utils.databases;

import io.github.sefiraat.networks.Networks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * TEST FIRST: pending absolute item amounts must not resurrect removed drawer rows.
 *
 * Only the runtime intent buffer is inspected. The DataSource boundary is mocked;
 * database ordering, journal replay, lock mode and native server behavior require
 * separate real SQLite and player tests. No production source is modified here.
 */
class DrawerPendingDeletionConservationTest {

    private MockedStatic<Networks> networks;

    @BeforeEach
    void prepare() {
        DataStorage.clearRuntimeCache();
        networks = mockStatic(Networks.class);
        networks.when(Networks::getDataSource).thenReturn(mock(DataSource.class));
    }

    @AfterEach
    void cleanup() {
        try {
            if (networks != null) {
                networks.close();
            }
        } finally {
            DataStorage.clearRuntimeCache();
        }
    }

    @SuppressWarnings("unchecked")
    private static Integer pending(int containerId, int itemId) throws Exception {
        Field field = DataStorage.class.getDeclaredField("CHANGES");
        field.setAccessible(true);
        AtomicReference<ConcurrentMap<Integer, ConcurrentMap<Integer, Integer>>> reference =
            (AtomicReference<ConcurrentMap<Integer, ConcurrentMap<Integer, Integer>>>) field.get(null);
        Map<Integer, Integer> items = reference.get().get(containerId);
        return items == null ? null : items.get(itemId);
    }

    @Test
    void partialThenFinalWithdrawalMustNotReplaySevenItems() throws Exception {
        DataStorage.setStoredAmount(21, 9, 7);
        assertEquals(7, pending(21, 9));
        DataStorage.deleteStoredItem(21, 9);
        assertEquals(-1, pending(21, 9),
            "An absolute pending UPSERT7 must be replaced by deletion intent, not resurrected later");
    }

    @Test
    void deleteWithoutEarlierAmountStillRequiresDurableIntent() throws Exception {
        assertNull(pending(21, 9));
        DataStorage.deleteStoredItem(21, 9);
        assertEquals(-1, pending(21, 9),
            "A deletion must be represented in the future journal/snapshot generation");
    }

    @Test
    void deletingThenReinsertingSameCanonicalIdMustPublishFive() throws Exception {
        DataStorage.setStoredAmount(21, 9, 7);
        DataStorage.deleteStoredItem(21, 9);
        DataStorage.addStoredItem(21, 9, 5);
        assertEquals(5, pending(21, 9),
            "New canonical stock of five must supersede the removed seven and tombstone");
    }

    @Test
    void firstInsertMustPublishItsAbsoluteStock() throws Exception {
        DataStorage.addStoredItem(21, 9, 5);
        assertEquals(5, pending(21, 9));
    }

    @Test
    void aLaterAbsoluteUpdateMustRemainAuthoritative() throws Exception {
        DataStorage.setStoredAmount(21, 9, 7);
        DataStorage.deleteStoredItem(21, 9);
        DataStorage.addStoredItem(21, 9, 5);
        DataStorage.setStoredAmount(21, 9, 11);
        assertEquals(11, pending(21, 9));
    }

    @Test
    void retainedZeroMustNotBeConflatedWithDeletionMarker() throws Exception {
        DataStorage.setStoredAmount(21, 9, 0);
        assertEquals(0, pending(21, 9));
    }
}

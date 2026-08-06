package io.github.sefiraat.networks.compatibility;

import com.ytdd9527.networksexpansion.utils.databases.QueryQueue;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryQueueTest {

    @Test
    void executesInOrderAndDrainsCleanly() {
        QueryQueue queue = new QueryQueue();
        AtomicInteger sequence = new AtomicInteger();
        queue.startThread();

        queue.scheduleUpdate(() -> sequence.compareAndSet(0, 1));
        queue.scheduleQuery(() -> sequence.compareAndSet(1, 2));

        assertTrue(queue.shutdown(5_000));
        assertEquals(2, sequence.get());
        QueryQueue.QueueSnapshot snapshot = queue.snapshot();
        assertEquals(2, snapshot.scheduled());
        assertEquals(2, snapshot.executed());
        assertEquals(0, snapshot.failed());
        assertFalse(snapshot.workerRunning());
    }

    @Test
    void rejectsWorkAfterShutdown() {
        QueryQueue queue = new QueryQueue();
        queue.startThread();
        assertTrue(queue.shutdown(5_000));

        assertThrows(IllegalStateException.class, () -> queue.scheduleUpdate(() -> false));
        assertEquals(1, queue.snapshot().rejected());
    }
}

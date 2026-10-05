package io.github.sefiraat.networks.compatibility;

import com.ytdd9527.networksexpansion.utils.databases.QueryQueue;
import com.ytdd9527.networksexpansion.utils.databases.QueuedTask;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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

    @Test
    void dequeuedTaskRemainsOutstandingUntilItsExecutionCompletes() throws Exception {
        QueryQueue queue = new QueryQueue();
        PausingQueue tasks = new PausingQueue();
        installQueue(queue, tasks);
        AtomicInteger executions = new AtomicInteger();
        queue.scheduleUpdate(() -> {
            executions.incrementAndGet();
            return false;
        });
        queue.startThread();
        try {
            assertTrue(tasks.dequeued.await(5, TimeUnit.SECONDS));
            assertEquals(0, tasks.size(), "Worker has taken the task out of the blocking queue");
            assertEquals(0, queue.getInFlightTaskAmount(), "Worker has not returned from take yet");
            assertEquals(1, queue.getTaskAmount());
            assertFalse(queue.isAllDone());
            assertFalse(queue.awaitDrained(0));
        } finally {
            tasks.release.countDown();
            assertTrue(queue.shutdown(5_000));
        }
        assertEquals(1, executions.get());
        assertEquals(0, queue.getTaskAmount());
    }

    @Test
    void largeBacklogDiagnosticsDoNotWalkTheQueue() throws Exception {
        QueryQueue queue = new QueryQueue();
        CountingQueue tasks = new CountingQueue();
        installQueue(queue, tasks);
        for (int i = 0; i < 10_000; i++) {
            queue.scheduleUpdate(() -> false);
        }
        try {
            for (int i = 0; i < 1_000; i++) {
                assertEquals(10_000, queue.getTaskAmount());
                assertEquals(10_000, queue.snapshot().queued());
                assertFalse(queue.isAllDone());
            }
            assertEquals(0, tasks.containsCalls.get(), "Diagnostics must not scan queued tasks for a stop marker");
        } finally {
            assertFalse(queue.shutdown(0));
        }
        assertEquals(10_000, queue.snapshot().cancelled());
        assertEquals(0, queue.getTaskAmount());
        assertTrue(queue.isAllDone(), "The internal stop marker is not database work");
    }

    @Test
    void cancellationCountsOnlyRemovedTasksWhileAnExecutionIsStillOwnedByTheWorker() throws Exception {
        QueryQueue queue = new QueryQueue();
        CountDownLatch executing = new CountDownLatch(1);
        queue.scheduleUpdate(() -> {
            executing.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            }
            return false;
        });
        for (int i = 0; i < 100; i++) {
            queue.scheduleUpdate(() -> false);
        }
        queue.startThread();
        try {
            assertTrue(executing.await(5, TimeUnit.SECONDS));
            assertEquals(101, queue.getTaskAmount());
            assertEquals(1, queue.getInFlightTaskAmount());
        } finally {
            assertFalse(queue.shutdown(0));
        }
        var snapshot = queue.snapshot();
        assertFalse(snapshot.workerRunning());
        assertEquals(101, snapshot.scheduled());
        assertEquals(100, snapshot.cancelled());
        assertEquals(1, snapshot.executed());
        assertEquals(0, queue.getTaskAmount());
        assertEquals(0, snapshot.queued());
        assertEquals(0, snapshot.inFlight());
    }

    @Test
    void taskStaysOutstandingUntilItsCallbackFinishes() throws Exception {
        QueryQueue queue = new QueryQueue();
        CountDownLatch callback = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        queue.scheduleUpdate(new QueuedTask() {
            @Override
            public boolean execute() {
                return true;
            }

            @Override
            public boolean callback() {
                callback.countDown();
                try {
                    return !release.await(5, TimeUnit.SECONDS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return true;
                }
            }
        });
        queue.startThread();
        try {
            assertTrue(callback.await(5, TimeUnit.SECONDS));
            assertEquals(1, queue.getTaskAmount());
            assertFalse(queue.awaitDrained(0));
        } finally {
            release.countDown();
            assertTrue(queue.shutdown(5_000));
        }
        assertEquals(0, queue.getTaskAmount());
        assertEquals(1, queue.snapshot().executed());
    }

    @Test
    void concurrentProducersRetainEveryAcceptedTaskAndFinishWithBalancedCounts() throws Exception {
        QueryQueue queue = new QueryQueue();
        AtomicInteger executions = new AtomicInteger();
        List<Thread> producers = new ArrayList<>();
        queue.startThread();
        try {
            for (int p = 0; p < 4; p++) {
                Thread producer = new Thread(() -> {
                    for (int i = 0; i < 2_000; i++) {
                        queue.scheduleUpdate(() -> {
                            executions.incrementAndGet();
                            return false;
                        });
                    }
                });
                producers.add(producer);
                producer.start();
            }
            for (Thread producer : producers) {
                producer.join(5_000);
                assertFalse(producer.isAlive());
            }
            assertTrue(queue.awaitDrained(5_000));
        } finally {
            assertTrue(queue.shutdown(5_000));
        }
        assertEquals(8_000, executions.get());
        assertEquals(8_000, queue.snapshot().scheduled());
        assertEquals(8_000, queue.snapshot().executed());
        assertEquals(0, queue.snapshot().cancelled());
        assertEquals(0, queue.getTaskAmount());
    }

    @Test
    void invalidSubmissionDoesNotLeavePhantomOutstandingWork() {
        QueryQueue queue = new QueryQueue();
        assertThrows(NullPointerException.class, () -> queue.scheduleUpdate(null));
        assertEquals(0, queue.getTaskAmount());
        assertEquals(0, queue.snapshot().scheduled());
        assertTrue(queue.shutdown(0));
        assertThrows(IllegalStateException.class, () -> queue.scheduleUpdate(() -> false));
        assertEquals(0, queue.getTaskAmount());
    }

    private static void installQueue(QueryQueue queue, LinkedBlockingQueue<QueuedTask> tasks) throws Exception {
        Field field = QueryQueue.class.getDeclaredField("tasks");
        field.setAccessible(true);
        field.set(queue, tasks);
    }

    private static final class PausingQueue extends LinkedBlockingQueue<QueuedTask> {
        private final CountDownLatch dequeued = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public QueuedTask take() throws InterruptedException {
            QueuedTask task = super.take();
            dequeued.countDown();
            release.await();
            return task;
        }
    }

    private static final class CountingQueue extends LinkedBlockingQueue<QueuedTask> {
        private final AtomicInteger containsCalls = new AtomicInteger();

        @Override
        public boolean contains(Object value) {
            containsCalls.incrementAndGet();
            return super.contains(value);
        }
    }
}

package io.github.sefiraat.networks.utils;

import java.util.concurrent.atomic.LongAdder;

/**
 * Lock-free runtime counters for item-transfer safety diagnostics.
 *
 * <p>No item identity or player data is retained. Counters are reset when Networks disables.</p>
 */
public final class TransferAudit {

    private static final LongAdder WITHDRAWAL_ATTEMPTS = new LongAdder();
    private static final LongAdder WITHDRAWAL_REQUESTED = new LongAdder();
    private static final LongAdder WITHDRAWN = new LongAdder();
    private static final LongAdder WITHDRAWAL_COMMITTED = new LongAdder();
    private static final LongAdder DEPOSIT_ATTEMPTS = new LongAdder();
    private static final LongAdder DEPOSIT_OFFERED = new LongAdder();
    private static final LongAdder DEPOSIT_COMMITTED = new LongAdder();
    private static final LongAdder ROLLBACK_REQUESTED = new LongAdder();
    private static final LongAdder ROLLBACK_REINSERTED = new LongAdder();
    private static final LongAdder DEPOSIT_COMPENSATION_REQUESTED = new LongAdder();
    private static final LongAdder DEPOSIT_COMPENSATION_RETRIEVED = new LongAdder();
    private static final LongAdder SAFETY_DROPPED = new LongAdder();
    private static final LongAdder FAILURES = new LongAdder();

    private TransferAudit() {
    }

    public static void recordWithdrawalAttempt(long requested) {
        WITHDRAWAL_ATTEMPTS.increment();
        WITHDRAWAL_REQUESTED.add(nonNegative(requested));
    }

    public static void recordWithdrawn(long amount) {
        WITHDRAWN.add(nonNegative(amount));
    }

    public static void recordWithdrawalCommitted(long amount) {
        WITHDRAWAL_COMMITTED.add(nonNegative(amount));
    }

    public static void recordDepositAttempt(long offered) {
        DEPOSIT_ATTEMPTS.increment();
        DEPOSIT_OFFERED.add(nonNegative(offered));
    }

    public static void recordDepositCommitted(long amount) {
        DEPOSIT_COMMITTED.add(nonNegative(amount));
    }

    public static void recordRollback(long requested, long reinserted) {
        ROLLBACK_REQUESTED.add(nonNegative(requested));
        ROLLBACK_REINSERTED.add(nonNegative(reinserted));
    }

    public static void recordDepositCompensation(long requested, long retrieved) {
        DEPOSIT_COMPENSATION_REQUESTED.add(nonNegative(requested));
        DEPOSIT_COMPENSATION_RETRIEVED.add(nonNegative(retrieved));
    }

    public static void recordSafetyDrop(long amount) {
        SAFETY_DROPPED.add(nonNegative(amount));
    }

    public static void recordFailure() {
        FAILURES.increment();
    }

    public static Snapshot snapshot() {
        return new Snapshot(
            WITHDRAWAL_ATTEMPTS.sum(),
            WITHDRAWAL_REQUESTED.sum(),
            WITHDRAWN.sum(),
            WITHDRAWAL_COMMITTED.sum(),
            DEPOSIT_ATTEMPTS.sum(),
            DEPOSIT_OFFERED.sum(),
            DEPOSIT_COMMITTED.sum(),
            ROLLBACK_REQUESTED.sum(),
            ROLLBACK_REINSERTED.sum(),
            DEPOSIT_COMPENSATION_REQUESTED.sum(),
            DEPOSIT_COMPENSATION_RETRIEVED.sum(),
            SAFETY_DROPPED.sum(),
            FAILURES.sum());
    }

    public static void reset() {
        WITHDRAWAL_ATTEMPTS.reset();
        WITHDRAWAL_REQUESTED.reset();
        WITHDRAWN.reset();
        WITHDRAWAL_COMMITTED.reset();
        DEPOSIT_ATTEMPTS.reset();
        DEPOSIT_OFFERED.reset();
        DEPOSIT_COMMITTED.reset();
        ROLLBACK_REQUESTED.reset();
        ROLLBACK_REINSERTED.reset();
        DEPOSIT_COMPENSATION_REQUESTED.reset();
        DEPOSIT_COMPENSATION_RETRIEVED.reset();
        SAFETY_DROPPED.reset();
        FAILURES.reset();
    }

    private static long nonNegative(long value) {
        return Math.max(0L, value);
    }

    public record Snapshot(
        long withdrawalAttempts,
        long withdrawalRequested,
        long withdrawn,
        long withdrawalCommitted,
        long depositAttempts,
        long depositOffered,
        long depositCommitted,
        long rollbackRequested,
        long rollbackReinserted,
        long depositCompensationRequested,
        long depositCompensationRetrieved,
        long safetyDropped,
        long failures
    ) {
        public long outstandingRollbackItems() {
            return Math.max(0L, rollbackRequested - rollbackReinserted - safetyDropped);
        }

        public long uncompensatedDepositItems() {
            return Math.max(0L, depositCompensationRequested - depositCompensationRetrieved);
        }
    }
}

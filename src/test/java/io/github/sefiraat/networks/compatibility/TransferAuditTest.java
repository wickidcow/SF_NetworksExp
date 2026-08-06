package io.github.sefiraat.networks.compatibility;

import io.github.sefiraat.networks.utils.TransferAudit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransferAuditTest {

    @AfterEach
    void reset() {
        TransferAudit.reset();
    }

    @Test
    void reportsOutstandingRollbackAndDepositCompensation() {
        TransferAudit.recordWithdrawalAttempt(20);
        TransferAudit.recordWithdrawn(18);
        TransferAudit.recordWithdrawalCommitted(15);
        TransferAudit.recordRollback(3, 2);
        TransferAudit.recordDepositAttempt(12);
        TransferAudit.recordDepositCommitted(10);
        TransferAudit.recordDepositCompensation(4, 3);
        TransferAudit.recordFailure();

        TransferAudit.Snapshot snapshot = TransferAudit.snapshot();
        assertEquals(1, snapshot.outstandingRollbackItems());
        assertEquals(1, snapshot.uncompensatedDepositItems());
        assertEquals(15, snapshot.withdrawalCommitted());
        assertEquals(10, snapshot.depositCommitted());
        assertEquals(1, snapshot.failures());
    }

    @Test
    void safetyDropsCloseRollbackDeficit() {
        TransferAudit.recordRollback(5, 2);
        TransferAudit.recordSafetyDrop(3);

        assertEquals(0, TransferAudit.snapshot().outstandingRollbackItems());
    }
}

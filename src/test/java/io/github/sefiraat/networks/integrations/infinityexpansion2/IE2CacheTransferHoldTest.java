package io.github.sefiraat.networks.integrations.infinityexpansion2;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IE2CacheTransferHoldTest {
    public static final class LegacyCache { public int getAmount() { return 1; } }
    public static final class PartialCache {
        public boolean hasBlockedTransfer() { return false; }
    }
    public static final class ModernCache {
        boolean transactionHeld;
        boolean unassigned;
        int reads;
        public boolean hasBlockedTransfer() { reads++; return transactionHeld; }
        public boolean hasUnassignedItems() { reads++; return unassigned; }
    }
    public static final class BrokenCache {
        public boolean hasBlockedTransfer() { throw new IllegalStateException("injected"); }
        public boolean hasUnassignedItems() { return false; }
    }
    public static final class WrongTypeCache {
        public String hasBlockedTransfer() { return "false"; }
        public boolean hasUnassignedItems() { return false; }
    }
    public static final class NullableCache {
        Boolean value;
        public Boolean hasBlockedTransfer() { return value; }
        public boolean hasUnassignedItems() { return false; }
    }

    @Test
    void oldCacheWithoutReceiptGuardsKeepsLegacyCompatibility() throws Exception {
        assertFalse(IE2CacheTransferHold.isBlocked(new LegacyCache()));
    }

    @Test
    void modernCacheChecksEveryLiveCall() throws Exception {
        var cache = new ModernCache();
        assertFalse(IE2CacheTransferHold.isBlocked(cache));
        cache.transactionHeld = true;
        assertTrue(IE2CacheTransferHold.isBlocked(cache));
        cache.transactionHeld = false;
        assertFalse(IE2CacheTransferHold.isBlocked(cache));
        assertEquals(5, cache.reads);
    }

    @Test
    void unassignedIdentityBlocksEvenWithoutIncidentHold() throws Exception {
        var cache = new ModernCache();
        cache.unassigned = true;
        assertTrue(IE2CacheTransferHold.isBlocked(cache));
        cache.unassigned = false;
        assertFalse(IE2CacheTransferHold.isBlocked(cache));
    }

    @Test
    void differentCacheInstancesHaveIndependentHoldState() throws Exception {
        var held = new ModernCache();
        held.transactionHeld = true;
        assertTrue(IE2CacheTransferHold.isBlocked(held));
        assertFalse(IE2CacheTransferHold.isBlocked(new ModernCache()));
    }

    @Test
    void missingPartialModernContractFailsClosed() throws Exception {
        assertTrue(IE2CacheTransferHold.isBlocked(new PartialCache()));
        assertThrows(ReflectiveOperationException.class, () -> IE2CacheTransferHold.isBlocked(null));
    }

    @Test
    void unreadableGuardCannotBeMisreadAsHealthy() throws Exception {
        assertThrows(ReflectiveOperationException.class, () -> IE2CacheTransferHold.isBlocked(new BrokenCache()));
        assertThrows(ReflectiveOperationException.class, () -> IE2CacheTransferHold.isBlocked(new WrongTypeCache()));
        var cache = new NullableCache();
        cache.value = null;
        assertThrows(ReflectiveOperationException.class, () -> IE2CacheTransferHold.isBlocked(cache));
        cache.value = true;
        assertTrue(IE2CacheTransferHold.isBlocked(cache));
        cache.value = false;
        assertFalse(IE2CacheTransferHold.isBlocked(cache));
    }
}

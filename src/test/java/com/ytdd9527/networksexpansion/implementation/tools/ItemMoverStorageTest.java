package com.ytdd9527.networksexpansion.implementation.tools;

import com.jeff_media.morepersistentdatatypes.DataType;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.utils.Keys;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.serialization.DelegateDeserialization;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real production ItemMover PDC/serializer and metadata writes; faults affect only live setters/callbacks. */
class ItemMoverStorageTest {
    private static final NamespacedKey OWNER = new NamespacedKey("item_mover_test", "owner");
    private static final NamespacedKey PENDING = new NamespacedKey("networks", "item_mover_pending_transfer");
    private static final NamespacedKey ROUND_TRIP = new NamespacedKey("item_mover_test", "round_trip");

    @BeforeAll
    static void initializeKeys() throws Exception {
        try (var networks = mockStatic(Networks.class);
             var integrations = mockStatic(SupportedPluginManager.class)) {
            Networks plugin = mock(Networks.class);
            SupportedPluginManager supported = mock(SupportedPluginManager.class);
            networks.when(Networks::getInstance).thenReturn(plugin);
            integrations.when(SupportedPluginManager::getInstance).thenReturn(supported);
            when(plugin.getName()).thenReturn("Networks");
            when(plugin.namespace()).thenReturn("networks");
            Class.forName(Keys.class.getName());
        }
    }

    @BeforeEach void setUp() { MockBukkit.mock(); }
    @AfterEach void tearDown() { MockBukkit.unmock(); }

    @Test void completeCreditIsDetachedAndPreservesEveryIdentityComponent() {
        ItemStack mover = mover(null, 0);
        ItemStack before = mover.clone();
        ItemStack incoming = token("A", 17);
        ItemStack candidate = ItemMoverStorage.prepareCredit(mover, incoming, 17);
        assertEquals(before, mover);
        assertEquals(17, incoming.getAmount());
        assertEquals(17, ItemMoverStorage.inspect(candidate).amount());
        assertEquals(token("A", 1), ItemMoverStorage.inspect(candidate).template());
        assertEquals("mover-owner", candidate.getItemMeta().getPersistentDataContainer().get(OWNER, PersistentDataType.STRING));
        assertEquals(before.getItemMeta().lore(), candidate.getItemMeta().lore());
        assertTrue(ItemMoverStorage.commit(mover, before, candidate));
        assertEquals(candidate, mover);
    }

    @Test void legacyIntegerMigratesOnlyWithVerifiedCompleteWrite() {
        ItemStack mover = mover(token("A", 1), 12);
        mover.editMeta(meta -> {
            meta.getPersistentDataContainer().remove(Keys.ITEM_MOVER_AMOUNT_LONG);
            meta.getPersistentDataContainer().set(Keys.ITEM_MOVER_AMOUNT, PersistentDataType.INTEGER, 12);
        });
        ItemStack incoming = token("A", 3);
        assertEquals(3, ItemMoverStorage.deposit(mover, incoming));
        assertEquals(15, ItemMoverStorage.inspect(mover).amount());
        assertFalse(mover.getItemMeta().getPersistentDataContainer().has(Keys.ITEM_MOVER_AMOUNT));
        assertEquals(0, incoming.getAmount());
        assertFalse(ItemMoverStorage.hasPendingTransfer(mover));
    }

    @Test void unknownPositiveBalanceIsNeverAssignedAnIncomingIdentity() {
        ItemStack mover = mover(null, 19);
        ItemStack before = mover.clone();
        ItemStack input = token("A", 4);
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.deposit(mover, input));
        assertEquals(before, mover);
        assertEquals(token("A", 4), input);
    }

    @Test void conflictingLegacyAndLongBalancesCannotBeGuessed() {
        ItemStack mover = mover(token("A", 1), 19);
        mover.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.ITEM_MOVER_AMOUNT, PersistentDataType.INTEGER, 11));
        ItemStack before = mover.clone();
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.prepareAmount(mover, 0));
        assertEquals(before, mover);
    }

    @Test void negativeAndWrongTypedBalancesRemainUntouched() {
        ItemStack negative = mover(token("A", 1), -4);
        ItemStack original = negative.clone();
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.prepareCredit(negative, token("A", 1), 1));
        assertEquals(original, negative);
        ItemStack malformed = mover(token("A", 1), 3);
        malformed.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.ITEM_MOVER_AMOUNT_LONG, PersistentDataType.STRING, "3"));
        ItemStack before = malformed.clone();
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.prepareAmount(malformed, 0));
        assertEquals(before, malformed);
    }

    @Test void differentPdcOwnerCannotEnterAssignedMover() {
        ItemStack mover = mover(token("A", 1), 13);
        ItemStack before = mover.clone();
        ItemStack incoming = token("B", 7);
        assertEquals(0, ItemMoverStorage.deposit(mover, incoming));
        assertEquals(before, mover);
        assertEquals(token("B", 7), incoming);
    }

    @Test void positiveBalanceCannotBeRelabelledOrItsIdentityCleared() {
        ItemStack mover = mover(token("A", 1), 13);
        ItemStack before = mover.clone();
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.prepareTemplate(mover, token("B", 1)));
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.prepareTemplate(mover, null));
        assertEquals(before, mover);
    }

    @Test void longMaximumAcceptsExactlyFreeCapacityAndKeepsPhysicalRemainder() {
        ItemStack mover = mover(token("A", 1), Long.MAX_VALUE - 3);
        ItemStack incoming = token("A", 17);
        assertEquals(3, ItemMoverStorage.deposit(mover, incoming));
        assertEquals(Long.MAX_VALUE, ItemMoverStorage.inspect(mover).amount());
        assertEquals(token("A", 14), incoming);
        assertEquals(0, ItemMoverStorage.deposit(mover, incoming));
        assertEquals(14, incoming.getAmount());
    }

    @Test void rejectedMetadataWriteCannotConsumePhysicalSource() {
        ObservedStack mover = observedMover(13);
        ItemStack before = new ItemStack(mover);
        mover.metaFault = Fault.REJECT;
        ItemStack input = token("A", 7);
        assertEquals(0, ItemMoverStorage.deposit(mover, input));
        assertEquals(before, mover);
        assertEquals(token("A", 7), input);
        assertEquals(1, mover.metadataWrites);
        mover.metaFault = Fault.NONE;
        assertEquals(7, ItemMoverStorage.deposit(mover, input));
        assertEquals(20, ItemMoverStorage.inspect(mover).amount());
    }

    @Test void thrownMetadataWriteBeforeMutationCannotConsumeSource() {
        ObservedStack mover = observedMover(13);
        ItemStack before = new ItemStack(mover);
        mover.metaFault = Fault.THROW_BEFORE;
        ItemStack input = token("A", 7);
        assertEquals(0, ItemMoverStorage.deposit(mover, input));
        assertEquals(before, mover);
        assertEquals(token("A", 7), input);
    }

    @Test void metadataWriteThenThrowUsesActualPdcReceiptExactlyOnce() {
        ObservedStack mover = observedMover(13);
        mover.metaFault = Fault.THROW_AFTER;
        ItemStack input = token("A", 7);
        assertEquals(7, ItemMoverStorage.deposit(mover, input));
        assertEquals(20, ItemMoverStorage.inspect(mover).amount());
        assertEquals(0, input.getAmount());
        assertEquals(2, mover.metadataWrites, "One reservation and one settlement, never two partial storage-field writes");
    }

    @Test void rejectedSourceDebitRollsBackOnlyTheExactReservedDestination() {
        ItemStack mover = mover(token("A", 1), 13);
        ItemStack before = mover.clone();
        ObservedStack input = new ObservedStack(token("A", 7));
        input.amountFault = Fault.REJECT;
        assertEquals(0, ItemMoverStorage.deposit(mover, input));
        assertEquals(before, mover);
        assertEquals(token("A", 7), input);
    }

    @Test void sourceDebitThenThrowStillHasOneExactCommittedQuantity() {
        ItemStack mover = mover(token("A", 1), 13);
        ObservedStack input = new ObservedStack(token("A", 7));
        input.amountFault = Fault.THROW_AFTER;
        assertEquals(7, ItemMoverStorage.deposit(mover, input));
        assertEquals(20, ItemMoverStorage.inspect(mover).amount());
        assertEquals(0, input.getAmount());
    }

    @Test void sourceChangedByMetadataCallbackIsNotOverwrittenOrCounted() {
        ObservedStack mover = observedMover(13);
        ItemStack before = new ItemStack(mover);
        ItemStack input = token("A", 7);
        mover.beforeMeta = () -> input.setAmount(3);
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.deposit(mover, input));
        assertEquals(before, mover);
        assertEquals(token("A", 3), input);
    }

    @Test void sameMoverReentryIsRefusedUntilOuterReceiptCompletes() {
        ObservedStack mover = observedMover(13);
        ItemStack inner = token("A", 5);
        AtomicInteger reached = new AtomicInteger();
        mover.beforeMeta = () -> {
            reached.incrementAndGet();
            assertEquals(0, ItemMoverStorage.deposit(mover, inner));
            assertEquals(0, ItemMoverStorage.transferTo(mover, 13, stack -> fail("Reentrant destination must not run")));
        };
        assertEquals(7, ItemMoverStorage.deposit(mover, token("A", 7)));
        assertEquals(1, reached.get());
        assertEquals(20, ItemMoverStorage.inspect(mover).amount());
        assertEquals(token("A", 5), inner);
    }

    @Test void samePhysicalSourceCannotBeCreditedIntoASecondMoverDuringCallback() {
        ObservedStack mover = observedMover(13);
        ItemStack second = mover(null, 0);
        ItemStack input = token("A", 7);
        mover.beforeMeta = () -> assertEquals(0, ItemMoverStorage.deposit(second, input));
        assertEquals(7, ItemMoverStorage.deposit(mover, input));
        assertEquals(20, ItemMoverStorage.inspect(mover).amount());
        assertEquals(0, ItemMoverStorage.inspect(second).amount());
    }

    @Test void staleMetadataSnapshotCannotOverwriteCurrentItem() {
        ItemStack mover = mover(token("A", 1), 13);
        ItemStack before = mover.clone();
        ItemStack candidate = ItemMoverStorage.prepareAmount(before, 9);
        mover.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "changed"));
        ItemStack changed = mover.clone();
        assertFalse(ItemMoverStorage.commit(mover, before, candidate));
        assertEquals(changed, mover);
    }

    @Test void loreCleanupDoesNotResurrectZeroBalanceKeysFromStaleMetadata() {
        ItemStack mover = mover(token("A", 1), 0);
        ItemStack before = mover.clone();
        ItemStack prepared = ItemMoverStorage.prepareLore(before, List.of("base lore"));
        assertTrue(ItemMoverStorage.commit(mover, before, prepared));
        var data = mover.getItemMeta().getPersistentDataContainer();
        assertFalse(data.has(Keys.ITEM_MOVER_ITEM));
        assertFalse(data.has(Keys.ITEM_MOVER_AMOUNT));
        assertFalse(data.has(Keys.ITEM_MOVER_AMOUNT_LONG));
        assertEquals("mover-owner", data.get(OWNER, PersistentDataType.STRING));
        assertEquals(List.of("base lore"), mover.getItemMeta().getLore());
    }

    @Test void lorePreparationCannotEraseUnidentifiedPositiveContents() {
        ItemStack mover = mover(null, 13);
        ItemStack before = mover.clone();
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.prepareLore(mover, List.of("new")));
        assertEquals(before, mover);
    }

    @Test void refusedMoverDebitNeverCallsExternalDestination() {
        ObservedStack mover = observedMover(37);
        mover.metaFault = Fault.REJECT;
        assertEquals(0, ItemMoverStorage.transferTo(mover, 17, stack -> fail("Unverified source cannot credit destination")));
        assertEquals(37, ItemMoverStorage.inspect(mover).amount());
    }

    @Test void partialExternalTransferRestoresOnlyVerifiedRemainder() {
        ItemStack mover = mover(token("A", 1), 37);
        int moved = ItemMoverStorage.transferTo(mover, 17, stack -> {
            assertEquals(token("A", 17), stack);
            assertTrue(ItemMoverStorage.hasPendingTransfer(mover));
            stack.setAmount(10);
        });
        assertEquals(7, moved);
        assertEquals(30, ItemMoverStorage.inspect(mover).amount());
        assertEquals(37, moved + ItemMoverStorage.inspect(mover).amount());
    }

    @Test void unchangedDestinationRestoresExactOriginalIncludingLegacyMetadata() {
        ItemStack mover = mover(token("A", 1), 37);
        ItemStack before = mover.clone();
        assertEquals(0, ItemMoverStorage.transferTo(mover, 17, stack -> {}));
        assertEquals(before, mover);
    }

    @Test void thrownPartialDestinationRetainsDurableOriginalAndOfferedReceipts() {
        ItemStack mover = mover(token("A", 1), 37);
        ItemStack before = mover.clone();
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.transferTo(mover, 17, stack -> {
            stack.setAmount(10);
            throw new IllegalStateException("Destination modified then failed; remainder is not an authorized refund");
        }));
        ItemStack reloaded = roundTrip(mover);
        assertTrue(ItemMoverStorage.hasPendingTransfer(reloaded));
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.inspect(reloaded));
        ItemMoverStorage.PendingReceipt receipt = ItemMoverStorage.pendingReceipt(reloaded);
        assertNotNull(receipt);
        assertStoredMoverSnapshot(before, receipt.original());
        assertEquals(token("A", 17), receipt.offered());
        assertEquals("external-output", receipt.direction());
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.transferTo(reloaded, 1, stack -> fail("Pending transfer must not replay")));
    }

    @Test void invalidDestinationRemainderRemainsHeldAndCannotBecomeARefund() {
        ItemStack mover = mover(token("A", 1), 37);
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.transferTo(mover, 17, stack -> stack.setAmount(18)));
        assertTrue(ItemMoverStorage.hasPendingTransfer(mover));
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.deposit(mover, token("A", 1)));
    }

    @Test void settlementRefusalRetainsPendingReceiptInsteadOfMakingDebitReplayable() {
        ObservedStack mover = observedMover(37);
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.transferTo(mover, 17, stack -> {
            stack.setAmount(10);
            mover.metaFault = Fault.REJECT;
        }));
        assertTrue(ItemMoverStorage.hasPendingTransfer(roundTrip(mover)));
    }

    @Test void externalWithdrawalCannotRunBeforeMoverPdcReservation() {
        ObservedStack mover = observedMover(37);
        mover.metaFault = Fault.REJECT;
        assertEquals(0, ItemMoverStorage.transferFrom(mover, token("A", 1), 17, amount -> {
            fail("Unverified mover cannot withdraw external source"); return null;
        }));
        assertEquals(37, ItemMoverStorage.inspect(mover).amount());
    }

    @Test void partialExternalWithdrawalCreditsExactReceiptOnlyOnce() {
        ItemStack mover = mover(token("A", 1), Long.MAX_VALUE - 7);
        AtomicInteger calls = new AtomicInteger();
        assertEquals(3, ItemMoverStorage.transferFrom(mover, token("A", 1), 17, amount -> {
            calls.incrementAndGet();
            assertEquals(7, amount);
            assertTrue(ItemMoverStorage.hasPendingTransfer(mover));
            return token("A", 3);
        }));
        assertEquals(1, calls.get());
        assertEquals(Long.MAX_VALUE - 4, ItemMoverStorage.inspect(mover).amount());
    }

    @Test void invalidExternalReceiptPreservesReservationWithoutGuessingIdentityOrRefund() {
        ItemStack mover = mover(token("A", 1), 37);
        AtomicInteger calls = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.transferFrom(mover, token("A", 1), 17, amount -> {
            calls.incrementAndGet(); return token("B", amount);
        }));
        assertEquals(1, calls.get());
        assertTrue(ItemMoverStorage.hasPendingTransfer(roundTrip(mover)));
    }

    @Test void unregisteredItemWrapperCannotCreateAnUnreadableRecoveryReceipt() {
        ItemStack wrapper = new UnregisteredMover(mover(token("A", 1), 37));
        AtomicInteger called = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> ItemMoverStorage.transferTo(wrapper, 17, stack -> {
            called.incrementAndGet();
            throw new IllegalStateException("Injected external failure");
        }));
        assertEquals(1, called.get(), "Serialization preflight must succeed with the exact standard copy");
        ItemMoverStorage.PendingReceipt receipt = ItemMoverStorage.pendingReceipt(wrapper);
        assertNotNull(receipt);
        assertStoredMoverSnapshot(mover(token("A", 1), 37), receipt.original());
        assertStoredMoverSnapshot(mover(token("A", 1), 20), receipt.prepared());
        assertEquals(token("A", 17), receipt.offered());
    }

    @Test void detachedHandSnapshotMustCommitToTheAuthoritativeSlotBeforeDestination() {
        ItemStack expected = mover(token("A", 1), 37);
        AtomicReference<ItemStack> slot = new AtomicReference<>(expected.clone());
        assertEquals(7, ItemMoverStorage.transferToSlot(expected, () -> slot.get().clone(),
            item -> slot.set(item.clone()), 17, stack -> {
                assertTrue(ItemMoverStorage.hasPendingTransfer(slot.get()));
                assertEquals(37, ItemMoverStorage.inspect(expected).amount(), "Detached event object is not the authoritative hand");
                stack.setAmount(10);
            }));
        assertEquals(30, ItemMoverStorage.inspect(slot.get()).amount());
        assertEquals(37, ItemMoverStorage.inspect(expected).amount());
    }

    @Test void rebuildingWrapperCloneCannotRelabelSourceOrOfferedItems() {
        ItemStack mover = new RebuildingStack(mover(token("A", 1), 37));
        mover.editMeta(meta -> meta.setCustomModelData(731));
        assertEquals(5, ItemMoverStorage.transferTo(mover, 5, stack -> {
            assertEquals(token("A", 5), stack);
            stack.setAmount(0);
        }));
        assertEquals(32, ItemMoverStorage.inspect(mover).amount());
        assertEquals(731, mover.getItemMeta().getCustomModelData());
        assertEquals("mover-owner", mover.getItemMeta().getPersistentDataContainer().get(OWNER, PersistentDataType.STRING));
        ItemStack incoming = new RebuildingStack(token("A", 7));
        assertEquals(7, ItemMoverStorage.deposit(mover, incoming));
        assertEquals(39, ItemMoverStorage.inspect(mover).amount());
        assertEquals(token("A", 1), ItemMoverStorage.inspect(mover).template());
        assertEquals(0, incoming.getAmount());
    }

    @Test void staleHandCannotWithdrawOrReceiveExternalItems() {
        ItemStack expected = mover(token("A", 1), 37);
        ItemStack current = mover(token("A", 1), 11);
        assertEquals(0, ItemMoverStorage.transferToSlot(expected, () -> current,
            item -> fail("Stale hand must not be overwritten"), 17, stack -> fail("Stale hand cannot deposit")));
        assertEquals(0, ItemMoverStorage.transferFromSlot(expected, () -> current,
            item -> fail("Stale hand must not be overwritten"), token("A", 1), 17,
            amount -> { fail("Stale hand cannot withdraw"); return null; }));
        assertEquals(11, ItemMoverStorage.inspect(current).amount());
    }

    @Test void rejectedHandWriteCannotTriggerExternalMutation() {
        ItemStack current = mover(token("A", 1), 37);
        assertEquals(0, ItemMoverStorage.transferToSlot(current.clone(), () -> current.clone(),
            item -> {}, 17, stack -> fail("Hand setter rejected reservation")));
        assertEquals(37, ItemMoverStorage.inspect(current).amount());
    }

    @Test void handWriteThenThrowUsesActualSlotReadback() {
        AtomicReference<ItemStack> slot = new AtomicReference<>(mover(token("A", 1), 37));
        assertEquals(7, ItemMoverStorage.transferToSlot(slot.get().clone(), () -> slot.get().clone(), item -> {
            slot.set(item.clone());
            throw new IllegalStateException("Applied hand write before failure");
        }, 17, stack -> stack.setAmount(10)));
        assertEquals(30, ItemMoverStorage.inspect(slot.get()).amount());
    }

    @Test void externalSourceCreditsTheActualHandAndLeavesDetachedEventUntouched() {
        ItemStack event = mover(token("A", 1), 37);
        AtomicReference<ItemStack> slot = new AtomicReference<>(event.clone());
        assertEquals(7, ItemMoverStorage.transferFromSlot(event, () -> slot.get().clone(),
            item -> slot.set(item.clone()), token("A", 1), 17, amount -> token( "A", 7)));
        assertEquals(44, ItemMoverStorage.inspect(slot.get()).amount());
        assertEquals(37, ItemMoverStorage.inspect(event).amount());
    }

    // Deliberately no Bukkit deserialization annotation, as with some real Slimefun wrappers.
    private static final class UnregisteredMover extends ItemStack {
        private UnregisteredMover(ItemStack source) { super(source); }
        @Override public ItemStack clone() { return new UnregisteredMover(super.clone()); }
        @Override public boolean equals(Object other) {
            if (other instanceof ItemStack && !(other instanceof UnregisteredMover)) {
                throw new ClassCastException("Native Paper cross-wrapper equality");
            }
            return other instanceof UnregisteredMover stack && getType() == stack.getType()
                && getAmount() == stack.getAmount()
                && java.util.Objects.equals(getItemMeta(), stack.getItemMeta());
        }
        @Override public int hashCode() { return java.util.Objects.hash(getType(), getAmount(), getItemMeta()); }
    }

    private static final class RebuildingStack extends ItemStack {
        private RebuildingStack(ItemStack source) { super(source); }
        @Override public ItemStack clone() {
            ItemStack changed = super.clone();
            changed.editMeta(meta -> {
                meta.setCustomModelData(999);
                meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "rebuilt-owner");
            });
            return changed;
        }
    }

    private static ItemStack roundTrip(ItemStack stack) {
        ItemStack carrier = new ItemStack(Material.STONE);
        carrier.editMeta(meta -> meta.getPersistentDataContainer().set(ROUND_TRIP, DataType.ITEM_STACK, stack));
        return carrier.getItemMeta().getPersistentDataContainer().get(ROUND_TRIP, DataType.ITEM_STACK);
    }

    private static void assertStoredMoverSnapshot(ItemStack expected, ItemStack actual) {
        // MockBukkit PDC equality delegates to Map.equals, which compares byte[] by
        // reference after deserialization. Compare the original stored bytes exactly,
        // alongside every other fixture field, without mocking the real serializer.
        assertEquals(expected.getType(), actual.getType());
        assertEquals(expected.getAmount(), actual.getAmount());
        assertEquals(expected.getItemMeta().displayName(), actual.getItemMeta().displayName());
        assertEquals(expected.getItemMeta().lore(), actual.getItemMeta().lore());
        var first = expected.getItemMeta().getPersistentDataContainer();
        var second = actual.getItemMeta().getPersistentDataContainer();
        assertEquals(first.getKeys(), second.getKeys());
        assertEquals(first.get(OWNER, PersistentDataType.STRING), second.get(OWNER, PersistentDataType.STRING));
        assertArrayEquals(first.get(Keys.ITEM_MOVER_ITEM, PersistentDataType.BYTE_ARRAY), second.get(Keys.ITEM_MOVER_ITEM, PersistentDataType.BYTE_ARRAY));
        assertEquals(first.get(Keys.ITEM_MOVER_AMOUNT, PersistentDataType.INTEGER), second.get(Keys.ITEM_MOVER_AMOUNT, PersistentDataType.INTEGER));
        assertEquals(first.get(Keys.ITEM_MOVER_AMOUNT_LONG, PersistentDataType.LONG), second.get(Keys.ITEM_MOVER_AMOUNT_LONG, PersistentDataType.LONG));
        assertEquals(ItemMoverStorage.inspect(expected), ItemMoverStorage.inspect(actual));
    }

    private static ItemStack mover(ItemStack template, long amount) {
        ItemStack mover = new ItemStack(Material.DEBUG_STICK);
        mover.editMeta(meta -> {
            meta.setDisplayName("Existing ItemMover");
            meta.setLore(List.of("existing lore"));
            var data = meta.getPersistentDataContainer();
            data.set(OWNER, PersistentDataType.STRING, "mover-owner");
            if (template != null) data.set(Keys.ITEM_MOVER_ITEM, DataType.ITEM_STACK, template);
            if (amount != 0 || template != null) data.set(Keys.ITEM_MOVER_AMOUNT_LONG, PersistentDataType.LONG, amount);
        });
        return mover;
    }

    private static ItemStack token(String owner, int amount) {
        ItemStack stack = new ItemStack(Material.DIAMOND, amount);
        stack.editMeta(meta -> {
            meta.setDisplayName("Same display for different owners");
            meta.setLore(List.of("identity lore", owner));
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, owner);
        });
        return stack;
    }

    private static ObservedStack observedMover(long amount) {
        return new ObservedStack(mover(token("A", 1), amount));
    }

    private enum Fault { NONE, REJECT, THROW_BEFORE, THROW_AFTER }

    @DelegateDeserialization(ItemStack.class)
    private static final class ObservedStack extends ItemStack {
        private Fault metaFault = Fault.NONE;
        private Fault amountFault = Fault.NONE;
        private int metadataWrites;
        private Runnable beforeMeta;
        private ObservedStack(ItemStack original) { super(original); }
        // Detached preparation remains ordinary Bukkit data. Faults apply to the actual live endpoint only.
        @Override public ItemStack clone() { return super.clone(); }
        @Override public boolean setItemMeta(ItemMeta meta) {
            metadataWrites++;
            Runnable callback = beforeMeta;
            beforeMeta = null;
            if (callback != null) callback.run();
            if (metaFault == Fault.REJECT) return false;
            if (metaFault == Fault.THROW_BEFORE) throw new IllegalStateException("Rejected metadata write");
            boolean result = super.setItemMeta(meta);
            if (metaFault == Fault.THROW_AFTER) throw new IllegalStateException("Applied metadata then threw");
            return result;
        }
        @Override public void setAmount(int amount) {
            if (amountFault == Fault.REJECT) return;
            if (amountFault == Fault.THROW_BEFORE) throw new IllegalStateException("Rejected source quantity write");
            super.setAmount(amount);
            if (amountFault == Fault.THROW_AFTER) throw new IllegalStateException("Applied source quantity then threw");
        }
    }
}

package io.github.sefiraat.networks.utils;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Real BlockMenuUtil.pushItem() with fail-after-write and silent menu setters.
 * The offered ItemStack may be mutated by pushItem, so the only trusted source
 * for rollback accounting is exact destination slot readback.
 */
class NetworkMenuWithdrawalIntegrityTest {
    private final Location accessor = new Location(null, 31, 64, 42);
    private final Map<Integer, ItemStack> slots = new HashMap<>();
    private MockedStatic<Networks> networks;
    private NetworkRoot root;
    private BlockMenu menu;
    private int restored;

    @BeforeEach
    void setUp() throws ClassNotFoundException {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        Networks plugin = mock(Networks.class);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getInstance).thenReturn(plugin);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        when(plugin.getLogger()).thenReturn(logger);
        Class.forName(StackUtils.class.getName());
        Class.forName(NetworkRoot.class.getName());
        TransferAudit.reset();

        root = mock(NetworkRoot.class);
        menu = mock(BlockMenu.class);
        when(menu.getSize()).thenReturn(9);
        when(menu.getLocation()).thenReturn(accessor);
        when(menu.getItemInSlot(anyInt())).thenAnswer(call -> slots.get((int) call.getArgument(0)));
        doAnswer(call -> {
            int slot = call.getArgument(0);
            slots.put(slot, call.getArgument(1));
            return null;
        }).when(menu).replaceExistingItem(anyInt(), nullable(ItemStack.class));
        doAnswer(call -> {
            ItemStack incoming = call.getArgument(1);
            restored += incoming.getAmount();
            incoming.setAmount(0);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));
    }

    @AfterEach
    void tearDown() {
        try {
            if (networks != null) networks.close();
        } finally {
            TransferAudit.reset();
            MenuTransferSafetyHold.reset();
            MockBukkit.unmock();
        }
    }

    @Test
    void successfulInsertConsumesExactlyTheWithdrawnAmount() {
        assertEquals(8, push(dust(8), 0));
        assertEquals(8, quantity(0));
        assertEquals(0, restored);
        assertEquals(8, TransferAudit.snapshot().withdrawalCommitted());
    }

    @Test
    void destinationWriteThenThrowNeverRefundsAnInstalledStack() {
        doAnswer(call -> {
            slots.put(0, call.getArgument(1));
            throw new IllegalStateException("setter wrote item then threw");
        }).when(menu).replaceExistingItem(eq(0), nullable(ItemStack.class));

        assertEquals(8, push(dust(8), 0));
        assertEquals(8, quantity(0));
        assertEquals(0, restored, "Restoring this stack would mint another copy");
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void destinationThrowsBeforeWriteSoWholeStackReturnsToNetwork() {
        doAnswer(call -> { throw new IllegalStateException("setter refused write"); })
            .when(menu).replaceExistingItem(eq(0), nullable(ItemStack.class));

        assertEquals(0, push(dust(8), 0));
        assertEquals(0, quantity(0));
        assertEquals(8, restored);
    }

    @Test
    void partialFirstSlotAndLaterWriteThenThrowStayExactlyConserved() {
        slots.put(0, dust(60));
        doAnswer(call -> {
            slots.put(1, call.getArgument(1));
            throw new IllegalStateException("second setter threw after applying");
        }).when(menu).replaceExistingItem(eq(1), nullable(ItemStack.class));

        assertEquals(8, push(dust(8), 0, 1));
        assertEquals(64, quantity(0));
        assertEquals(4, quantity(1));
        assertEquals(0, restored);
        assertEquals(60 + 8, quantity(0) + quantity(1) + restored);
    }

    @Test
    void partialFirstSlotAndLaterRejectedWriteRestoreOnlyUncommittedAmount() {
        slots.put(0, dust(60));
        doAnswer(call -> { throw new IllegalStateException("second setter refused"); })
            .when(menu).replaceExistingItem(eq(1), nullable(ItemStack.class));

        assertEquals(4, push(dust(8), 0, 1));
        assertEquals(64, quantity(0));
        assertEquals(0, quantity(1));
        assertEquals(4, restored);
        assertEquals(68, quantity(0) + quantity(1) + restored);
    }

    @Test
    void silentSetterRejectionNeverCausesItemLoss() {
        doAnswer(call -> null).when(menu).replaceExistingItem(eq(0), nullable(ItemStack.class));

        assertEquals(0, push(dust(8), 0));
        assertEquals(0, quantity(0));
        assertEquals(8, restored);
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void foreignSlotReplacementIsAmbiguousAndNeverBlindlyRefunded() {
        doAnswer(call -> {
            slots.put(0, new ItemStack(Material.EMERALD, 8));
            throw new IllegalStateException("foreign slot replacement");
        }).when(menu).replaceExistingItem(eq(0), nullable(ItemStack.class));

        assertEquals(0, push(dust(8), 0));
        assertEquals(Material.EMERALD, slots.get(0).getType());
        assertEquals(0, restored, "An unreadable or replaced destination must fail closed");
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void afterCommitDirtyFailureDoesNotRestoreAlreadyInsertedItems() {
        AtomicInteger dirtyCalls = new AtomicInteger();
        doAnswer(call -> {
            if (dirtyCalls.incrementAndGet() == 1) {
                throw new IllegalStateException("first markDirty applied too late");
            }
            return null;
        }).when(menu).markDirty();

        assertEquals(8, push(dust(8), 0));
        assertEquals(8, quantity(0));
        assertEquals(0, restored);
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void persistentDirtyFailureQuarantinesTheMachineWithoutRefundingCommittedItems() {
        doThrow(new IllegalStateException("cannot mark machine dirty"))
            .when(menu).markDirty();

        assertEquals(8, push(dust(8), 0));
        assertEquals(8, quantity(0));
        assertEquals(0, restored);
        assertTrue(MenuTransferSafetyHold.isHeld(menu));
        assertEquals(1, MenuTransferSafetyHold.count());

        // Subsequent offers are safely returned to the network without another insertion.
        assertEquals(0, push(dust(3), 0));
        assertEquals(3, restored);
        assertEquals(8, quantity(0));
    }

    @Test
    void noValidDestinationSlotReturnsTheEntireStack() {
        assertEquals(0, push(dust(8), -1, 55));
        assertEquals(8, restored);
    }

    @Test
    void duplicateSlotIndicesAreNotCountedTwice() {
        slots.put(0, dust(60));

        assertEquals(4, push(dust(4), 0, 0));
        assertEquals(64, quantity(0));
        assertEquals(0, restored);
    }

    @Test
    void snapshotFailurePreventsAllMenuWritesAndRefundsTheSource() {
        when(menu.getItemInSlot(eq(0))).thenThrow(new IllegalStateException("preflight read failure"));

        assertEquals(0, push(dust(8), 0));
        assertEquals(8, restored);
        verify(menu, never()).replaceExistingItem(anyInt(), nullable(ItemStack.class));
    }

    @Test
    void ambiguousDestinationIsHeldUntilTheMenuIsReplaced() {
        doAnswer(call -> {
            slots.put(0, new ItemStack(Material.EMERALD, 8));
            throw new IllegalStateException("unexpected machine replacement");
        }).when(menu).replaceExistingItem(eq(0), nullable(ItemStack.class));

        assertEquals(0, push(dust(8), 0));
        assertTrue(MenuTransferSafetyHold.isHeld(menu));
        assertEquals(1, MenuTransferSafetyHold.count());

        // Even an already-withdrawn offer must be returned without writing the held menu.
        assertEquals(0, push(dust(4), 0));
        assertEquals(4, restored);
        verify(menu, times(1)).replaceExistingItem(eq(0), nullable(ItemStack.class));

        // A newly registered BlockMenu at the same location does not inherit the hold.
        BlockMenu replacement = mock(BlockMenu.class);
        when(replacement.getLocation()).thenReturn(accessor);

        // A different wrapper object is not proof that the block was replaced.
        assertTrue(MenuTransferSafetyHold.isHeld(replacement));
        assertEquals(1, MenuTransferSafetyHold.count());
        assertTrue(MenuTransferSafetyHold.describe(5).getFirst().contains("possible-unreconciled=8xGLOWSTONE_DUST"));

        try (MockedStatic<StorageCacheUtils> storage = mockStatic(StorageCacheUtils.class)) {
            storage.when(() -> StorageCacheUtils.getMenu(accessor)).thenReturn(replacement);
            assertFalse(MenuTransferSafetyHold.isHeld(replacement));
        }
        assertEquals(0, MenuTransferSafetyHold.count());
    }

    @Test
    void readbackFailureAfterWriteDoesNotDuplicateUnknownDestination() {
        AtomicInteger calls = new AtomicInteger();
        when(menu.getItemInSlot(eq(0))).thenAnswer(call -> {
            if (calls.incrementAndGet() >= 3) throw new IllegalStateException("readback failure");
            return slots.get(0);
        });

        assertEquals(0, push(dust(8), 0));
        assertEquals(8, quantity(0));
        assertEquals(0, restored);
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    private int push(ItemStack offered, int... inputSlots) {
        return NetworkTransferUtils.commitNetworkWithdrawal(root, accessor, menu, offered, inputSlots);
    }

    private int quantity(int slot) {
        ItemStack stack = slots.get(slot);
        return stack == null || stack.getType().isAir() ? 0 : stack.getAmount();
    }

    private static ItemStack dust(int amount) {
        ItemStack item = new ItemStack(Material.GLOWSTONE_DUST, amount);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
            NamespacedKey.fromString("test:dust_owner"), PersistentDataType.STRING, "stable");
        item.setItemMeta(meta);
        return item;
    }
}

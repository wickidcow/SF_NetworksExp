package io.github.sefiraat.networks.utils;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests the public network-to-cursor withdrawal boundary with real StackUtils
 * matching and an untrusted Bukkit cursor setter.
 */
class NetworkCursorWithdrawalIntegrityTest {
    private final Location accessor = new Location(null, 8, 64, 12);
    private final AtomicReference<ItemStack> cursor = new AtomicReference<>();
    private MockedStatic<Networks> networks;
    private NetworkRoot root;
    private Player player;
    private int returned;

    @BeforeEach
    void setUp() throws ClassNotFoundException {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        Networks plugin = mock(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getInstance).thenReturn(plugin);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        when(plugin.getLogger()).thenReturn(logger);
        Class.forName(StackUtils.class.getName());
        Class.forName(NetworkRoot.class.getName());

        root = mock(NetworkRoot.class);
        player = mock(Player.class);
        cursor.set(null);
        returned = 0;
        when(player.getLocation()).thenReturn(accessor);
        when(player.getItemOnCursor()).thenAnswer(call -> cursor.get());
        doAnswer(call -> {
            cursor.set(call.getArgument(0));
            return null;
        }).when(player).setItemOnCursor(any(ItemStack.class));

        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            ItemRequest request = call.getArgument(1);
            return diamond(request.getAmount());
        });
        doAnswer(call -> {
            ItemStack incoming = call.getArgument(1);
            returned += incoming.getAmount();
            incoming.setAmount(0);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));
        TransferAudit.reset();
    }

    @AfterEach
    void tearDown() {
        try {
            TransferAudit.reset();
            if (networks != null) {
                networks.close();
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void emptyCursorAcceptsAnExactWithdrawal() {
        assertEquals(8, move(8));
        assertEquals(8, current());
        assertEquals(0, returned);
        assertEquals(8, TransferAudit.snapshot().withdrawalCommitted());
        assertEquals(8, current() + returned);
    }

    @Test
    void compatiblePartialCursorPreservesExistingMetadataAndCount() {
        cursor.set(diamond(60));
        assertEquals(4, move(8));
        assertEquals(64, current());
        assertEquals(0, returned);
        assertEquals(64, current() + returned);
    }

    @Test
    void setterAppliesFullStackThenThrowsWithoutMintingNetworkRefund() {
        doAnswer(call -> {
            cursor.set(call.getArgument(0));
            throw new IllegalStateException("Cursor write applied then threw");
        }).when(player).setItemOnCursor(any(ItemStack.class));

        assertEquals(8, move(8));
        assertEquals(8, current());
        assertEquals(0, returned);
        assertEquals(8, TransferAudit.snapshot().withdrawalCommitted());
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void setterRefusesWithoutWritingAndTheSourceIsRestored() {
        doAnswer(call -> { throw new IllegalStateException("Cursor refused write"); })
            .when(player).setItemOnCursor(any(ItemStack.class));

        assertEquals(0, move(8));
        assertEquals(0, current());
        assertEquals(8, returned);
        assertEquals(8, current() + returned);
    }

    @Test
    void partialCursorWriteThenThrowOnlyRefundsUncommittedRemainder() {
        doAnswer(call -> {
            cursor.set(diamond(3));
            throw new IllegalStateException("Cursor kept only three");
        }).when(player).setItemOnCursor(any(ItemStack.class));

        assertEquals(3, move(8));
        assertEquals(3, current());
        assertEquals(5, returned);
        assertEquals(8, current() + returned);
    }

    @Test
    void silentPartialAcceptanceIsCountedWithoutLoss() {
        doAnswer(call -> {
            cursor.set(diamond(3));
            return null;
        }).when(player).setItemOnCursor(any(ItemStack.class));

        assertEquals(3, move(8));
        assertEquals(3, current());
        assertEquals(5, returned);
    }

    @Test
    void foreignCursorReplacementFailsClosedInsteadOfDuplicatingIt() {
        doAnswer(call -> {
            cursor.set(new ItemStack(Material.EMERALD, 3));
            throw new IllegalStateException("Foreign cursor replacement");
        }).when(player).setItemOnCursor(any(ItemStack.class));

        assertEquals(0, move(8));
        assertEquals(Material.EMERALD, cursor.get().getType());
        assertEquals(0, returned);
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void changedCursorDuringWithdrawalIsNotOverwritten() {
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            cursor.set(new ItemStack(Material.EMERALD, 1));
            return diamond(8);
        });

        assertEquals(0, move(8));
        assertEquals(Material.EMERALD, cursor.get().getType());
        assertEquals(8, returned);
        verify(player, never()).setItemOnCursor(any(ItemStack.class));
    }

    @Test
    void unreadableCursorAfterWriteNeverTriggersBlindRefund() {
        AtomicInteger reads = new AtomicInteger();
        when(player.getItemOnCursor()).thenAnswer(call -> {
            if (reads.incrementAndGet() >= 3) {
                throw new IllegalStateException("Unreadable post-write cursor");
            }
            return cursor.get();
        });

        assertEquals(0, move(8));
        assertEquals(8, current());
        assertEquals(0, returned);
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void excessWithdrawalIsRolledBackBeforeItCanOverfillCursor() {
        cursor.set(diamond(60));
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenReturn(diamond(8));

        assertEquals(0, move(8));
        assertEquals(60, current());
        assertEquals(8, returned);
        verify(player, never()).setItemOnCursor(any(ItemStack.class));
    }

    private int move(int amount) {
        return NetworkTransferUtils.moveNetworkItemOntoCursor(
            root, accessor, player, diamond(1), amount);
    }

    private int current() {
        ItemStack stack = cursor.get();
        return stack == null || stack.getType().isAir() ? 0 : stack.getAmount();
    }

    private static ItemStack diamond(int count) {
        ItemStack item = new ItemStack(Material.DIAMOND, count);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
            NamespacedKey.fromString("test:cursor_owner"),
            PersistentDataType.STRING,
            "preserved");
        item.setItemMeta(meta);
        return item;
    }
}

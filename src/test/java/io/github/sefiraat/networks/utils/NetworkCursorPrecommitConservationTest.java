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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Source callbacks must not cause an identical second credit through a cursor refund. */
class NetworkCursorPrecommitConservationTest {
    private final Location accessor = new Location(null, 51, 64, 51);
    private final Location otherAccessor = new Location(null, 52, 64, 51);
    private final Location controller = new Location(null, 50, 64, 51);
    private final AtomicReference<ItemStack> cursor = new AtomicReference<>();
    private MockedStatic<Networks> networks;
    private NetworkRoot root;
    private Player player;
    private int stock;
    private int refunded;

    @BeforeEach
    void setUp() throws ClassNotFoundException {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        Networks plugin = mock(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        networks.when(Networks::getInstance).thenReturn(plugin);
        networks.when(Networks::getConfigManager).thenReturn(config);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        when(plugin.getLogger()).thenReturn(logger);
        Class.forName(StackUtils.class.getName());
        Class.forName(NetworkRoot.class.getName());
        NetworkTransferUtils.resetRuntimeSafety();
        TransferAudit.reset();
        stock = 16;
        refunded = 0;
        cursor.set(null);
        root = mock(NetworkRoot.class);
        when(root.getNodePosition()).thenReturn(controller);
        player = mock(Player.class);
        when(player.getLocation()).thenReturn(accessor);
        when(player.getItemOnCursor()).thenAnswer(call -> cursor.get());
        doAnswer(call -> {
            cursor.set(call.getArgument(0));
            return null;
        }).when(player).setItemOnCursor(any(ItemStack.class));
        doAnswer(call -> {
            ItemStack incoming = call.getArgument(1);
            refunded += incoming.getAmount();
            stock += incoming.getAmount();
            incoming.setAmount(0);
            return null;
        }).when(root).addItemStack0(any(Location.class), any(ItemStack.class));
    }

    @AfterEach
    void tearDown() {
        try {
            NetworkTransferUtils.resetRuntimeSafety();
            TransferAudit.reset();
            if (networks != null) networks.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @ParameterizedTest
    @CsvSource({"0,8", "4,8", "0,3", "4,3"})
    void sourceCallbackCursorCreditMustNotBeRefundedAgain(int previous, int delivered) {
        cursor.set(previous == 0 ? null : diamond(previous));
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            stock -= 8;
            cursor.set(diamond(previous + delivered));
            return diamond(8);
        });

        assertEquals(0, move(accessor));
        assertEquals(0, refunded, "Cursor callback already accepted items; no speculative refund is safe");
        assertEquals(previous + delivered, cursor.get().getAmount());
        assertTrue(stock + cursor.get().getAmount() <= 16 + previous, "A withdrawal must never mint items");
        if (delivered == 8) assertEquals(16 + previous, stock + cursor.get().getAmount());
        assertQuarantinedWithoutCursorWrite();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void unreadablePrecommitCursorCannotAuthorizeARefund(boolean callbackWrote) {
        AtomicInteger reads = new AtomicInteger();
        when(player.getItemOnCursor()).thenAnswer(call -> {
            if (reads.incrementAndGet() > 1) throw new IllegalStateException("Unverified cursor readback");
            return null;
        });
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            stock -= 8;
            if (callbackWrote) cursor.set(diamond(8));
            return diamond(8);
        });

        assertEquals(0, move(accessor));
        assertEquals(0, refunded, "An unreadable destination is not evidence of zero acceptance");
        assertEquals(8, stock);
        assertQuarantinedWithoutCursorWrite();
    }

    private void assertQuarantinedWithoutCursorWrite() {
        assertEquals(1, NetworkTransferUtils.getHeldWithdrawalSourceCount());
        assertTrue(NetworkTransferUtils.isNetworkRootHeld(root, otherAccessor));
        verify(player, never()).setItemOnCursor(any(ItemStack.class));
        assertEquals(0, move(otherAccessor));
        verify(root, never()).getItemStack0(eq(otherAccessor), any(ItemRequest.class));
        verify(root, never()).addItemStack0(any(Location.class), any(ItemStack.class));
    }

    private int move(Location from) {
        return NetworkTransferUtils.moveNetworkItemOntoCursor(root, from, player, diamond(1), 8);
    }

    private static ItemStack diamond(int count) {
        ItemStack item = new ItemStack(Material.DIAMOND, count);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("test", "cursor_conservation"), PersistentDataType.STRING, "owned"));
        return item;
    }
}

package io.github.sefiraat.networks.utils;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.api.enums.TransportMode;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.balugaq.netex.utils.LineOperationUtil;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Covers the real VOID grab caller, not only the compensation helper in isolation. */
class NetworkVoidTransferTest {
    private static final int SLOT = 2;
    private static final int NEXT_SLOT = 3;
    private final Location accessor = new Location(null, -1314, 66, 817);
    private final AtomicReference<ItemStack> source = new AtomicReference<>();
    private MockedStatic<Networks> networks;
    private MockedStatic<BlockMenuUtil> menus;
    private NetworkRoot root;
    private BlockMenu menu;
    private ItemStack original;
    private ItemStack next;
    private int accepted;

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

        original = diamond(16);
        next = new ItemStack(Material.IRON_INGOT, 8);
        source.set(original);
        root = mock(NetworkRoot.class);
        menu = mock(BlockMenu.class);
        when(root.allowAccessInput(accessor)).thenReturn(true);
        when(menu.getSize()).thenReturn(9);
        when(menu.getLocation()).thenReturn(accessor);
        when(menu.getItemInSlot(SLOT)).thenAnswer(call -> source.get());
        when(menu.getItemInSlot(NEXT_SLOT)).thenReturn(next);
        doAnswer(call -> {
            source.set(call.getArgument(1));
            return null;
        }).when(menu).replaceExistingItem(eq(SLOT), nullable(ItemStack.class));
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            ItemRequest request = call.getArgument(1);
            int recovered = Math.min(accepted, request.getAmount());
            accepted -= recovered;
            return recovered == 0 ? null : diamond(recovered);
        });
        menus = mockStatic(BlockMenuUtil.class);
        menus.when(() -> BlockMenuUtil.getSafeTransportSlots(menu, ItemTransportFlow.WITHDRAW))
            .thenReturn(new int[]{SLOT, NEXT_SLOT});
    }

    @AfterEach
    void tearDown() {
        try {
            if (menus != null) menus.close();
        } finally {
            try {
                if (networks != null) networks.close();
            } finally {
                NetworkTransferUtils.resetRuntimeSafety();
                TransferAudit.reset();
                MockBukkit.unmock();
            }
        }
    }

    @ParameterizedTest
    @CsvSource({"0,type", "4,type", "16,type", "0,metadata", "4,metadata", "16,metadata",
        "0,amount", "4,amount", "16,amount", "0,empty", "4,empty", "16,empty"})
    void changedSourceIsNeverVoidedAndThePassStops(int amount, String change) {
        ItemStack external = switch (change) {
            case "type" -> new ItemStack(Material.EMERALD, 16);
            case "amount" -> diamond(11);
            case "empty" -> null;
            default -> diamond(16);
        };
        if (change.equals("metadata")) {
            var meta = external.getItemMeta();
            meta.getPersistentDataContainer().set(NamespacedKey.fromString("oldaddon:owner"),
                PersistentDataType.STRING, "replacement-owner");
            external.setItemMeta(meta);
        }
        doAnswer(call -> {
            accept(call.getArgument(1), amount);
            source.set(external);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        LineOperationUtil.grabItem(accessor, root, menu, TransportMode.VOID, 64);

        assertSame(external, source.get());
        assertEquals(0, accepted);
        assertPassStopped();
        verify(menu, never()).replaceExistingItem(anyInt(), nullable(ItemStack.class));
        assertEquals(amount, TransferAudit.snapshot().depositCompensationRequested());
        assertEquals(amount, TransferAudit.snapshot().depositCompensationRetrieved());
    }

    @ParameterizedTest
    @CsvSource({"0,false", "4,false", "16,false", "0,true", "4,true", "16,true"})
    void depositExceptionsReconcileAcceptedItemsWithoutVoiding(int amount, boolean linkage) {
        doAnswer(call -> {
            accept(call.getArgument(1), amount);
            if (linkage) throw new NoSuchMethodError("Injected deposit failure");
            throw new IllegalStateException("Injected deposit failure");
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        LineOperationUtil.grabItem(accessor, root, menu, TransportMode.VOID, 64);

        assertEquals(amount, accepted);
        assertEquals(16 - amount, remaining());
        assertEquals(16, accepted + remaining());
        if (remaining() > 0) assertTrue(original.isSimilar(source.get()));
        assertPassStopped();
        if (amount == 0) verify(menu, never()).replaceExistingItem(anyInt(), nullable(ItemStack.class));
        verify(root, never()).getItemStack0(any(), any());
    }

    @ParameterizedTest
    @CsvSource({"0,64", "4,64", "16,64", "4,4"})
    void cleanVoidOffersKeepIntentionalOverflowBehavior(int amount, int limit) {
        acceptNormally(amount);

        var result = NetworkTransferUtils.moveMenuSlotIntoNetworkAndVoidRemainder(root, accessor, menu, SLOT, limit);

        assertFalse(result.failed());
        assertEquals(amount, result.moved());
        assertEquals(amount, accepted);
        assertNull(source.get());
        verify(menu, times(1)).replaceExistingItem(SLOT, null);
        verify(menu, times(1)).markDirty();
        verify(root, never()).getItemStack0(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void sourceWriteFailureRestoresOriginalAndCompensatesWithoutASecondClear(boolean dirtyFailure) {
        acceptNormally(4);
        AtomicBoolean failOnce = new AtomicBoolean(true);
        if (dirtyFailure) {
            doAnswer(call -> {
                if (failOnce.getAndSet(false)) throw new IllegalStateException("Injected dirty failure");
                return null;
            }).when(menu).markDirty();
        } else {
            doAnswer(call -> {
                source.set(call.getArgument(1));
                if (failOnce.getAndSet(false)) throw new IllegalStateException("Injected write failure");
                return null;
            }).when(menu).replaceExistingItem(eq(SLOT), nullable(ItemStack.class));
        }

        LineOperationUtil.grabItem(accessor, root, menu, TransportMode.VOID, 64);

        assertEquals(original, source.get());
        assertEquals(0, accepted);
        assertPassStopped();
        verify(menu, times(1)).replaceExistingItem(SLOT, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void voidSourceWriteFailurePreservesNewlyProducedItems(boolean dirtyFailure) {
        doAnswer(call -> {
            accept(call.getArgument(1), 4);
            source.set(diamond(20));
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));
        AtomicBoolean failOnce = new AtomicBoolean(true);
        if (dirtyFailure) {
            doAnswer(call -> {
                if (failOnce.getAndSet(false)) throw new IllegalStateException("Injected dirty failure");
                return null;
            }).when(menu).markDirty();
        } else {
            doAnswer(call -> {
                source.set(call.getArgument(1));
                if (failOnce.getAndSet(false)) throw new IllegalStateException("Injected write-then-throw");
                return null;
            }).when(menu).replaceExistingItem(eq(SLOT), nullable(ItemStack.class));
        }

        var result = NetworkTransferUtils.moveMenuSlotIntoNetworkAndVoidRemainder(root, accessor, menu, SLOT, 64);

        assertTrue(result.failed());
        assertEquals(0, result.moved());
        assertEquals(0, accepted);
        // The verified pre-commit output was 16 original + 4 newly generated.
        assertEquals(20, remaining());
        assertEquals(4, TransferAudit.snapshot().depositCompensationRequested());
        assertEquals(4, TransferAudit.snapshot().depositCompensationRetrieved());
    }

    @Test
    void failedWriterMustNotRestoreOrClearAThirdPartyReplacement() {
        acceptNormally(4);
        ItemStack external = new ItemStack(Material.EMERALD, 9);
        doAnswer(call -> {
            source.set(external);
            throw new IllegalStateException("Replacement during source write");
        }).when(menu).replaceExistingItem(eq(SLOT), nullable(ItemStack.class));

        LineOperationUtil.grabItem(accessor, root, menu, TransportMode.VOID, 64);

        assertSame(external, source.get());
        assertEquals(0, accepted);
        assertPassStopped();
        verify(menu, times(1)).replaceExistingItem(eq(SLOT), nullable(ItemStack.class));
    }

    @Test
    void incompleteCompensationStillCannotAuthorizeVoid() {
        ItemStack external = new ItemStack(Material.EMERALD, 9);
        doAnswer(call -> {
            accept(call.getArgument(1), 4);
            source.set(external);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenReturn(null);

        LineOperationUtil.grabItem(accessor, root, menu, TransportMode.VOID, 64);

        assertSame(external, source.get());
        assertEquals(4, accepted); // A failed rollback is not falsely reported as conservation.
        assertEquals(0, TransferAudit.snapshot().depositCompensationRetrieved());
        assertEquals(1, NetworkTransferUtils.getHeldWithdrawalSourceCount(),
            "An incompletely compensated network must stay quarantined");
        assertPassStopped();
        verify(menu, never()).replaceExistingItem(anyInt(), nullable(ItemStack.class));
    }

    @Test
    void failureStatusSurvivesAPartiallyAcceptedOffer() {
        doAnswer(call -> {
            accept(call.getArgument(1), 4);
            throw new IllegalStateException("Injected partial deposit failure");
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        var result = NetworkTransferUtils.moveMenuSlotIntoNetworkAndVoidRemainder(root, accessor, menu, SLOT, 64);

        assertTrue(result.failed());
        assertEquals(4, result.moved());
        assertEquals(12, remaining());
        assertEquals(16, accepted + remaining());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void nonpositiveLimitsNeverVoidOrOffer(int limit) {
        var result = NetworkTransferUtils.moveMenuSlotIntoNetworkAndVoidRemainder(root, accessor, menu, SLOT, limit);
        assertFalse(result.failed());
        assertEquals(0, result.moved());
        assertSame(original, source.get());
        verify(root, never()).addItemStack0(any(), any());
        verify(menu, never()).replaceExistingItem(anyInt(), nullable(ItemStack.class));
    }

    @Test
    void ordinaryMenuTransfersDoNotVoidTheirRemainder() {
        acceptNormally(4);
        assertEquals(4, NetworkTransferUtils.moveMenuSlotIntoNetwork(root, accessor, menu, SLOT, 64));
        assertEquals(12, remaining());
        assertEquals(16, accepted + remaining());
    }

    private void assertPassStopped() {
        verify(root, times(1)).addItemStack0(eq(accessor), any(ItemStack.class));
        verify(menu, never()).getItemInSlot(NEXT_SLOT);
        verify(menu, never()).replaceExistingItem(eq(NEXT_SLOT), nullable(ItemStack.class));
        assertEquals(8, next.getAmount());
    }

    private void acceptNormally(int amount) {
        doAnswer(call -> {
            accept(call.getArgument(1), amount);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));
    }

    private void accept(ItemStack offered, int amount) {
        int moved = Math.min(amount, offered.getAmount());
        accepted += moved;
        offered.setAmount(offered.getAmount() - moved);
    }

    private int remaining() {
        ItemStack item = source.get();
        return item == null || item.getType().isAir() ? 0 : item.getAmount();
    }

    private static ItemStack diamond(int amount) {
        ItemStack item = new ItemStack(Material.DIAMOND, amount);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(NamespacedKey.fromString("oldaddon:owner"),
            PersistentDataType.STRING, "original-owner");
        item.setItemMeta(meta);
        return item;
    }
}

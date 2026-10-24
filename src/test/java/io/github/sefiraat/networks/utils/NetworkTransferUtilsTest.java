package io.github.sefiraat.networks.utils;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises public deposit helpers with real item metadata and failing storage/source boundaries. */
class NetworkTransferUtilsTest {
    private static final int SOURCE_SLOT = 2;
    private final Location accessor = new Location(null, 417, 64, 429);
    private final AtomicReference<ItemStack> source = new AtomicReference<>();
    private MockedStatic<Networks> networks;
    private NetworkRoot root;
    private BlockMenu menu;
    private PlayerInventory inventory;
    private Player player;
    private ItemStack original;
    private int accepted;
    private boolean failSourceCommit;

    enum SourceKind { MENU, INVENTORY, CURSOR, MAIN_HAND, REFERENCE }

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
        clearAccessHistory();
        TransferAudit.reset();

        original = customDiamond(32);
        source.set(original);
        root = mock(NetworkRoot.class);
        menu = mock(BlockMenu.class);
        inventory = mock(PlayerInventory.class);
        player = mock(Player.class);
        when(menu.getSize()).thenReturn(9);
        when(menu.getLocation()).thenReturn(accessor);
        when(menu.getItemInSlot(SOURCE_SLOT)).thenAnswer(call -> source.get());
        doAnswer(call -> {
            writeSource(call.getArgument(1));
            return null;
        }).when(menu).replaceExistingItem(eq(SOURCE_SLOT), nullable(ItemStack.class));
        when(inventory.getSize()).thenReturn(41);
        when(inventory.getHeldItemSlot()).thenReturn(SOURCE_SLOT);
        when(inventory.getItem(SOURCE_SLOT)).thenAnswer(call -> source.get());
        doAnswer(call -> {
            writeSource(call.getArgument(1));
            return null;
        }).when(inventory).setItem(eq(SOURCE_SLOT), nullable(ItemStack.class));
        when(player.getInventory()).thenReturn(inventory);
        when(player.getLocation()).thenReturn(accessor);
        when(player.getItemOnCursor()).thenAnswer(call -> source.get());
        doAnswer(call -> {
            writeSource(call.getArgument(0));
            return null;
        }).when(player).setItemOnCursor(any(ItemStack.class));
    }

    @AfterEach
    void tearDown() {
        try {
            clearAccessHistory();
            // A real root's write-then-throw circuit breaker must not leak
            // between independent parameterized test fixtures.
            NetworkTransferUtils.resetRuntimeSafety();
            TransferAudit.reset();
        } finally {
            try {
                if (networks != null) networks.close();
            } finally {
                MockBukkit.unmock();
            }
        }
    }

    static Stream<Arguments> failedDeposits() {
        return Arrays.stream(SourceKind.values()).flatMap(kind ->
            Stream.of(0, 7, 32).flatMap(amount ->
                Stream.of(false, true).map(linkageFailure -> Arguments.of(kind, amount, linkageFailure))));
    }

    @ParameterizedTest(name = "{0}: accepted={1}, linkageFailure={2}")
    @MethodSource("failedDeposits")
    void failedDepositCommitsExactlyWhatTheNetworkAccepted(
        SourceKind kind, int amount, boolean linkageFailure) {
        acceptThenFail(amount, linkageFailure);

        assertEquals(amount, transfer(kind, Integer.MAX_VALUE));

        assertConserved(32);
        assertEquals(32 - amount, remaining());
        if (amount == 0) {
            assertSame(original, source.get());
            verify(menu, never()).replaceExistingItem(anyInt(), any());
            verify(inventory, never()).setItem(anyInt(), any());
            verify(player, never()).setItemOnCursor(any());
        }
        if (kind == SourceKind.MENU && amount > 0) verify(menu).markDirty();
        TransferAudit.Snapshot audit = TransferAudit.snapshot();
        assertEquals(1, audit.depositAttempts());
        assertEquals(32, audit.depositOffered());
        assertEquals(amount, audit.depositCommitted());
        assertEquals(1, audit.failures());
        verify(root, never()).getItemStack0(any(), any());
    }

    @ParameterizedTest
    @EnumSource(value = SourceKind.class, names = "REFERENCE", mode = EnumSource.Mode.EXCLUDE)
    void failedDepositRespectsTheTransferLimit(SourceKind kind) {
        acceptThenFail(Integer.MAX_VALUE, false);

        assertEquals(11, transfer(kind, 11));

        assertConserved(32);
        assertEquals(21, remaining());
        assertEquals(11, TransferAudit.snapshot().depositOffered());
        assertEquals(11, TransferAudit.snapshot().depositCommitted());
    }

    static Stream<Arguments> externallyModifiedSources() {
        return Arrays.stream(SourceKind.values())
            .filter(kind -> kind != SourceKind.REFERENCE)
            .flatMap(kind -> Stream.of(false, true)
                .map(metadataOnly -> Arguments.of(kind, metadataOnly)));
    }

    @ParameterizedTest(name = "{0}: metadataOnly={1}")
    @MethodSource("externallyModifiedSources")
    void sourceChangedWhileDepositWasAcceptedIsNeverOverwritten(SourceKind kind, boolean metadataOnly) {
        final ItemStack external = metadataOnly ? customDiamond(32) : new ItemStack(Material.EMERALD, 16);
        if (metadataOnly) {
            var meta = external.getItemMeta();
            meta.getPersistentDataContainer().set(NamespacedKey.fromString("oldaddon:owner"),
                PersistentDataType.STRING, "another-owner");
            external.setItemMeta(meta);
        }

        doAnswer(call -> {
            accept(call.getArgument(1), 7);
            source.set(external);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            ItemRequest request = call.getArgument(1);
            assertEquals(7, request.getAmount());
            accepted -= 7;
            return customDiamond(7);
        });

        assertEquals(0, transfer(kind, Integer.MAX_VALUE));
        assertSame(external, source.get());
        assertEquals(0, accepted);
        verify(root).uncontrolAccessOutput(accessor);
        verify(menu, never()).replaceExistingItem(anyInt(), any());
        verify(inventory, never()).setItem(anyInt(), any());
        verify(player, never()).setItemOnCursor(any());
        assertEquals(7, TransferAudit.snapshot().depositCompensationRequested());
        assertEquals(7, TransferAudit.snapshot().depositCompensationRetrieved());
    }

    @Test
    void anInPlaceMutationOfALiveStackReferenceIsAlsoRejected() {
        doAnswer(call -> {
            accept(call.getArgument(1), 7);
            original.setAmount(19); // e.g. a third-party operation used the live source stack
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            accepted -= 7;
            return customDiamond(7);
        });

        assertEquals(0, transfer(SourceKind.REFERENCE, Integer.MAX_VALUE));
        assertSame(original, source.get());
        assertEquals(19, original.getAmount());
        assertEquals(0, accepted);
        assertEquals(7, TransferAudit.snapshot().depositCompensationRetrieved());
    }

    @Test
    void failedSourceWriterMustNotRestoreOverAThirdPartyStack() {
        doAnswer(call -> {
            source.set(new ItemStack(Material.EMERALD, 21));
            throw new IllegalStateException("External inventory replacement during the source write");
        }).when(inventory).setItem(eq(SOURCE_SLOT), nullable(ItemStack.class));
        doAnswer(call -> {
            accept(call.getArgument(1), 7);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            accepted -= 7;
            return customDiamond(7);
        });

        assertEquals(0, transfer(SourceKind.INVENTORY, Integer.MAX_VALUE));
        assertEquals(Material.EMERALD, source.get().getType());
        assertEquals(21, source.get().getAmount());
        assertEquals(0, accepted);
        verify(inventory, times(1)).setItem(eq(SOURCE_SLOT), nullable(ItemStack.class));
        assertEquals(7, TransferAudit.snapshot().depositCompensationRetrieved());
    }

    @Test
    void repeatedPartialFailuresCannotMintItems() {
        acceptThenFail(7, false);
        int moved = 0;
        for (int i = 0; i < 5; i++) {
            moved += transfer(SourceKind.INVENTORY, Integer.MAX_VALUE);
            assertConserved(32);
        }

        assertEquals(32, moved);
        assertEquals(0, remaining());
        assertEquals(0, transfer(SourceKind.INVENTORY, Integer.MAX_VALUE));
        assertEquals(5, TransferAudit.snapshot().depositAttempts());
        assertEquals(32, TransferAudit.snapshot().depositCommitted());
    }

    @ParameterizedTest
    @EnumSource(value = SourceKind.class, names = "REFERENCE", mode = EnumSource.Mode.EXCLUDE)
    void sourceCommitFailureStillCompensatesTheAcceptedDeposit(SourceKind kind) {
        acceptThenFail(7, false);
        failSourceCommit = true;
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            ItemRequest request = call.getArgument(1);
            assertEquals(7, request.getAmount());
            assertTrue(original.isSimilar(request.getItemStack()));
            accepted -= request.getAmount();
            return customDiamond(request.getAmount());
        });

        assertEquals(0, transfer(kind, Integer.MAX_VALUE));

        assertConserved(32);
        assertEquals(32, remaining());
        assertEquals(0, accepted);
        verify(root).uncontrolAccessOutput(accessor);
        TransferAudit.Snapshot audit = TransferAudit.snapshot();
        assertEquals(7, audit.depositCompensationRequested());
        assertEquals(7, audit.depositCompensationRetrieved());
        assertEquals(0, audit.uncompensatedDepositItems());
        assertEquals(2, audit.failures());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void actualRootKeepsEarlierStorageAcceptanceWhenTheNextStorageFails(boolean linkageFailure) {
        NetworkRoot routedRoot = spy(new NetworkRoot(accessor, NodeType.CONTROLLER, 100));
        BarrelIdentity first = mock(BarrelIdentity.class);
        BarrelIdentity broken = mock(BarrelIdentity.class);
        doReturn(new LinkedHashSet<>(List.of(first, broken))).when(routedRoot).getInputAbleBarrels();
        when(first.getLocation()).thenReturn(new Location(null, 418, 64, 429));
        when(first.canAccept(any(ItemStack.class))).thenReturn(true);

        // The first destination has room for seven items; use the real menu insertion implementation.
        ItemStack stored = customDiamond(57);
        BlockMenu destination = mock(BlockMenu.class);
        when(destination.getSize()).thenReturn(9);
        when(destination.getItemInSlot(0)).thenReturn(stored);
        doAnswer(call -> {
            ItemStack incoming = call.getArgument(0);
            BlockMenuUtil.pushItem(destination, incoming, 0);
            return null;
        }).when(first).depositItemStack(any(ItemStack.class));
        when(broken.canAccept(any(ItemStack.class))).thenAnswer(call -> {
            throw storageFailure(linkageFailure);
        });

        assertEquals(7, NetworkTransferUtils.moveInventorySlotIntoNetwork(
            routedRoot, accessor, inventory, SOURCE_SLOT));

        assertEquals(25, remaining());
        assertEquals(64, stored.getAmount());
        assertEquals(89, stored.getAmount() + remaining());
        assertTrue(original.isSimilar(stored));
        assertTrue(original.isSimilar(source.get()));
        // A successful occupied-slot merge is explicitly written back.
        // DirtyChestMenu.replaceExistingItem(..., false) already records the
        // mutation; the helper must not issue an extra markDirty() call.
        verify(destination).replaceExistingItem(eq(0), same(stored), eq(false));
        verify(destination, never()).markDirty();
        verify(broken).canAccept(any(ItemStack.class));
        assertEquals(7, TransferAudit.snapshot().depositCommitted());
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void lineGrabberNeverRedepositsIntoItsOwnSourceBarrel() {
        NetworkRoot routedRoot = spy(new NetworkRoot(accessor, NodeType.CONTROLLER, 100));
        BarrelIdentity origin = mock(BarrelIdentity.class);
        BarrelIdentity destination = mock(BarrelIdentity.class);
        doReturn(new LinkedHashSet<>(List.of(origin, destination)))
            .when(routedRoot).getInputAbleBarrels();

        // Yaw and pitch are intentionally different: routing compares block coordinates.
        when(origin.getLocation()).thenReturn(new Location(null, 417, 64, 429, 45, 30));
        // A different Y block in the same vertical column is a legitimate destination.
        when(destination.getLocation()).thenReturn(new Location(null, 417, 65, 429));
        when(destination.canAccept(any(ItemStack.class))).thenReturn(true);
        doAnswer(call -> {
            accept(call.getArgument(0), 2);
            return null;
        }).when(destination).depositItemStack(any(ItemStack.class));

        assertEquals(2, NetworkTransferUtils.moveMenuSlotIntoNetwork(
            routedRoot, accessor, menu, SOURCE_SLOT, 2));
        assertConserved(32);
        assertEquals(30, remaining());
        verify(origin, never()).canAccept(any(ItemStack.class));
        verify(origin, never()).depositItemStack(any(ItemStack.class));
        verify(destination).depositItemStack(any(ItemStack.class));
        assertEquals(0, TransferAudit.snapshot().failures());
        assertEquals(0, TransferAudit.snapshot().depositCompensationRequested());
    }

    @Test
    void aNetworkCellCannotReceiveTheOfferFromItsOwnLiveSourceMenu() {
        NetworkRoot routedRoot = spy(new NetworkRoot(accessor, NodeType.CONTROLLER, 100));
        Location sameBlock = new Location(null, 417, 64, 429, 90, 0);
        routedRoot.getCells().add(sameBlock);
        try (MockedStatic<StorageCacheUtils> storage = mockStatic(StorageCacheUtils.class)) {
            storage.when(() -> StorageCacheUtils.getMenu(sameBlock)).thenReturn(menu);

            assertEquals(0, NetworkTransferUtils.moveMenuSlotIntoNetwork(
                routedRoot, accessor, menu, SOURCE_SLOT, 2));
            storage.verify(() -> StorageCacheUtils.getMenu(sameBlock), never());
        }

        assertSame(original, source.get());
        assertEquals(32, remaining());
        assertEquals(0, TransferAudit.snapshot().failures());
    }

    @ParameterizedTest
    @EnumSource(value = SourceKind.class, names = {"MENU", "INVENTORY", "CURSOR", "MAIN_HAND"})
    void silentSourceSetterRejectionCannotCreditNetworkAndKeepOriginalItems(SourceKind kind) {
        doAnswer(call -> {
            accept(call.getArgument(1), 7);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            ItemRequest request = call.getArgument(1);
            int recovered = Math.min(accepted, request.getAmount());
            accepted -= recovered;
            return recovered == 0 ? null : customDiamond(recovered);
        });

        if (kind == SourceKind.MENU) {
            doNothing().when(menu).replaceExistingItem(eq(SOURCE_SLOT), nullable(ItemStack.class));
        } else if (kind == SourceKind.CURSOR) {
            doNothing().when(player).setItemOnCursor(any(ItemStack.class));
        } else {
            doNothing().when(inventory).setItem(eq(SOURCE_SLOT), nullable(ItemStack.class));
        }

        assertEquals(0, transfer(kind, 64));
        assertEquals(32, remaining(), "Rejected source write must not remove unrelated items");
        assertEquals(0, accepted, "Accepted items must be compensated if source deduction did not occur");
        assertEquals(7, TransferAudit.snapshot().depositCompensationRequested());
        assertEquals(7, TransferAudit.snapshot().depositCompensationRetrieved());
        assertConserved(32);
    }

    @Test
    void successfulPartialDepositRetainsItsExistingBehavior() {
        doAnswer(call -> {
            accept(call.getArgument(1), 7);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        assertEquals(7, transfer(SourceKind.INVENTORY, Integer.MAX_VALUE));

        assertConserved(32);
        assertEquals(25, remaining());
        assertEquals(0, TransferAudit.snapshot().failures());
    }

    private void acceptThenFail(int amount, boolean linkageFailure) {
        doAnswer(call -> {
            accept(call.getArgument(1), amount);
            throw storageFailure(linkageFailure);
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));
    }

    private void accept(ItemStack offered, int amount) {
        assertNotSame(source.get(), offered, "The network must receive a detached offer");
        assertTrue(original.isSimilar(offered), "The offer must retain custom metadata");
        int moved = Math.min(amount, offered.getAmount());
        accepted += moved;
        offered.setAmount(offered.getAmount() - moved);
    }

    private static Throwable storageFailure(boolean linkageFailure) {
        return linkageFailure
            ? new NoSuchMethodError("Simulated incompatible storage integration")
            : new IllegalStateException("Simulated storage failure");
    }

    private int transfer(SourceKind kind, int limit) {
        return switch (kind) {
            case MENU -> NetworkTransferUtils.moveMenuSlotIntoNetwork(root, accessor, menu, SOURCE_SLOT, limit);
            case INVENTORY -> NetworkTransferUtils.moveInventorySlotIntoNetwork(
                root, accessor, inventory, SOURCE_SLOT, limit);
            case CURSOR -> NetworkTransferUtils.movePlayerCursorIntoNetwork(root, accessor, player, limit);
            case MAIN_HAND -> NetworkTransferUtils.movePlayerMainHandIntoNetwork(root, accessor, player, limit);
            case REFERENCE -> NetworkTransferUtils.moveStackReferenceIntoNetwork(root, accessor, source.get());
        };
    }

    private void writeSource(ItemStack stack) {
        source.set(stack);
        if (failSourceCommit) {
            failSourceCommit = false;
            throw new IllegalStateException("Simulated source failure after writing");
        }
    }

    private int remaining() {
        ItemStack stack = source.get();
        return stack == null || stack.getType() == Material.AIR ? 0 : stack.getAmount();
    }

    private void assertConserved(int initialAmount) {
        assertEquals(initialAmount, accepted + remaining(), "Source plus network must conserve every item");
        if (remaining() > 0) assertTrue(original.isSimilar(source.get()), "Source metadata must be retained");
    }

    private void clearAccessHistory() {
        NetworkRoot.removePersistentAccessHistory(accessor);
        NetworkRoot.removeCountObservingAccessHistory(accessor);
        NetworkRoot.transportMissInputHistory.remove(accessor);
        NetworkRoot.controlledAccessInputHistory.remove(accessor);
    }

    private static ItemStack customDiamond(int amount) {
        ItemStack item = new ItemStack(Material.DIAMOND, amount);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Owner's diamond"));
        meta.lore(List.of(Component.text("Retain this item's identity")));
        meta.getPersistentDataContainer().set(NamespacedKey.fromString("oldaddon:owner"),
            PersistentDataType.STRING, "retained-owner");
        item.setItemMeta(meta);
        return item;
    }
}

package io.github.sefiraat.networks.utils;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.kyori.adventure.text.Component;
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

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises the real menu deposit boundary with template-rebuilding addon stacks.
 * MockBukkit 4.110.0 drops the modern model component while copying ItemMeta. Its
 * supported scalar model field drives this fixture; native modern components are
 * checked separately by CrafterManagerDisplayProbe on Paper and Purpur.
 */
@SuppressWarnings("deprecation") // Test-only scalar model fixture for MockBukkit 4.110.0.
class NetworkMenuSourceSnapshotTest {
    private static final int SLOT = 2;
    private static final NamespacedKey OWNER = NamespacedKey.fromString("test:owner");
    private final Location accessor = new Location(null, -1314, 66, 817);
    private final AtomicReference<ItemStack> source = new AtomicReference<>();
    private MockedStatic<Networks> networks;
    private NetworkRoot root;
    private BlockMenu menu;
    private ItemStack expected;
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

        expected = dust(16);
        source.set(new RebuildingDustStack(expected));
        root = mock(NetworkRoot.class);
        menu = mock(BlockMenu.class);
        when(menu.getSize()).thenReturn(9);
        when(menu.getLocation()).thenReturn(accessor);
        when(menu.getItemInSlot(SLOT)).thenAnswer(call -> source.get());
        doAnswer(call -> {
            source.set(call.getArgument(1));
            return null;
        }).when(menu).replaceExistingItem(eq(SLOT), nullable(ItemStack.class));
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            ItemRequest request = call.getArgument(1);
            int recovered = Math.min(accepted, request.getAmount());
            accepted -= recovered;
            return recovered == 0 ? null : dust(recovered);
        });
    }

    @AfterEach
    void tearDown() {
        try {
            if (networks != null) networks.close();
        } finally {
            TransferAudit.reset();
            MockBukkit.unmock();
        }
    }

    @Test
    void rebuildingCloneCanMismatchWithoutTheSourceChanging() {
        ItemStack live = source.get();
        ItemStack rebuilt = live.clone();
        assertEquals(expected, live);
        assertFalse(rebuilt.isSimilar(live));
        assertEquals(7002, rebuilt.getItemMeta().getCustomModelData());
        assertOriginalMetadata(live);
        assertEquals(16, live.getAmount());
    }

    @ParameterizedTest
    @CsvSource({"false,0", "false,4", "false,16", "true,0", "true,4", "true,16"})
    void unchangedDustKeepsItsExistingMetadataAndTransferSemantics(boolean voidMode, int amount) {
        ItemStack live = source.get();
        acceptNormally(amount);

        int moved;
        if (voidMode) {
            var result = NetworkTransferUtils.moveMenuSlotIntoNetworkAndVoidRemainder(root, accessor, menu, SLOT, 64);
            assertFalse(result.failed());
            moved = result.moved();
        } else {
            moved = NetworkTransferUtils.moveMenuSlotIntoNetwork(root, accessor, menu, SLOT, 64);
        }

        assertEquals(amount, moved);
        assertEquals(amount, accepted);
        assertEquals(voidMode ? 0 : 16 - amount, remaining());
        assertEquals(expected, live, "A detached offer must not mutate the original addon object");
        if (remaining() > 0) assertOriginalMetadata(source.get());
        verify(root, never()).getItemStack0(any(), any());
        assertEquals(0, TransferAudit.snapshot().depositCompensationRequested());
    }

    @Test
    void aliasingCloneCannotMakeTheNetworkMutateTheLiveSource() {
        source.set(new AliasingDustStack(expected));
        acceptNormally(4);
        assertEquals(4, NetworkTransferUtils.moveMenuSlotIntoNetwork(root, accessor, menu, SLOT, 64));
        assertEquals(12, remaining());
        assertEquals(16, accepted + remaining());
        assertOriginalMetadata(source.get());
        verify(root, never()).getItemStack0(any(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void realMetadataReplacementStillRejectsAndCompensates(boolean voidMode) {
        ItemStack external = dust(16);
        var meta = external.getItemMeta();
        meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "different-owner");
        external.setItemMeta(meta);
        doAnswer(call -> {
            accept(call.getArgument(1), 4);
            source.set(external);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        if (voidMode) {
            var result = NetworkTransferUtils.moveMenuSlotIntoNetworkAndVoidRemainder(root, accessor, menu, SLOT, 64);
            assertTrue(result.failed());
            assertEquals(0, result.moved());
        } else {
            assertEquals(0, NetworkTransferUtils.moveMenuSlotIntoNetwork(root, accessor, menu, SLOT, 64));
        }
        assertSame(external, source.get());
        assertEquals(0, accepted);
        assertEquals(4, TransferAudit.snapshot().depositCompensationRetrieved());
        verify(menu, never()).replaceExistingItem(anyInt(), nullable(ItemStack.class));
    }

    @ParameterizedTest
    @CsvSource({"false,0", "false,4", "false,16", "true,0", "true,4", "true,16"})
    void producingMachineCanGrowIdenticalOutputDuringDeposit(boolean voidMode, int amount) {
        doAnswer(call -> {
            accept(call.getArgument(1), amount);
            // Simulates an IE generator producing four new items during the transfer.
            source.get().setAmount(20);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        int moved;
        if (voidMode) {
            var result = NetworkTransferUtils.moveMenuSlotIntoNetworkAndVoidRemainder(root, accessor, menu, SLOT, 64);
            assertFalse(result.failed());
            moved = result.moved();
        } else {
            moved = NetworkTransferUtils.moveMenuSlotIntoNetwork(root, accessor, menu, SLOT, 64);
        }

        assertEquals(amount, moved);
        assertEquals(amount, accepted);
        // VOID may discard only the original unaccepted output, never the extra four.
        assertEquals(voidMode ? 4 : 20 - amount, remaining());
        assertEquals(20, remaining() + accepted + (voidMode ? 16 - amount : 0));
        assertOriginalMetadata(source.get());
        if (amount > 0 || voidMode) verify(menu).markDirty();
        else verify(menu, never()).markDirty();
        assertEquals(0, TransferAudit.snapshot().depositCompensationRequested());
        verify(root, never()).getItemStack0(any(), any());
    }

    @Test
    void partialNetworkFailureWithProductionDoesNotVoidNewOutput() {
        doAnswer(call -> {
            accept(call.getArgument(1), 4);
            source.get().setAmount(20);
            throw new IllegalStateException("Injected partial destination failure");
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        var result = NetworkTransferUtils.moveMenuSlotIntoNetworkAndVoidRemainder(root, accessor, menu, SLOT, 64);

        assertTrue(result.failed());
        assertEquals(4, result.moved());
        assertEquals(4, accepted);
        assertEquals(16, remaining());
        assertEquals(20, remaining() + accepted);
        assertOriginalMetadata(source.get());
        assertEquals(0, TransferAudit.snapshot().depositCompensationRequested());
    }

    @Test
    void failedPartialDepositKeepsTheOriginalMetadataOnItsRemainder() {
        doAnswer(call -> {
            accept(call.getArgument(1), 4);
            throw new IllegalStateException("Injected destination failure");
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));

        var result = NetworkTransferUtils.moveMenuSlotIntoNetworkAndVoidRemainder(root, accessor, menu, SLOT, 64);

        assertTrue(result.failed());
        assertEquals(4, result.moved());
        assertEquals(16, accepted + remaining());
        assertOriginalMetadata(source.get());
        verify(menu, never()).replaceExistingItem(SLOT, null);
        verify(root, never()).getItemStack0(any(), any());
    }

    @Test
    void failedSourceCommitRestoresExistingMetadataInsteadOfReapplyingModels() {
        acceptNormally(4);
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(call -> {
            if (first.getAndSet(false)) throw new IllegalStateException("Injected dirty failure");
            return null;
        }).when(menu).markDirty();

        var result = NetworkTransferUtils.moveMenuSlotIntoNetworkAndVoidRemainder(root, accessor, menu, SLOT, 64);

        assertTrue(result.failed());
        assertEquals(expected, source.get());
        assertOriginalMetadata(source.get());
        assertEquals(0, accepted);
        verify(menu, times(1)).replaceExistingItem(SLOT, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void snapshotFailureStopsBeforeTheNetworkCanAcceptAnything(boolean linkage) {
        ItemStack live = new RebuildingDustStack(expected) {
            @Override
            public ItemStack clone() {
                if (linkage) throw new NoSuchMethodError("Injected clone failure");
                throw new IllegalStateException("Injected clone failure");
            }
        };
        source.set(live);

        var result = NetworkTransferUtils.moveMenuSlotIntoNetworkAndVoidRemainder(root, accessor, menu, SLOT, 64);

        assertTrue(result.failed());
        assertEquals(0, result.moved());
        assertSame(live, source.get());
        assertEquals(expected, live);
        verifyNoInteractions(root);
        verify(menu, never()).replaceExistingItem(anyInt(), nullable(ItemStack.class));
    }

    private void acceptNormally(int amount) {
        doAnswer(call -> {
            accept(call.getArgument(1), amount);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));
    }

    private void accept(ItemStack offered, int amount) {
        assertOriginalMetadata(offered);
        assertEquals(16, source.get().getAmount(), "The live source cannot be decremented by the offer");
        int moved = Math.min(amount, offered.getAmount());
        accepted += moved;
        offered.setAmount(offered.getAmount() - moved);
    }

    private void assertOriginalMetadata(ItemStack item) {
        assertNotNull(item);
        assertEquals(expected.getType(), item.getType());
        var actual = item.getItemMeta();
        assertEquals(expected.getItemMeta(), actual);
        assertEquals(7001, actual.getCustomModelData());
        assertEquals(Component.text("Extractor dust fixture"), actual.displayName());
        assertEquals(List.of(Component.text("Existing item metadata")), actual.lore());
        assertEquals("original-owner", actual.getPersistentDataContainer().get(OWNER, PersistentDataType.STRING));
        assertEquals(Long.valueOf(9876543210L), actual.getPersistentDataContainer().get(
            NamespacedKey.fromString("test:counter"), PersistentDataType.LONG));
        assertArrayEquals(new byte[]{3, 1, 4}, actual.getPersistentDataContainer().get(
            NamespacedKey.fromString("test:payload"), PersistentDataType.BYTE_ARRAY));
    }

    private int remaining() {
        ItemStack item = source.get();
        return item == null || item.getType().isAir() ? 0 : item.getAmount();
    }

    private static ItemStack dust(int amount) {
        ItemStack item = new ItemStack(Material.GUNPOWDER, amount);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Extractor dust fixture"));
        meta.lore(List.of(Component.text("Existing item metadata")));
        meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "original-owner");
        meta.getPersistentDataContainer().set(NamespacedKey.fromString("test:counter"),
            PersistentDataType.LONG, 9876543210L);
        meta.getPersistentDataContainer().set(NamespacedKey.fromString("test:payload"),
            PersistentDataType.BYTE_ARRAY, new byte[]{3, 1, 4});
        meta.setCustomModelData(7001);
        item.setItemMeta(meta);
        return item;
    }

    /** Models template reconstruction; this is not a claim about a particular installed IE2 version. */
    private static class RebuildingDustStack extends ItemStack {
        RebuildingDustStack(ItemStack template) {
            super(template.getType(), template.getAmount());
            setItemMeta(template.getItemMeta());
        }

        @Override
        public ItemStack clone() {
            ItemStack rebuilt = new ItemStack(getType(), getAmount());
            var meta = getItemMeta();
            meta.setCustomModelData(7002);
            rebuilt.setItemMeta(meta);
            return rebuilt;
        }
    }

    private static final class AliasingDustStack extends RebuildingDustStack {
        AliasingDustStack(ItemStack template) {
            super(template);
        }

        @Override
        public ItemStack clone() {
            return this;
        }
    }
}

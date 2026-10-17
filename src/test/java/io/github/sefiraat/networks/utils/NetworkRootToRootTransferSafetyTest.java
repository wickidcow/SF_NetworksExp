package io.github.sefiraat.networks.utils;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression cases for target storage write-then-throw across independent network roots. */
class NetworkRootToRootTransferSafetyTest {
    private final Location sourceAccessor = new Location(null, 101, 64, 300);
    private final Location secondaryAccessor = new Location(null, 107, 64, 300);
    private final Location targetAccessor = new Location(null, 201, 64, 400);
    private final Location controller = new Location(null, 100, 64, 300);
    private MockedStatic<Networks> networks;
    private NetworkRoot source;
    private NetworkRoot target;

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
        source = mock(NetworkRoot.class);
        target = mock(NetworkRoot.class);
        when(source.getNodePosition()).thenReturn(controller);
        when(source.getItemStack0(eq(sourceAccessor), any(ItemRequest.class)))
            .thenAnswer(call -> diamond(Math.min(4, ((ItemRequest) call.getArgument(1)).getAmount())));
        TransferAudit.reset();
        NetworkTransferUtils.resetRuntimeSafety();
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
    @ValueSource(ints = {0, 2, 4})
    void targetWriteThenThrowNeverRefundsPotentiallyAcceptedItems(int accepted) {
        doAnswer(call -> {
            ItemStack offered = call.getArgument(1);
            // Storage adapters may accept items WITHOUT adjusting the offered amount.
            if (accepted > 0 && accepted < 4) offered.setAmount(4 - accepted);
            if (accepted == 4) offered.setAmount(0);
            throw new IllegalStateException("Injected target write-after-accept failure");
        }).when(target).addItemStack0(eq(targetAccessor), any(ItemStack.class));

        assertEquals(0, transfer());
        assertEquals(1, NetworkTransferUtils.getHeldWithdrawalSourceCount());
        assertTrue(RootRollbackSafetyHold.isHeld(source, secondaryAccessor));
        assertEquals(0, TransferAudit.snapshot().safetyDropped());
        verify(source, never()).addItemStack0(any(), any());

        // A different accessor of the very same controller must also fail closed.
        assertEquals(0, NetworkTransferUtils.moveNetworkItemBetweenRoots(
            source, secondaryAccessor, target, targetAccessor, diamond(1), 4));
        verify(source, never()).getItemStack0(eq(secondaryAccessor), any(ItemRequest.class));
    }

    @Test
    void targetIdentityCorruptionAfterNormalReturnCannotAuthorizeARefund() {
        doAnswer(call -> {
            ItemStack offered = call.getArgument(1);
            offered.setAmount(2);
            offered.setType(Material.EMERALD);
            return null;
        }).when(target).addItemStack0(eq(targetAccessor), any(ItemStack.class));

        assertEquals(0, transfer());
        assertEquals(1, NetworkTransferUtils.getHeldWithdrawalSourceCount());
        assertEquals(0, TransferAudit.snapshot().safetyDropped());
        verify(source, never()).addItemStack0(any(), any());
    }

    @Test
    void completedPartialTransferReturnsOnlyVerifiedRemainder() {
        doAnswer(call -> {
            ItemStack offered = call.getArgument(1);
            offered.setAmount(2);
            return null;
        }).when(target).addItemStack0(eq(targetAccessor), any(ItemStack.class));
        doAnswer(call -> {
            ItemStack refund = call.getArgument(1);
            assertEquals(2, refund.getAmount());
            refund.setAmount(0);
            return null;
        }).when(source).addItemStack0(eq(sourceAccessor), any(ItemStack.class));

        assertEquals(2, transfer());
        assertEquals(0, NetworkTransferUtils.getHeldWithdrawalSourceCount());
        assertEquals(2, TransferAudit.snapshot().rollbackReinserted());
        assertEquals(0, TransferAudit.snapshot().safetyDropped());
    }

    @Test
    void targetAccessResetFailsBeforeTargetWriteAndAllowsSafeSourceRefund() {
        doThrow(new IllegalStateException("Target rate-limit reset failed"))
            .when(target).uncontrolAccessInput(targetAccessor);
        doAnswer(call -> {
            ItemStack refund = call.getArgument(1);
            refund.setAmount(0);
            return null;
        }).when(source).addItemStack0(eq(sourceAccessor), any(ItemStack.class));

        assertEquals(0, transfer());
        verify(target, never()).addItemStack0(any(), any());
        assertEquals(4, TransferAudit.snapshot().rollbackReinserted());
        assertEquals(0, NetworkTransferUtils.getHeldWithdrawalSourceCount());
    }

    private int transfer() {
        return NetworkTransferUtils.moveNetworkItemBetweenRoots(
            source, sourceAccessor, target, targetAccessor, diamond(1), 4);
    }

    private static ItemStack diamond(int amount) {
        return new ItemStack(Material.DIAMOND, amount);
    }
}

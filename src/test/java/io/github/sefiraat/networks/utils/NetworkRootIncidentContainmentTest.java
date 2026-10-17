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
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** A root-wide hold must also protect consumers which bypass NetworkTransferUtils. */
@SuppressWarnings("removal")
class NetworkRootIncidentContainmentTest {
    private final Location controller = new Location(null, 300, 64, 500);
    private final Location first = new Location(null, 301, 64, 500);
    private final Location second = new Location(null, 302, 64, 500);
    private MockedStatic<Networks> networks;
    private NetworkRoot root;

    @BeforeEach
    void setUp() {
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
        root = mock(NetworkRoot.class, CALLS_REAL_METHODS);
        when(root.getNodePosition()).thenReturn(controller);
        NetworkTransferUtils.resetRuntimeSafety();
        TransferAudit.reset();
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

    @Test
    void unverifiedStorageExceptionBlocksAllDirectAndLegacyEntrypoints() {
        NetworkTransferUtils.quarantineUnverifiedNetworkRoot(
            root, first, Material.DIAMOND, 8,
            new IllegalStateException("Injected adapter write-then-throw"));

        assertEquals(1, NetworkTransferUtils.getHeldWithdrawalSourceCount());
        assertTrue(NetworkTransferUtils.isNetworkRootHeld(root, second));

        ItemRequest request = new ItemRequest(new ItemStack(Material.DIAMOND, 1), 8);
        assertNull(root.getItemStack0(second, request));
        ItemStack incoming = new ItemStack(Material.DIAMOND, 8);
        root.addItemStack0(second, incoming);
        assertEquals(8, incoming.getAmount(), "A quarantined deposit must not consume the offer");

        assertNull(root.getItemStack(request));
        root.addItemStack(incoming);
        assertEquals(8, incoming.getAmount());
        verify(root, never()).allowAccessOutput(any());
        verify(root, never()).allowAccessInput(any());

        NetworkRoot replacement = mock(NetworkRoot.class, CALLS_REAL_METHODS);
        when(replacement.getNodePosition()).thenReturn(controller);
        assertNull(replacement.getItemStack0(second, request));
        verify(replacement, never()).allowAccessOutput(any());
    }
}

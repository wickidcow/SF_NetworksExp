package io.github.sefiraat.networks.network.barrel;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.utils.StackUtils;
import net.kyori.adventure.text.Component;
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

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Regression tests for stale barrel snapshots, without loading Slimefun's live
 * QuantumStorage plugin registry during a pure unit test.
 */
class NetworkStorageIdentityTest {
    private final Location location = new Location(null, 417, 64, 429);
    private MockedStatic<Networks> networks;
    private QuantumCache cache;

    @BeforeEach
    void setUp() throws ClassNotFoundException {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        Networks plugin = mock(Networks.class);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getInstance).thenReturn(plugin);
        when(plugin.getName()).thenReturn("Networks");
        when(plugin.namespace()).thenReturn("networks");
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        Class.forName(StackUtils.class.getName());
        Class.forName(NetworkRoot.class.getName());
        cache = new QuantumCache(customDiamond(1, "original"), 12L, 64L, false, false);
    }

    @AfterEach
    void tearDown() {
        try {
            NetworkRoot.removePersistentAccessHistory(location);
            NetworkRoot.removeCountObservingAccessHistory(location);
        } finally {
            try {
                if (networks != null) networks.close();
            } finally {
                MockBukkit.unmock();
            }
        }
    }

    @Test
    void reassignedQuantumIdentityCannotFulfillOldSnapshotRequest() {
        cache.setAmount(0);
        cache.setItemStack(new ItemStack(Material.EMERALD));
        cache.setAmount(8);
        AtomicInteger withdrawals = new AtomicInteger();

        ItemStack fetched = NetworkStorage.withdrawIfMatching(
            cache, new ItemRequest(customDiamond(1, "original"), 5), () -> {
                withdrawals.incrementAndGet();
                return cache.withdrawItem(5);
            });

        assertNull(fetched);
        assertEquals(0, withdrawals.get());
        assertEquals(8L, cache.getAmountLong(), "Wrong-item withdrawals must never reduce storage");
        assertEquals(Material.EMERALD, cache.getItemStack().getType());
    }

    @Test
    void itemMetadataMustMatchLiveStorageBeforeWithdrawal() {
        cache.setItemStack(customDiamond(1, "different"));
        AtomicInteger withdrawals = new AtomicInteger();

        assertNull(NetworkStorage.withdrawIfMatching(
            cache, new ItemRequest(customDiamond(1, "original"), 3), () -> {
                withdrawals.incrementAndGet();
                return cache.withdrawItem(3);
            }));

        assertEquals(0, withdrawals.get());
        assertEquals(12L, cache.getAmountLong());
    }

    @Test
    void matchingQuantumWithdrawalHoldsCacheMonitorUntilComplete() {
        AtomicBoolean held = new AtomicBoolean();

        ItemStack fetched = NetworkStorage.withdrawIfMatching(
            cache, new ItemRequest(customDiamond(1, "original"), 4), () -> {
                held.set(Thread.holdsLock(cache));
                return cache.withdrawItem(4);
            });

        assertNotNull(fetched);
        assertEquals(4, fetched.getAmount());
        assertEquals(8L, cache.getAmountLong());
        assertTrue(held.get(), "The live template and withdrawal must share one monitor");
        assertEquals(customDiamond(1, "original").getItemMeta(), fetched.getItemMeta());
    }

    @Test
    void invalidAndUnassignedQuantumRequestsNeverReachStorage() {
        AtomicInteger withdrawals = new AtomicInteger();
        java.util.function.Supplier<ItemStack> withdraw = () -> {
            withdrawals.incrementAndGet();
            return cache.withdrawItem(1);
        };

        assertNull(NetworkStorage.withdrawIfMatching(
            cache, new ItemRequest(customDiamond(1, "original"), 0), withdraw));
        cache.setAmount(0);
        cache.setItemStack(null);
        assertNull(NetworkStorage.withdrawIfMatching(
            cache, new ItemRequest(customDiamond(1, "original"), 1), withdraw));
        assertEquals(0, withdrawals.get());
    }

    @Test
    @SuppressWarnings("deprecation")
    void networkRootRejectsMismatchedResultInLegacyAndAccessorPaths() {
        NetworkRoot root = spy(new NetworkRoot(location, NodeType.CONTROLLER, 100));
        BarrelIdentity barrel = mock(BarrelIdentity.class);
        when(barrel.getLocation()).thenReturn(location.clone().add(1, 0, 0));
        when(barrel.getItemStack()).thenReturn(new ItemStack(Material.DIAMOND));
        when(barrel.requestItem(any(ItemRequest.class))).thenReturn(new ItemStack(Material.EMERALD, 3));
        doReturn(Set.of(barrel)).when(root).getOutputAbleBarrels();
        doReturn(Map.of()).when(root).getOutputAbleCargoStorageUnitDatas();

        assertNull(root.getItemStack(new ItemRequest(new ItemStack(Material.DIAMOND), 3)));
        assertNull(root.getItemStack0(location, new ItemRequest(new ItemStack(Material.DIAMOND), 3)));
        verify(barrel, times(2)).requestItem(any(ItemRequest.class));
    }

    @Test
    void cachedAccessHistoryAlsoRejectsMismatchedResult() {
        Location storageLocation = location.clone().add(1, 0, 0);
        NetworkRoot root = spy(new NetworkRoot(location, NodeType.CONTROLLER, 100));
        BarrelIdentity barrel = mock(BarrelIdentity.class);
        when(barrel.getItemStack()).thenReturn(new ItemStack(Material.DIAMOND));
        when(barrel.requestItem(any(ItemRequest.class))).thenReturn(new ItemStack(Material.EMERALD, 3));
        doReturn(barrel).when(root).accessOutputAbleBarrel(storageLocation);
        doReturn(Set.of()).when(root).getOutputAbleBarrels();
        doReturn(Map.of()).when(root).getOutputAbleCargoStorageUnitDatas();
        NetworkRoot.addPersistentAccessHistory(location, storageLocation);

        assertNull(root.getItemStack0(location, new ItemRequest(new ItemStack(Material.DIAMOND), 3)));
        verify(barrel).requestItem(any(ItemRequest.class));
    }

    private static ItemStack customDiamond(int amount, String label) {
        ItemStack item = new ItemStack(Material.DIAMOND, amount);
        var meta = item.getItemMeta();
        meta.displayName(Component.text("Owner's diamond"));
        meta.getPersistentDataContainer().set(NamespacedKey.fromString("oldaddon:owner"),
            PersistentDataType.STRING, label);
        item.setItemMeta(meta);
        return item;
    }
}

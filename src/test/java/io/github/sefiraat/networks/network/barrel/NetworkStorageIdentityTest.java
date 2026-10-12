package io.github.sefiraat.networks.network.barrel;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.sefiraat.networks.utils.StackUtils;
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
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression tests for a stale network snapshot versus the live storage identity. */
class NetworkStorageIdentityTest {
    private final Location location = new Location(null, 417, 64, 429);
    private MockedStatic<Networks> networks;
    private MockedStatic<StorageCacheUtils> storage;
    private MockedStatic<NetworkQuantumStorage> quantum;
    private BlockMenu menu;
    private QuantumCache cache;
    private NetworkStorage snapshot;

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

        menu = mock(BlockMenu.class);
        when(menu.getLocation()).thenReturn(location);
        storage = mockStatic(StorageCacheUtils.class);
        storage.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(menu);
        quantum = mockStatic(NetworkQuantumStorage.class);
        cache = new QuantumCache(customDiamond(1, "original"), 12L, 64L, false, false);
        Map<Location, QuantumCache> caches = new ConcurrentHashMap<>();
        caches.put(location, cache);
        quantum.when(NetworkQuantumStorage::getCaches).thenReturn(caches);
        snapshot = new NetworkStorage(location, customDiamond(1, "original"), 12L);
    }

    @AfterEach
    void tearDown() {
        try {
            NetworkRoot.removePersistentAccessHistory(location);
            NetworkRoot.removeCountObservingAccessHistory(location);
        } finally {
            try {
                if (quantum != null) quantum.close();
            } finally {
                try {
                    if (storage != null) storage.close();
                } finally {
                    try {
                        if (networks != null) networks.close();
                    } finally {
                        MockBukkit.unmock();
                    }
                }
            }
        }
    }

    @Test
    void reassignedQuantumIdentityCannotFulfillOldSnapshotRequest() {
        cache.setAmount(0);
        cache.setItemStack(new ItemStack(Material.EMERALD));
        cache.setAmount(8);

        ItemStack fetched = snapshot.requestItem(new ItemRequest(customDiamond(1, "original"), 5));

        assertNull(fetched);
        assertEquals(8L, cache.getAmountLong(), "Wrong-item withdrawals must not reduce stored quantity");
        assertEquals(Material.EMERALD, cache.getItemStack().getType());
        quantum.verify(() -> NetworkQuantumStorage.getItemStack(eq(cache), eq(menu), anyInt()), never());
    }

    @Test
    void customItemMetadataMustStillMatchTheLiveTemplate() {
        cache.setItemStack(customDiamond(1, "different"));

        assertNull(snapshot.requestItem(new ItemRequest(customDiamond(1, "original"), 3)));
        assertEquals(12L, cache.getAmountLong());
        quantum.verify(() -> NetworkQuantumStorage.getItemStack(eq(cache), eq(menu), anyInt()), never());
    }

    @Test
    void matchingQuantumWithdrawalHoldsCacheMonitorUntilTheTransferCompletes() {
        AtomicBoolean held = new AtomicBoolean();
        quantum.when(() -> NetworkQuantumStorage.getItemStack(cache, menu, 4))
            .thenAnswer(call -> {
                held.set(Thread.holdsLock(cache));
                return cache.withdrawItem(4);
            });

        ItemStack fetched = snapshot.requestItem(new ItemRequest(customDiamond(1, "original"), 4));

        assertNotNull(fetched);
        assertEquals(4, fetched.getAmount());
        assertEquals(8L, cache.getAmountLong());
        assertTrue(held.get(), "Live template and withdrawal must use one cache monitor");
        assertEquals(customDiamond(1, "original").getItemMeta(), fetched.getItemMeta());
    }

    @Test
    void invalidQuantumRequestNeverReachesStorage() {
        assertNull(snapshot.requestItem(new ItemRequest(customDiamond(1, "original"), 0)));
        cache.setAmount(0);
        cache.setItemStack(null);
        assertNull(snapshot.requestItem(new ItemRequest(customDiamond(1, "original"), 1)));
        quantum.verify(() -> NetworkQuantumStorage.getItemStack(eq(cache), eq(menu), anyInt()), never());
    }

    @Test
    @SuppressWarnings("deprecation")
    void networkRootRejectsMismatchedResultInLegacyAndAccessorPaths() {
        NetworkRoot root = spy(new NetworkRoot(location, NodeType.CONTROLLER, 100));
        BarrelIdentity barrel = mock(BarrelIdentity.class);
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
        NetworkRoot.persistentAccessHistory.put(location, new ConcurrentHashMap<>(Map.of(storageLocation, 3)));

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

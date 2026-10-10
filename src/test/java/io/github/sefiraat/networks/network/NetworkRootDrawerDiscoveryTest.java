package io.github.sefiraat.networks.network;

import com.balugaq.netex.api.data.StorageUnitData;
import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.api.enums.StorageType;
import com.balugaq.netex.api.events.NetworkRootLocateStorageEvent;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import com.ytdd9527.networksexpansion.implementation.machines.unit.NetworksDrawer;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.slimefun.network.NetworkDirectional;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** Exercises the actual discovery overload used by Drawer Manager against substituted server/storage lookups. */
class NetworkRootDrawerDiscoveryTest {
    private static final BlockFace[] FACES = {
        BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST
    };

    private final Map<Location, SlimefunItem> blocks = new HashMap<>();
    private final Map<Location, StorageUnitData> drawers = new HashMap<>();
    private MockedStatic<Bukkit> bukkit;
    private MockedStatic<StorageCacheUtils> storage;
    private MockedStatic<NetworksDrawer> drawerStorage;
    private MockedStatic<NetworkDirectional> directions;
    private PluginManager pluginManager;
    private NetworksDrawer drawerBlock;
    private NetworkRoot root;

    @BeforeAll
    static void initializePluginConstants() throws ClassNotFoundException {
        // NetworkRoot reads config constants and drawer keys use the plugin name during class initialization.
        try (MockedStatic<Networks> networks = mockStatic(Networks.class);
             MockedStatic<SupportedPluginManager> integrations = mockStatic(SupportedPluginManager.class)) {
            ConfigManager config = mock(ConfigManager.class);
            Networks plugin = mock(Networks.class);
            SupportedPluginManager supported = mock(SupportedPluginManager.class);
            networks.when(Networks::getConfigManager).thenReturn(config);
            networks.when(Networks::getInstance).thenReturn(plugin);
            networks.when(Networks::getSupportedPluginManager).thenReturn(supported);
            integrations.when(SupportedPluginManager::getInstance).thenReturn(supported);
            when(plugin.getName()).thenReturn("Networks");
            when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
            Class.forName(NetworkRoot.class.getName());
            Class.forName(NetworkDirectional.class.getName());
            Class.forName(NetworksDrawer.class.getName());
        }
    }

    @BeforeEach
    void setUp() {
        pluginManager = mock(PluginManager.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
        bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
        storage = mockStatic(StorageCacheUtils.class);
        storage.when(() -> StorageCacheUtils.getSlimefunItem(any(Location.class)))
            .thenAnswer(call -> blocks.get(call.getArgument(0)));
        drawerStorage = mockStatic(NetworksDrawer.class);
        drawerStorage.when(() -> NetworksDrawer.getStorageData(any(Location.class)))
            .thenAnswer(call -> drawers.get(call.getArgument(0)));
        directions = mockStatic(NetworkDirectional.class);
        drawerBlock = mock(NetworksDrawer.class);
        root = new NetworkRoot(new Location(null, 100, 64, 100), NodeType.CONTROLLER, 100);
    }

    @AfterEach
    void tearDown() {
        if (directions != null) directions.close();
        if (drawerStorage != null) drawerStorage.close();
        if (storage != null) storage.close();
        if (bukkit != null) bukkit.close();
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = BlockFace.class, names = {"SELF", "NORTH"})
    void classicMonitorFindsAllSixFacesRegardlessOfItsHistoricalDirection(BlockFace historicalDirection) {
        Location monitor = monitor(0, NodeType.STORAGE_MONITOR);
        directions.when(() -> NetworkDirectional.getSelectedFace(monitor)).thenReturn(historicalDirection);
        Map<StorageUnitData, Location> expected = drawersAround(monitor);

        assertEquals(expected, discover());
        directions.verifyNoInteractions();
        expected.keySet().forEach(data -> verifyNoInteractions(data));
    }

    @Test
    void overlappingMonitorFacesReadEachPhysicalDrawerOnce() {
        Location first = monitor(0, NodeType.STORAGE_MONITOR);
        monitor(2, NodeType.STORAGE_MONITOR);
        Location shared = first.clone().add(1, 0, 0);
        StorageUnitData data = drawerAt(shared);

        assertEquals(Map.of(data, shared), discover());
        storage.verify(() -> StorageCacheUtils.getSlimefunItem(shared), times(1));
        drawerStorage.verify(() -> NetworksDrawer.getStorageData(shared), times(1));
        verifyNoInteractions(data);
    }

    @Test
    void inputAndOutputOnlyMonitorsStillExposeOnlyTheirSelectedFaces() {
        Location input = monitor(0, NodeType.INPUT_ONLY_MONITOR);
        Location output = monitor(10, NodeType.OUTPUT_ONLY_MONITOR);
        drawersAround(input);
        drawersAround(output);
        directions.when(() -> NetworkDirectional.getSelectedFace(input)).thenReturn(BlockFace.EAST);
        directions.when(() -> NetworkDirectional.getSelectedFace(output)).thenReturn(BlockFace.DOWN);
        Location inputTarget = input.clone().add(1, 0, 0);
        Location outputTarget = output.clone().add(0, -1, 0);

        assertEquals(Map.of(drawers.get(inputTarget), inputTarget, drawers.get(outputTarget), outputTarget), discover());
    }

    @Test
    void unconfiguredDirectionalMonitorsDoNotExposeAdjacentDrawers() {
        Location input = monitor(0, NodeType.INPUT_ONLY_MONITOR);
        Location output = monitor(10, NodeType.OUTPUT_ONLY_MONITOR);
        drawersAround(input);
        drawersAround(output);
        directions.when(() -> NetworkDirectional.getSelectedFace(output)).thenReturn(BlockFace.SELF);

        assertTrue(discover().isEmpty());
        storage.verifyNoInteractions();
        drawerStorage.verifyNoInteractions();
    }

    @Test
    void retainsStorageIdentityAndStrategyWhileSkippingUnavailableDataAndOtherBlocks() {
        Location monitor = monitor(0, NodeType.STORAGE_MONITOR);
        Location loaded = monitor.clone().add(1, 0, 0);
        StorageUnitData data = drawerAt(loaded);
        blocks.put(monitor.clone().add(-1, 0, 0), drawerBlock);
        blocks.put(monitor.clone().add(0, 1, 0), mock(SlimefunItem.class));
        var strategy = NetworkRootLocateStorageEvent.Strategy.custom("drawer-manager-test");

        assertEquals(Map.of(data, loaded), root.getCargoStorageUnitDatas(strategy, true));
        verifyNoInteractions(data);
        var event = ArgumentCaptor.forClass(NetworkRootLocateStorageEvent.class);
        verify(pluginManager).callEvent(event.capture());
        assertSame(root, event.getValue().getRoot());
        assertSame(strategy, event.getValue().getStrategy());
        assertEquals(StorageType.DRAWER, event.getValue().getStorageType());
        assertFalse(event.getValue().isInputAble());
        assertFalse(event.getValue().isOutputAble());
        assertFalse(event.getValue().isAsynchronous());
    }

    private Map<StorageUnitData, Location> discover() {
        return root.getCargoStorageUnitDatas(NetworkRootLocateStorageEvent.Strategy.DEFAULT, true);
    }

    private Location monitor(int x, NodeType type) {
        Location location = new Location(null, x, 64, 0);
        root.registerNode(location, type);
        return location;
    }

    private Map<StorageUnitData, Location> drawersAround(Location monitor) {
        Map<StorageUnitData, Location> expected = new HashMap<>();
        for (BlockFace face : FACES) {
            Location location = monitor.clone().add(face.getDirection());
            expected.put(drawerAt(location), location);
        }
        return expected;
    }

    private StorageUnitData drawerAt(Location location) {
        StorageUnitData data = mock(StorageUnitData.class);
        blocks.put(location, drawerBlock);
        drawers.put(location, data);
        return data;
    }
}

package com.balugaq.netex.api.data;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.api.enums.StorageUnitType;
import com.balugaq.netex.utils.Lang;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import com.ytdd9527.networksexpansion.implementation.machines.unit.NetworksDrawer;
import com.ytdd9527.networksexpansion.utils.databases.DataStorage;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real stored quantities and physical deposit remainders, including intentional valid Void Excess. */
class DrawerInvalidBalanceTest {
    private final List<MockedStatic<?>> mocks = new ArrayList<>();
    private final Location location = new Location(null, 14, 64, 14);
    private MockedStatic<DataStorage> persistence;
    private ItemStack template;

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        var networks = keep(mockStatic(Networks.class));
        var integrations = keep(mockStatic(SupportedPluginManager.class));
        Networks plugin = mock(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        SupportedPluginManager supported = mock(SupportedPluginManager.class);
        networks.when(Networks::getInstance).thenReturn(plugin);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getSupportedPluginManager).thenReturn(supported);
        integrations.when(SupportedPluginManager::getInstance).thenReturn(supported);
        when(plugin.getName()).thenReturn("Networks");
        when(plugin.namespace()).thenReturn("networks");
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        keep(mockStatic(Lang.class, call -> switch (call.getMethod().getName()) {
            case "getString" -> (String) call.getArgument(0);
            case "getStringArray" -> new String[0];
            case "getIcon" -> new ItemStack((Material) call.getArgument(1));
            default -> RETURNS_DEFAULTS.answer(call);
        }));
        Class.forName("io.github.sefiraat.networks.utils.StackUtils");
        Class.forName("io.github.sefiraat.networks.network.NetworkRoot");
        Class.forName(NetworksDrawer.class.getName());
        keep(mockStatic(SlimefunItem.class));
        persistence = keep(mockStatic(DataStorage.class));
        template = new ItemStack(Material.DIAMOND);
        template.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("drawer_balance", "identity"), PersistentDataType.STRING, "preserve-exact"));
        StorageUnitData.clearAllAccessHistory();
    }

    @AfterEach
    void tearDown() throws Exception {
        try { voidExcesses().remove(location); }
        finally {
            StorageUnitData.clearAllAccessHistory();
            for (int i = mocks.size() - 1; i >= 0; i--) mocks.get(i).close();
            MockBukkit.unmock();
        }
    }

    @ParameterizedTest
    @CsvSource({"300,false", "300,true", "-7,false", "-7,true",
        "2147483647,false", "2147483647,true", "-2147483648,false", "-2147483648,true"})
    void invalidStoredBalanceNeverConsumesInputOrRepairsOriginalStock(int original, boolean voidExcess) throws Exception {
        StorageUnitData drawer = drawer(original);
        if (voidExcess) voidExcesses().add(location);
        ItemContainer canonical = drawer.getStoredItemsMap().get(3);
        ItemStack identity = canonical.getSample();
        ItemStack incoming = template.asQuantity(13);
        ItemStack sourceBefore = incoming.clone();
        drawer.depositItemStack0(location, incoming, true);
        assertAll(
            () -> assertEquals(original, canonical.getAmount(), "Invalid saved stock must be preserved without guessing"),
            () -> assertSame(canonical, drawer.getStoredItemsMap().get(3)),
            () -> assertEquals(identity, canonical.getSample()),
            () -> assertEquals(sourceBefore, incoming, "Void Excess is not permission to delete input on invalid storage"),
            persistence::verifyNoInteractions);
    }

    @ParameterizedTest
    @ValueSource(ints = {300, -7, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void directAdditionRefusesInvalidStockWithThePublicZeroAcceptanceContract(int original) {
        StorageUnitData drawer = drawer(original);
        ItemStack incoming = template.asQuantity(13);
        assertEquals(0, drawer.addStoredItem0(location, incoming, 13, true, false));
        assertEquals(original, drawer.getStoredItemsMap().get(3).getAmount());
        assertEquals(13, incoming.getAmount());
        persistence.verifyNoInteractions();
    }

    @ParameterizedTest
    @CsvSource({"-7,false", "-7,true", "-2147483648,false", "-2147483648,true"})
    void withdrawalMustNotForgetAnInvalidNegativeRecord(int original, boolean contentLocked) {
        StorageUnitData drawer = drawer(original);
        ItemContainer canonical = drawer.getStoredItemsMap().get(3);
        ItemStack requested = template.clone();
        assertNull(drawer.requestItem0(location, new ItemRequest(requested, 1), contentLocked));
        assertSame(canonical, drawer.getStoredItemsMap().get(3), "Invalid negative stock is not an empty entry");
        assertEquals(original, canonical.getAmount());
        assertEquals(template, requested);
        persistence.verifyNoInteractions();
    }

    @ParameterizedTest
    @CsvSource({"249,false,6", "249,true,0", "256,false,13", "256,true,0"})
    void validFullAndPartialBalancesRetainTheirConfiguredOverflowBehavior(
        int original, boolean voidExcess, int expectedRemainder) throws Exception {
        StorageUnitData drawer = drawer(original);
        if (voidExcess) voidExcesses().add(location);
        ItemStack incoming = template.asQuantity(13);
        drawer.depositItemStack0(location, incoming, true);
        assertEquals(256, drawer.getStoredItemsMap().get(3).getAmount());
        assertEquals(expectedRemainder, incoming.getAmount());
        if (original < 256) persistence.verify(() -> DataStorage.setStoredAmount(7, 3, 256));
        else persistence.verifyNoInteractions();
    }

    private StorageUnitData drawer(int amount) {
        var entries = new ConcurrentHashMap<Integer, ItemContainer>();
        entries.put(3, new ItemContainer(3, template, amount));
        return new StorageUnitData(7, mock(OfflinePlayer.class), StorageUnitType.TINY, true, location, entries);
    }

    private <T> MockedStatic<T> keep(MockedStatic<T> value) { mocks.add(value); return value; }

    @SuppressWarnings("unchecked")
    private static Set<Location> voidExcesses() throws Exception {
        Field field = NetworksDrawer.class.getDeclaredField("voidExcesses");
        field.setAccessible(true);
        return (Set<Location>) field.get(null);
    }
}

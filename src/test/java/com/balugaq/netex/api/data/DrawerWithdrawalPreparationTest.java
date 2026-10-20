package com.balugaq.netex.api.data;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.balugaq.netex.api.enums.StorageUnitType;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import com.ytdd9527.networksexpansion.utils.databases.DataStorage;
import io.github.sefiraat.networks.Networks;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Executes the actual withdrawal helper with real ItemContainer balances and Bukkit metadata.
 * Reflection isolates preparation from matching policy; persistence is observed, not simulated SQL.
 */
class DrawerWithdrawalPreparationTest {
    private static final int DRAWER_ID = 7;
    private static final int ITEM_ID = 3;
    private static final int ORIGINAL = 16;
    private static final NamespacedKey OWNER = new NamespacedKey("receipt_test", "owner");
    private static final NamespacedKey SERIAL = new NamespacedKey("receipt_test", "serial");
    private StorageUnitData drawer;
    private ItemContainer stored;
    private ItemStack template;
    private Method withdraw;

    @BeforeAll
    static void initializeMatchingConstants() throws Exception {
        try (var networks = mockStatic(Networks.class)) {
            Networks plugin = mock(Networks.class);
            ConfigManager config = mock(ConfigManager.class);
            networks.when(Networks::getInstance).thenReturn(plugin);
            networks.when(Networks::getConfigManager).thenReturn(config);
            when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
            Class.forName("io.github.sefiraat.networks.utils.StackUtils");
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        MockBukkit.mock();
        template = new ItemStack(Material.DIAMOND, 5);
        template.editMeta(meta -> {
            meta.displayName(Component.text("withdrawal-owner-A"));
            meta.lore(List.of(Component.text("preserve-request-template")));
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "A");
            meta.getPersistentDataContainer().set(SERIAL, PersistentDataType.LONG, 123456789L);
        });
        try (var slimefunItems = mockStatic(SlimefunItem.class)) {
            stored = new ItemContainer(ITEM_ID, template, ORIGINAL);
        }
        var items = new ConcurrentHashMap<Integer, ItemContainer>();
        items.put(ITEM_ID, stored);
        drawer = new StorageUnitData(DRAWER_ID, mock(OfflinePlayer.class), StorageUnitType.TINY,
            true, new Location(null, 1, 64, 1), items);
        withdraw = StorageUnitData.class.getDeclaredMethod("withdrawFromContainer",
            ItemContainer.class, ItemStack.class, int.class, boolean.class);
        withdraw.setAccessible(true);
    }

    @AfterEach
    void tearDown() { MockBukkit.unmock(); }

    @ParameterizedTest
    @CsvSource({"false,3", "false,16", "true,3", "true,16"})
    void cloneFailureDoesNotDebitOrForgetStoredIdentity(boolean locked, int requested) {
        ItemStack failingTemplate = mock(ItemStack.class);
        RuntimeException injected = new IllegalStateException("injected-clone-failure");
        when(failingTemplate.clone()).thenThrow(injected);
        ItemStack sampleBefore = stored.getSample();
        try (var persistence = mockStatic(DataStorage.class)) {
            assertSame(injected, assertThrows(IllegalStateException.class,
                () -> withdraw(failingTemplate, requested, locked)));
            assertAll(
                () -> assertEquals(ORIGINAL, stored.getAmount()),
                () -> assertSame(stored, drawer.getStoredItemsMap().get(ITEM_ID)),
                () -> assertEquals(sampleBefore, stored.getSample()),
                persistence::verifyNoInteractions);
        }
    }

    @ParameterizedTest
    @CsvSource({"false,3", "false,16", "true,3", "true,16"})
    void quantityPreparationFailureDoesNotDebitOrPublish(boolean locked, int requested) {
        ItemStack failingTemplate = mock(ItemStack.class);
        ItemStack prepared = mock(ItemStack.class);
        RuntimeException injected = new IllegalStateException("injected-quantity-failure");
        when(failingTemplate.clone()).thenReturn(prepared);
        doThrow(injected).when(prepared).setAmount(requested);
        try (var persistence = mockStatic(DataStorage.class)) {
            assertSame(injected, assertThrows(IllegalStateException.class,
                () -> withdraw(failingTemplate, requested, locked)));
            assertAll(
                () -> assertEquals(ORIGINAL, stored.getAmount()),
                () -> assertSame(stored, drawer.getStoredItemsMap().get(ITEM_ID)),
                persistence::verifyNoInteractions);
        }
    }

    @Test
    void partialWithdrawalPreservesRequestMetadataAndIndependentOwnership() {
        ItemStack before = template.clone();
        try (var persistence = mockStatic(DataStorage.class)) {
            ItemStack result = withdraw(template, 3, false);
            assertNotNull(result);
            assertNotSame(template, result);
            assertEquals(3, result.getAmount());
            assertEquals(13, stored.getAmount());
            assertEquals(ORIGINAL, result.getAmount() + stored.getAmount());
            assertTrue(before.isSimilar(result));
            assertEquals(before, template);
            assertEquals("A", result.getItemMeta().getPersistentDataContainer().get(OWNER, PersistentDataType.STRING));
            assertEquals(Long.valueOf(123456789L), result.getItemMeta().getPersistentDataContainer().get(SERIAL, PersistentDataType.LONG));
            persistence.verify(() -> DataStorage.setStoredAmount(DRAWER_ID, ITEM_ID, 13));
            persistence.verifyNoMoreInteractions();
            result.editMeta(meta -> meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "changed"));
            assertEquals(before, template);
            assertEquals("A", stored.getSample().getItemMeta().getPersistentDataContainer().get(OWNER, PersistentDataType.STRING));
        }
    }

    @Test
    void unlockedFinalWithdrawalDeletesExactlyOneRecord() {
        try (var persistence = mockStatic(DataStorage.class)) {
            ItemStack result = withdraw(template, ORIGINAL, false);
            assertEquals(ORIGINAL, result.getAmount());
            assertEquals(0, stored.getAmount());
            assertFalse(drawer.getStoredItemsMap().containsKey(ITEM_ID));
            persistence.verify(() -> DataStorage.deleteStoredItem(DRAWER_ID, ITEM_ID));
            persistence.verifyNoMoreInteractions();
        }
    }

    @Test
    void lockedFinalWithdrawalRetainsZeroBalanceIdentity() {
        try (var persistence = mockStatic(DataStorage.class)) {
            ItemStack result = withdraw(template, ORIGINAL, true);
            assertEquals(ORIGINAL, result.getAmount());
            assertEquals(0, stored.getAmount());
            assertSame(stored, drawer.getStoredItemsMap().get(ITEM_ID));
            persistence.verify(() -> DataStorage.setStoredAmount(DRAWER_ID, ITEM_ID, 0));
            persistence.verifyNoMoreInteractions();
        }
    }

    @Test
    void oversizedRequestReturnsOnlyAvailableStockWithoutEditingTemplate() {
        ItemStack before = template.clone();
        try (var persistence = mockStatic(DataStorage.class)) {
            ItemStack result = withdraw(template, Integer.MAX_VALUE, true);
            assertEquals(ORIGINAL, result.getAmount());
            assertEquals(0, stored.getAmount());
            assertEquals(before, template);
            persistence.verify(() -> DataStorage.setStoredAmount(DRAWER_ID, ITEM_ID, 0));
            persistence.verifyNoMoreInteractions();
        }
    }

    @Test
    void recursiveTemplatePreparationCannotWithdrawAgainAndGuardIsReleased() {
        ItemStack recursive = mock(ItemStack.class);
        AtomicInteger cloning = new AtomicInteger();
        when(recursive.clone()).thenAnswer(call -> {
            assertEquals(1, cloning.incrementAndGet(), "Nested withdrawal must not reach clone again");
            assertNull(withdraw(recursive, 3, true));
            return template.clone();
        });
        try (var persistence = mockStatic(DataStorage.class)) {
            ItemStack first = withdraw(recursive, 3, true);
            assertEquals(3, first.getAmount());
            assertEquals(13, stored.getAmount());
            ItemStack second = withdraw(template, 2, true);
            assertEquals(2, second.getAmount(), "A successful operation releases its preparation guard");
            assertEquals(ORIGINAL, first.getAmount() + second.getAmount() + stored.getAmount());
            persistence.verify(() -> DataStorage.setStoredAmount(DRAWER_ID, ITEM_ID, 13));
            persistence.verify(() -> DataStorage.setStoredAmount(DRAWER_ID, ITEM_ID, 11));
            persistence.verifyNoMoreInteractions();
        }
    }

    @Test
    void preparationBalanceChangeCannotCommitAnObsoleteWithdrawal() {
        ItemStack changing = mock(ItemStack.class);
        when(changing.clone()).thenAnswer(call -> {
            stored.addAmount(4); // Explicit callback fault; represents four separately supplied items.
            return template.clone();
        });
        try (var persistence = mockStatic(DataStorage.class)) {
            assertNull(withdraw(changing, 3, false));
            assertEquals(20, stored.getAmount());
            assertSame(stored, drawer.getStoredItemsMap().get(ITEM_ID));
            persistence.verifyNoInteractions();
            assertEquals(3, withdraw(template, 3, true).getAmount());
            assertEquals(17, stored.getAmount());
        }
    }

    @Test
    void failedPreparationReleasesGuardForHealthyRetry() {
        ItemStack failing = mock(ItemStack.class);
        when(failing.clone()).thenThrow(new IllegalStateException("injected"));
        try (var persistence = mockStatic(DataStorage.class)) {
            assertThrows(IllegalStateException.class, () -> withdraw(failing, 3, false));
            assertEquals(ORIGINAL, stored.getAmount());
            persistence.verifyNoInteractions();
            assertEquals(3, withdraw(template, 3, false).getAmount());
            assertEquals(13, stored.getAmount());
            persistence.verify(() -> DataStorage.setStoredAmount(DRAWER_ID, ITEM_ID, 13));
            persistence.verifyNoMoreInteractions();
        }
    }

    private ItemStack withdraw(ItemStack requestedTemplate, int requested, boolean locked) {
        try {
            return (ItemStack) withdraw.invoke(drawer, stored, requestedTemplate, requested, locked);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new AssertionError(failure.getCause());
        } catch (ReflectiveOperationException failure) {
            throw new AssertionError(failure);
        }
    }
}

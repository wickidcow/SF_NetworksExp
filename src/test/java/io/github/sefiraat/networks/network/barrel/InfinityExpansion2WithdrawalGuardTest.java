package io.github.sefiraat.networks.network.barrel;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.utils.StackUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InfinityExpansion2WithdrawalGuardTest {
    private final Map<Integer, ItemStack> items = new HashMap<>();
    private MockedStatic<Networks> networks;
    private BlockMenu menu;

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
        menu = mock(BlockMenu.class);
        when(menu.getSize()).thenReturn(54);
        when(menu.getItemInSlot(anyInt())).thenAnswer(call -> copy(items.get(call.getArgument(0))));
        successfulSetter();
    }

    @AfterEach
    void tearDown() {
        try {
            if (networks != null) networks.close();
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void ordinaryMultiSlotTransfersConserveExactCountsAcross256DeterministicCases() {
        Random random = new Random(0x1e2c0ffeeL);
        int[] slots = {16, 17, 18, 19};
        for (int iteration = 0; iteration < 256; iteration++) {
            items.clear();
            int total = 0;
            for (int slot : slots) {
                int amount = random.nextInt(65);
                if (amount > 0) items.put(slot, diamond(amount));
                total += amount;
            }
            int requested = 1 + random.nextInt(64);
            ItemStack result = InfinityExpansion2Withdrawal.withdraw(menu, slots, diamond(1), requested);
            int delivered = result == null ? 0 : result.getAmount();
            int remaining = items.values().stream().filter(item -> item != null && !item.getType().isAir())
                .mapToInt(ItemStack::getAmount).sum();
            assertEquals(Math.min(total, requested), delivered, "Wrong transfer amount in case " + iteration);
            assertEquals(total, delivered + remaining, "Conservation failed in case " + iteration);
        }
        verify(menu, never()).getItemInSlot(0);
        verify(menu, never()).getItemInSlot(53);
    }

    @Test
    void broadRequestMatchingDoesNotRewritePhysicalNamesOrTypedPersistentData() {
        ItemStack physical = diamond(8);
        physical.editMeta(meta -> {
            meta.displayName(Component.text("original physical item"));
            meta.getPersistentDataContainer().set(new NamespacedKey("fixture", "owner"),
                PersistentDataType.STRING, "retained-owner");
        });
        items.put(16, physical);
        try (MockedStatic<StackUtils> matches = mockStatic(StackUtils.class)) {
            matches.when(() -> StackUtils.itemsMatch(any(ItemStack.class), any(ItemStack.class))).thenReturn(true);
            ItemStack result = InfinityExpansion2Withdrawal.withdraw(menu, new int[]{16}, diamond(1), 4);
            assertNotNull(result);
            assertTrue(physical.isSimilar(result), "Never turn a physical item into the request template");
            assertTrue(physical.isSimilar(items.get(16)));
            assertEquals(4, result.getAmount());
            assertEquals(4, items.get(16).getAmount());
        }
    }

    @Test
    void writerCannotMutateTheSavedExpectedRemainderToForgeItsReceipt() {
        items.put(16, diamond(8));
        doAnswer(call -> {
            ItemStack proposed = call.getArgument(1);
            proposed.setAmount(8);
            items.put(16, proposed);
            return null;
        }).when(menu).replaceExistingItem(eq(16), any(ItemStack.class));
        assertThrows(IllegalStateException.class,
            () -> InfinityExpansion2Withdrawal.withdraw(menu, new int[]{16}, diamond(1), 4));
        assertEquals(8, items.get(16).getAmount());
    }

    @Test
    void sameMenuReentryCannotIssueAnotherReceiptAndGuardIsReleased() {
        items.put(16, diamond(8));
        doAnswer(call -> {
            assertThrows(IllegalStateException.class,
                () -> InfinityExpansion2Withdrawal.withdraw(menu, new int[]{16}, diamond(1), 4));
            items.put(16, copy(call.getArgument(1)));
            return null;
        }).when(menu).replaceExistingItem(eq(16), nullable(ItemStack.class));
        ItemStack first = InfinityExpansion2Withdrawal.withdraw(menu, new int[]{16}, diamond(1), 4);
        assertNotNull(first);
        assertEquals(4, first.getAmount());
        successfulSetter();
        ItemStack second = InfinityExpansion2Withdrawal.withdraw(menu, new int[]{16}, diamond(1), 4);
        assertNotNull(second);
        assertEquals(4, second.getAmount());
        assertNull(items.get(16));
    }

    @Test
    void rejectedWriteDoesNotLeaveTheOperationGuardPermanentlyLocked() {
        items.put(16, diamond(8));
        doThrow(new IllegalStateException("Injected writer failure"))
            .when(menu).replaceExistingItem(anyInt(), nullable(ItemStack.class));
        assertThrows(IllegalStateException.class,
            () -> InfinityExpansion2Withdrawal.withdraw(menu, new int[]{16}, diamond(1), 4));
        assertEquals(8, items.get(16).getAmount());
        successfulSetter();
        assertNotNull(InfinityExpansion2Withdrawal.withdraw(menu, new int[]{16}, diamond(1), 4));
        assertEquals(4, items.get(16).getAmount());
    }

    @Test
    void duplicateOrInvalidSlotsAreRejectedBeforeAnyDebit() {
        items.put(16, diamond(8));
        assertThrows(IllegalStateException.class,
            () -> InfinityExpansion2Withdrawal.withdraw(menu, new int[]{16, 16}, diamond(1), 4));
        assertThrows(IllegalStateException.class,
            () -> InfinityExpansion2Withdrawal.withdraw(menu, new int[]{16, 54}, diamond(1), 4));
        verify(menu, never()).replaceExistingItem(anyInt(), nullable(ItemStack.class));
        assertEquals(8, items.get(16).getAmount());
    }

    private void successfulSetter() {
        doAnswer(call -> {
            items.put(call.getArgument(0), copy(call.getArgument(1)));
            return null;
        }).when(menu).replaceExistingItem(anyInt(), nullable(ItemStack.class));
    }

    private static ItemStack diamond(int amount) { return new ItemStack(Material.DIAMOND, amount); }
    private static ItemStack copy(ItemStack item) { return item == null ? null : item.clone(); }
}

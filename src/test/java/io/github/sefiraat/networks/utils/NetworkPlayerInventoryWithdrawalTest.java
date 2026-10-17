package io.github.sefiraat.networks.utils;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
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
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Exercises the real player storage insertion helper with independent pre/post inventory
 * readbacks. The inventory writer is intentionally allowed to reject or partially apply.
 */
class NetworkPlayerInventoryWithdrawalTest {

    private final Location accessor = new Location(null, 57, 66, 91);
    private MockedStatic<Networks> networks;
    private NetworkRoot root;
    private Player player;
    private PlayerInventory inventory;
    private ItemStack[] storage;
    private int returned;

    @BeforeEach
    void setUp() throws ClassNotFoundException {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        Networks plugin = mock(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getInstance).thenReturn(plugin);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        when(plugin.getLogger()).thenReturn(logger);
        Class.forName(StackUtils.class.getName());
        Class.forName(NetworkRoot.class.getName());

        root = mock(NetworkRoot.class);
        player = mock(Player.class);
        inventory = mock(PlayerInventory.class);
        storage = new ItemStack[9];
        returned = 0;
        when(player.getInventory()).thenReturn(inventory);
        when(player.getLocation()).thenReturn(accessor);
        when(inventory.getStorageContents()).thenAnswer(call -> detached(storage));
        doAnswer(call -> {
            storage = detached(call.getArgument(0));
            return null;
        }).when(inventory).setStorageContents(any(ItemStack[].class));

        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            ItemRequest request = call.getArgument(1);
            return diamond(request.getAmount());
        });
        doAnswer(call -> {
            ItemStack remainder = call.getArgument(1);
            returned += remainder.getAmount();
            remainder.setAmount(0);
            return null;
        }).when(root).addItemStack0(eq(accessor), any(ItemStack.class));
        TransferAudit.reset();
    }

    @AfterEach
    void tearDown() {
        try {
            TransferAudit.reset();
            if (networks != null) {
                networks.close();
            }
        } finally {
            MockBukkit.unmock();
        }
    }

    @Test
    void ordinaryInsertionKeepsAllItemsAndMetadata() {
        assertEquals(8, move(8));
        assertEquals(8, amount(0));
        assertEquals(0, returned);
        assertTrue(diamond(1).isSimilar(storage[0]));
        assertEquals(8, TransferAudit.snapshot().withdrawalCommitted());
        assertEquals(8, stored() + returned);
    }

    @Test
    void partialStacksAreFilledBeforeEmptySlots() {
        storage[0] = diamond(60);
        assertEquals(8, move(8));
        assertEquals(64, amount(0));
        assertEquals(4, amount(1));
        assertEquals(0, returned);
        assertEquals(68, stored() + returned);
    }

    @Test
    void destinationWritesThenThrowsDoesNotGetAnAdditionalNetworkCopy() {
        doAnswer(call -> {
            storage = detached(call.getArgument(0));
            throw new IllegalStateException("Inventory setter applied then threw");
        }).when(inventory).setStorageContents(any(ItemStack[].class));

        assertEquals(8, move(8));
        assertEquals(8, stored());
        assertEquals(0, returned);
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void refusedDestinationWriteReturnsTheEntireWithdrawal() {
        doAnswer(call -> { throw new IllegalStateException("Inventory setter refused"); })
            .when(inventory).setStorageContents(any(ItemStack[].class));

        assertEquals(0, move(8));
        assertEquals(0, stored());
        assertEquals(8, returned);
        assertEquals(8, stored() + returned);
    }

    @Test
    void partiallyAppliedWriteThenThrowReturnsOnlyUncommittedRemainder() {
        storage[0] = diamond(60);
        doAnswer(call -> {
            ItemStack[] proposed = call.getArgument(0);
            storage[0] = proposed[0].clone();
            throw new IllegalStateException("First slot committed, later slot rejected");
        }).when(inventory).setStorageContents(any(ItemStack[].class));

        assertEquals(4, move(8));
        assertEquals(64, amount(0));
        assertEquals(0, amount(1));
        assertEquals(4, returned);
        assertEquals(68, stored() + returned);
    }

    @Test
    void silentSetterRejectionAlsoReturnsTheEntireWithdrawal() {
        doAnswer(call -> null).when(inventory).setStorageContents(any(ItemStack[].class));

        assertEquals(0, move(8));
        assertEquals(0, stored());
        assertEquals(8, returned);
    }

    @Test
    void changedInventoryDuringNetworkWithdrawalIsNotOverwritten() {
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            storage[0] = new ItemStack(Material.EMERALD, 3);
            return diamond(8);
        });

        assertEquals(0, move(8));
        assertEquals(Material.EMERALD, storage[0].getType());
        assertEquals(8, returned);
        verify(inventory, never()).setStorageContents(any(ItemStack[].class));
    }

    @Test
    void matchingItemsInsertedByWithdrawalCallbackAreNotBlindlyRefunded() {
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenAnswer(call -> {
            storage[0] = diamond(8);
            return diamond(8);
        });

        assertEquals(0, move(8));
        assertEquals(8, stored());
        assertEquals(0, returned, "Callback-delivered items must not be duplicated");
        verify(inventory, never()).setStorageContents(any(ItemStack[].class));
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void unreadableInventoryBeforeCommitFailsClosed() {
        AtomicInteger reads = new AtomicInteger();
        when(inventory.getStorageContents()).thenAnswer(call -> {
            if (reads.incrementAndGet() >= 3) {
                throw new IllegalStateException("Pre-commit storage read failed");
            }
            return detached(storage);
        });

        assertEquals(0, move(8));
        assertEquals(0, returned);
        verify(inventory, never()).setStorageContents(any(ItemStack[].class));
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void unrelatedDestinationReplacementIsAmbiguousAndIsNotBlindlyRefunded() {
        doAnswer(call -> {
            storage[0] = new ItemStack(Material.EMERALD, 4);
            throw new IllegalStateException("Another plugin replaced a slot");
        }).when(inventory).setStorageContents(any(ItemStack[].class));

        assertEquals(0, move(8));
        assertEquals(Material.EMERALD, storage[0].getType());
        assertEquals(0, returned);
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void unreadablePostWriteInventoryDoesNotTriggerAnOptimisticRefund() {
        AtomicInteger calls = new AtomicInteger();
        when(inventory.getStorageContents()).thenAnswer(call -> {
            if (calls.incrementAndGet() >= 5) {
                throw new IllegalStateException("Unverifiable destination readback");
            }
            return detached(storage);
        });

        assertEquals(0, move(8));
        assertEquals(8, stored());
        assertEquals(0, returned);
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void playerUpdateFailureAfterCommitDoesNotMintMoreItems() {
        doThrow(new IllegalStateException("Client packet update failed")).when(player).updateInventory();

        assertEquals(8, move(8));
        assertEquals(8, stored());
        assertEquals(0, returned);
        assertEquals(1, TransferAudit.snapshot().failures());
    }

    @Test
    void noCapacityDoesNotWithdrawFromTheNetwork() {
        Arrays.fill(storage, new ItemStack(Material.EMERALD, 64));

        assertEquals(0, move(8));
        verify(root, never()).getItemStack0(any(), any());
        assertEquals(0, returned);
    }

    @Test
    void networkReturningMoreThanRequestedCannotOverfillAnInventory() {
        storage[0] = diamond(63);
        for (int i = 1; i < storage.length; i++) {
            storage[i] = new ItemStack(Material.EMERALD, 64);
        }
        when(root.getItemStack0(eq(accessor), any(ItemRequest.class))).thenReturn(diamond(8));

        assertEquals(0, move(8));
        assertEquals(63, amount(0));
        assertEquals(8, returned);
        verify(inventory, never()).setStorageContents(any(ItemStack[].class));
    }

    private int move(int amount) {
        return NetworkTransferUtils.moveNetworkItemIntoPlayerInventory(
            root, accessor, player, diamond(1), amount);
    }

    private int amount(int slot) {
        ItemStack stack = storage[slot];
        return stack == null || stack.getType().isAir() ? 0 : stack.getAmount();
    }

    private int stored() {
        int total = 0;
        for (int slot = 0; slot < storage.length; slot++) {
            if (storage[slot] != null && storage[slot].getType() == Material.DIAMOND) {
                total += amount(slot);
            }
        }
        return total;
    }

    private static ItemStack[] detached(ItemStack[] stacks) {
        ItemStack[] result = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            if (stacks[i] != null) {
                result[i] = stacks[i].clone();
            }
        }
        return result;
    }

    private static ItemStack diamond(int amount) {
        ItemStack item = new ItemStack(Material.DIAMOND, amount);
        var meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(
            NamespacedKey.fromString("test:inventory_owner"),
            PersistentDataType.STRING, "preserved");
        item.setItemMeta(meta);
        return item;
    }
}

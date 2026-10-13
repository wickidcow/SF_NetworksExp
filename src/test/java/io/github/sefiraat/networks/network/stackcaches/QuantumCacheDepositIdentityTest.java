package io.github.sefiraat.networks.network.stackcaches;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.utils.StackUtils;
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
import static org.mockito.Mockito.*;

/**
 * Regression coverage for item-type and quantity conservation on Quantum deposits.
 * Tests real QuantumCache mutations while mocking only the enclosing plugin boundary.
 */
class QuantumCacheDepositIdentityTest {
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
        cache = new QuantumCache(namedDiamond(1, "same"), 0L, 64L, false, false);
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
    void matchingDepositAcceptsOnlyTheOfferedItemsAndKeepsMetadata() {
        ItemStack incoming = namedDiamond(12, "same");
        assertEquals(0, cache.increaseMatchingAmount(incoming));
        assertEquals(12L, cache.getAmountLong());
        assertEquals(12, incoming.getAmount(), "Acceptance reports a remainder; it does not mutate the source");
        assertEquals(namedDiamond(1, "same").getItemMeta(), cache.getItemStack().getItemMeta());
    }

    @Test
    void oldTemplateCannotDepositAfterEmptyStorageIsReassigned() {
        cache.setItemStack(new ItemStack(Material.EMERALD));
        ItemStack oldItem = namedDiamond(8, "same");
        assertEquals(8, cache.increaseMatchingAmount(oldItem));
        assertEquals(0L, cache.getAmountLong());
        assertEquals(Material.EMERALD, cache.getItemStack().getType());

        ItemStack newItem = new ItemStack(Material.EMERALD, 7);
        assertEquals(0, cache.increaseMatchingAmount(newItem));
        assertEquals(7L, cache.getAmountLong());
        assertEquals(7, newItem.getAmount());
    }

    @Test
    void sameMaterialWithDifferentPersistentDataCannotBeConverted() {
        ItemStack wrongOwner = namedDiamond(9, "different");
        assertEquals(9, cache.increaseMatchingAmount(wrongOwner));
        assertEquals(0L, cache.getAmountLong());
        assertEquals(9, wrongOwner.getAmount());
    }

    @Test
    void capacityLimitedDepositReturnsOnlyTheTrueUnacceptedRemainder() {
        cache.setAmount(60L);
        ItemStack incoming = namedDiamond(10, "same");
        assertEquals(6, cache.increaseMatchingAmount(incoming));
        assertEquals(64L, cache.getAmountLong());
        assertEquals(10, incoming.getAmount());
    }

    @Test
    void voidModePreservesIntentionalExcessDisposalWithoutInflatingStorage() {
        cache.setAmount(60L);
        cache.setVoidExcess(true);
        assertEquals(0, cache.increaseMatchingAmount(namedDiamond(10, "same")));
        assertEquals(64L, cache.getAmountLong());
    }

    @Test
    void unassignedAndZeroQuantityInputsDoNotMintItems() {
        cache.setItemStack(null);
        assertEquals(4, cache.increaseMatchingAmount(namedDiamond(4, "same")));
        assertEquals(0L, cache.getAmountLong());
        ItemStack zeroQuantity = namedDiamond(1, "same");
        zeroQuantity.setAmount(0);
        assertEquals(0, cache.increaseMatchingAmount(zeroQuantity));
        assertEquals(0L, cache.getAmountLong());
    }

    @Test
    void manualExtractionNeverReturnsMoreThanTheLiveStoredQuantity() {
        cache.setAmount(5L);
        // Simulate a previous transport withdrawal before the player extraction.
        ItemStack networkWithdrawal = cache.withdrawItem(4);
        assertNotNull(networkWithdrawal);
        assertEquals(4, networkWithdrawal.getAmount());

        // The manual handler now uses this actual remaining cache transaction.
        ItemStack playerWithdrawal = cache.withdrawItem(64);
        assertNotNull(playerWithdrawal);
        assertEquals(1, playerWithdrawal.getAmount());
        assertEquals(0L, cache.getAmountLong());
        assertNull(cache.withdrawItem(64));
        assertEquals(5, networkWithdrawal.getAmount() + playerWithdrawal.getAmount());
    }

    @Test
    void manualInsertCannotOverfillOrConsumeBeyondRemainingCapacity() {
        cache.setAmount(60L);
        ItemStack source = namedDiamond(10, "same");
        int available = (int) Math.min(source.getAmount(), cache.getLimitLong() - cache.getAmountLong());
        ItemStack offer = source.clone();
        offer.setAmount(available);
        int accepted = available - cache.increaseMatchingAmount(offer);
        source.setAmount(source.getAmount() - accepted);

        assertEquals(4, accepted);
        assertEquals(6, source.getAmount());
        assertEquals(64L, cache.getAmountLong());
        assertEquals(70L, source.getAmount() + cache.getAmountLong());
    }

    private static ItemStack namedDiamond(int amount, String owner) {
        ItemStack stack = new ItemStack(Material.DIAMOND, amount);
        var meta = stack.getItemMeta();
        meta.displayName(Component.text("Protected custom item"));
        meta.getPersistentDataContainer().set(
            NamespacedKey.fromString("oldaddon:owner"), PersistentDataType.STRING, owner);
        stack.setItemMeta(meta);
        return stack;
    }
}

package io.github.sefiraat.networks.network;

import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.network.barrel.BarrelIdentity;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.sefiraat.networks.utils.StackUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NetworkRootBulkAvailabilityTest {

    @BeforeAll
    static void initializePluginConstants() throws ClassNotFoundException {
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
            when(plugin.namespace()).thenReturn("networks");
            Class.forName(NetworkRoot.class.getName());
        }
    }

    @Test
    void satisfiesSeveralIngredientsFromOneBarrelTraversal() {
        ItemStack diamond = stack(Material.DIAMOND, 1, 64);
        ItemStack gold = stack(Material.GOLD_INGOT, 1, 64);
        ItemRequest[] requests = {
            new ItemRequest(diamond, 5),
            new ItemRequest(gold, 7)
        };

        BarrelIdentity firstDiamond = barrel(diamond, 3);
        BarrelIdentity secondDiamond = barrel(diamond, 2);
        BarrelIdentity goldBarrel = barrel(gold, 7);
        Set<BarrelIdentity> barrels = new LinkedHashSet<>();
        barrels.add(firstDiamond);
        barrels.add(secondDiamond);
        barrels.add(goldBarrel);

        NetworkRoot root = mock(NetworkRoot.class, CALLS_REAL_METHODS);
        doReturn(barrels).when(root).getOutputAbleBarrels();

        try (MockedStatic<StackUtils> stacks = mockStatic(StackUtils.class)) {
            stacks.when(() -> StackUtils.itemsMatch(requests[0], diamond)).thenReturn(true);
            stacks.when(() -> StackUtils.itemsMatch(requests[0], gold)).thenReturn(false);
            stacks.when(() -> StackUtils.itemsMatch(requests[1], diamond)).thenReturn(false);
            stacks.when(() -> StackUtils.itemsMatch(requests[1], gold)).thenReturn(true);

            assertTrue(root.containsAll(requests));
        }

        verify(root, times(1)).getOutputAbleBarrels();
    }

    @Test
    void refusesWhenAnyIngredientIsShort() {
        ItemStack diamond = stack(Material.DIAMOND, 1, 64);
        ItemStack gold = stack(Material.GOLD_INGOT, 1, 64);
        ItemRequest[] requests = {
            new ItemRequest(diamond, 5),
            new ItemRequest(gold, 8)
        };

        Set<BarrelIdentity> barrels = new LinkedHashSet<>();
        barrels.add(barrel(diamond, 5));
        barrels.add(barrel(gold, 7));

        NetworkRoot root = mock(NetworkRoot.class, CALLS_REAL_METHODS);
        doReturn(barrels).when(root).getOutputAbleBarrels();
        // Remaining storage classes are empty in this focused fixture.
        doReturn(java.util.Map.of()).when(root).getOutputAbleCargoStorageUnitDatas();

        try (MockedStatic<StackUtils> stacks = mockStatic(StackUtils.class)) {
            stacks.when(() -> StackUtils.itemsMatch(requests[0], diamond)).thenReturn(true);
            stacks.when(() -> StackUtils.itemsMatch(requests[0], gold)).thenReturn(false);
            stacks.when(() -> StackUtils.itemsMatch(requests[1], diamond)).thenReturn(false);
            stacks.when(() -> StackUtils.itemsMatch(requests[1], gold)).thenReturn(true);

            assertFalse(root.containsAll(requests));
        }
    }

    @Test
    void accumulationNeverCreditsOnePhysicalStackToTwoRequests() {
        ItemStack shared = stack(Material.DIAMOND, 1, 64);
        ItemRequest first = new ItemRequest(shared, 4);
        ItemRequest second = new ItemRequest(shared, 4);
        ItemRequest[] requests = {first, second};
        long[] found = new long[2];

        try (MockedStatic<StackUtils> stacks = mockStatic(StackUtils.class)) {
            stacks.when(() -> StackUtils.itemsMatch(first, shared)).thenReturn(true);
            stacks.when(() -> StackUtils.itemsMatch(second, shared)).thenReturn(true);

            int remaining = NetworkRoot.accumulateAvailability(requests, found, 2, shared, 4);
            assertTrue(found[0] == 4 && found[1] == 0);
            assertTrue(remaining == 1);
        }
    }

    private static BarrelIdentity barrel(ItemStack item, long amount) {
        BarrelIdentity barrel = mock(BarrelIdentity.class);
        when(barrel.getItemStack()).thenReturn(item);
        when(barrel.getAmount()).thenReturn(amount);
        return barrel;
    }

    private static ItemStack stack(Material material, int amount, int maxStack) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        when(stack.getAmount()).thenReturn(amount);
        when(stack.getMaxStackSize()).thenReturn(maxStack);
        when(stack.hasItemMeta()).thenReturn(false);
        return stack;
    }
}

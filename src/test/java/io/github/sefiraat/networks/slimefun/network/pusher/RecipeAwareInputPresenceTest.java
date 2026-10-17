package io.github.sefiraat.networks.slimefun.network.pusher;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.utils.StackUtils;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.IntStream;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises the production presence gate with real item identity comparisons. */
class RecipeAwareInputPresenceTest {
    private final Map<Integer, ItemStack> contents = new HashMap<>();
    private MockedStatic<Networks> networks;
    private BlockMenu menu;
    private BlockMenuPreset preset;

    @BeforeEach
    void setUp() throws ClassNotFoundException {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        Networks plugin = mock(Networks.class);
        networks.when(Networks::getConfigManager).thenReturn(config);
        networks.when(Networks::getInstance).thenReturn(plugin);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        Logger logger = Logger.getAnonymousLogger();
        logger.setLevel(Level.OFF);
        when(plugin.getLogger()).thenReturn(logger);
        Class.forName(StackUtils.class.getName());

        menu = mock(BlockMenu.class);
        preset = mock(BlockMenuPreset.class);
        when(menu.getPreset()).thenReturn(preset);
        when(menu.getSize()).thenReturn(9);
        when(menu.getItemInSlot(anyInt())).thenAnswer(call -> contents.get((int) call.getArgument(0)));
        when(preset.getSlotsAccessedByItemTransport(ItemTransportFlow.INSERT)).thenReturn(new int[] {0, 1});
        when(preset.getSlotsAccessedByItemTransport(eq(menu), eq(ItemTransportFlow.INSERT), any(ItemStack.class)))
            .thenReturn(new int[] {0, 1});
    }

    @AfterEach
    void tearDown() {
        try {
            if (networks != null) networks.close();
        } finally {
            AbstractNetworkPusher.resetRuntimeState();
            MockBukkit.unmock();
        }
    }

    @Test
    void outputAndDisplayCopiesDoNotUnlockMultipleBatches() {
        contents.put(0, new ItemStack(Material.IRON_INGOT, 9));
        contents.put(2, new ItemStack(Material.GOLD_INGOT, 1));
        contents.put(8, new ItemStack(Material.GOLD_INGOT, 1));

        boolean[] represented = presence(ironAndGold());
        assertArrayEquals(new boolean[] {true, false}, represented);
        assertEquals(0, RecipeAwarePusherPlanner.calculateFairTransferAmount(9, 8, 9, 119, !represented[1]));
        verify(menu, never()).getItemInSlot(2);
        verify(menu, never()).getItemInSlot(8);
        verify(preset, times(1)).getSlotsAccessedByItemTransport(ItemTransportFlow.INSERT);
    }

    @Test
    void realAcceptedInputsRestoreConfiguredBuffer() {
        contents.put(0, new ItemStack(Material.IRON_INGOT, 9));
        contents.put(1, new ItemStack(Material.GOLD_INGOT, 1));

        boolean[] represented = presence(ironAndGold());
        assertArrayEquals(new boolean[] {true, true}, represented);
        assertEquals(63, RecipeAwarePusherPlanner.calculateFairTransferAmount(9, 8, 9, 119, !represented[1]));
    }

    @Test
    void matchingItemInAnInputRejectedForThatIngredientIsNotRepresented() {
        contents.put(0, new ItemStack(Material.GOLD_INGOT, 1));
        when(preset.getSlotsAccessedByItemTransport(eq(menu), eq(ItemTransportFlow.INSERT), any(ItemStack.class)))
            .thenReturn(new int[] {1});

        assertArrayEquals(new boolean[] {false, false}, presence(ironAndGold()));
    }

    @Test
    void acceptedMatchingStackIsFoundAfterAnUnacceptedMatchingStack() {
        contents.put(0, new ItemStack(Material.GOLD_INGOT, 1));
        contents.put(1, new ItemStack(Material.GOLD_INGOT, 1));
        when(preset.getSlotsAccessedByItemTransport(eq(menu), eq(ItemTransportFlow.INSERT), any(ItemStack.class)))
            .thenReturn(new int[] {1});

        assertArrayEquals(new boolean[] {false, true}, presence(ironAndGold()));
        verify(preset, times(1)).getSlotsAccessedByItemTransport(
            eq(menu), eq(ItemTransportFlow.INSERT), any(ItemStack.class));
    }

    @Test
    void itemAwareOutputOrStatusSentinelStillCannotCountAsAnInput() {
        contents.put(0, new ItemStack(Material.GOLD_INGOT, 1));
        contents.put(8, new ItemStack(Material.GOLD_INGOT, 1));
        when(preset.getSlotsAccessedByItemTransport(eq(menu), eq(ItemTransportFlow.INSERT), any(ItemStack.class)))
            .thenReturn(new int[] {8});

        assertArrayEquals(new boolean[] {false, false}, presence(ironAndGold()));
        verify(menu, never()).getItemInSlot(8);
    }

    @Test
    void nullItemRoutingIsNeverUsedAsThePhysicalInputDeclaration() {
        contents.put(0, new ItemStack(Material.IRON_INGOT, 9));
        when(preset.getSlotsAccessedByItemTransport(eq(menu), eq(ItemTransportFlow.INSERT), isNull()))
            .thenThrow(new IllegalArgumentException("This preset requires a concrete item"));

        assertArrayEquals(new boolean[] {true, false}, presence(ironAndGold()));
        verify(preset, never()).getSlotsAccessedByItemTransport(
            eq(menu), eq(ItemTransportFlow.INSERT), isNull());
    }

    @Test
    void unavailablePhysicalInputDeclarationFailsConservatively() {
        contents.put(0, new ItemStack(Material.IRON_INGOT, 9));
        when(preset.getSlotsAccessedByItemTransport(ItemTransportFlow.INSERT))
            .thenThrow(new UnsupportedOperationException("No physical input declaration"));

        assertArrayEquals(new boolean[] {false, false}, presence(ironAndGold()));
        verify(preset, never()).getSlotsAccessedByItemTransport(
            eq(menu), eq(ItemTransportFlow.INSERT), any(ItemStack.class));
    }

    @Test
    void nullPhysicalInputDeclarationDoesNotScanTheMenu() {
        when(preset.getSlotsAccessedByItemTransport(ItemTransportFlow.INSERT)).thenReturn(null);
        assertArrayEquals(new boolean[] {false, false}, presence(ironAndGold()));
        verify(menu, never()).getItemInSlot(anyInt());
    }

    @Test
    void invalidAndDuplicateDeclaredInputsStaySafeAndQueryEachTypeOnce() {
        contents.put(0, new ItemStack(Material.IRON_INGOT, 9));
        contents.put(1, new ItemStack(Material.GOLD_INGOT, 1));
        when(preset.getSlotsAccessedByItemTransport(ItemTransportFlow.INSERT))
            .thenReturn(new int[] {-1, 0, 0, 99, 1});

        assertArrayEquals(new boolean[] {true, true}, presence(ironAndGold()));
        verify(menu, never()).getItemInSlot(-1);
        verify(menu, never()).getItemInSlot(99);
        verify(preset, times(2)).getSlotsAccessedByItemTransport(
            eq(menu), eq(ItemTransportFlow.INSERT), any(ItemStack.class));
    }

    @Test
    void aDifferentPersistentIdentityIsNotARecipeIngredient() {
        ItemStack template = taggedGold("required");
        contents.put(0, taggedGold("display-copy"));
        List<AbstractNetworkPusher.PushRequest> requests = List.of(
            new AbstractNetworkPusher.PushRequest(template, 1));

        assertArrayEquals(new boolean[] {false}, presence(requests));
        verify(preset, never()).getSlotsAccessedByItemTransport(
            eq(menu), eq(ItemTransportFlow.INSERT), any(ItemStack.class));
    }

    @Test
    void discoveryBudgetDoesNotExpandToEveryLargePusherTemplate() {
        Material[] materials = {Material.IRON_INGOT, Material.GOLD_INGOT, Material.DIAMOND, Material.EMERALD,
            Material.COAL, Material.REDSTONE, Material.LAPIS_LAZULI, Material.QUARTZ, Material.COPPER_INGOT,
            Material.NETHERITE_INGOT, Material.AMETHYST_SHARD, Material.BRICK};
        int[] inputs = IntStream.range(0, materials.length).toArray();
        List<AbstractNetworkPusher.PushRequest> requests = new ArrayList<>();
        for (int i = 0; i < materials.length; i++) {
            ItemStack item = new ItemStack(materials[i], 1);
            contents.put(i, item.clone());
            requests.add(new AbstractNetworkPusher.PushRequest(item, 1));
        }
        when(menu.getSize()).thenReturn(18);
        when(preset.getSlotsAccessedByItemTransport(ItemTransportFlow.INSERT)).thenReturn(inputs);
        when(preset.getSlotsAccessedByItemTransport(eq(menu), eq(ItemTransportFlow.INSERT), any(ItemStack.class)))
            .thenReturn(inputs);

        boolean[] represented = presence(requests);
        for (int i = 0; i < materials.length; i++) {
            assertEquals(i < 4, represented[i], "Unverified types must remain missing");
        }
        verify(preset, times(4)).getSlotsAccessedByItemTransport(
            eq(menu), eq(ItemTransportFlow.INSERT), any(ItemStack.class));
        // A later transfer's already-fresh slot result can confirm its type without rediscovery.
        assertTrue(AbstractNetworkPusher.isRecipeIngredientPresent(
            menu, new ItemStack(materials[11], 1), inputs, inputs));
        verify(preset, times(4)).getSlotsAccessedByItemTransport(
            eq(menu), eq(ItemTransportFlow.INSERT), any(ItemStack.class));
    }

    private boolean[] presence(List<AbstractNetworkPusher.PushRequest> requests) {
        return AbstractNetworkPusher.findRepresentedRecipeIngredients(
            menu, requests, AbstractNetworkPusher.getRecipeInputSlots(menu));
    }

    private static List<AbstractNetworkPusher.PushRequest> ironAndGold() {
        return List.of(
            new AbstractNetworkPusher.PushRequest(new ItemStack(Material.IRON_INGOT, 1), 9),
            new AbstractNetworkPusher.PushRequest(new ItemStack(Material.GOLD_INGOT, 1), 1));
    }

    private static ItemStack taggedGold(String value) {
        ItemStack stack = new ItemStack(Material.GOLD_INGOT, 1);
        ItemMeta meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("test", "identity"), PersistentDataType.STRING, value);
        stack.setItemMeta(meta);
        return stack;
    }
}

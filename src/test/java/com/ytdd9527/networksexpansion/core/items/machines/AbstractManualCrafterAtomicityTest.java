package com.ytdd9527.networksexpansion.core.items.machines;

import com.balugaq.netex.api.data.SuperRecipe;
import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.api.interfaces.FeedbackSendable;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.balugaq.netex.utils.Lang;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.events.NetworkCraftEvent;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AbstractManualCrafterAtomicityTest {
    private static final int[] INPUT_SLOTS = {0, 1, 2};
    private static final int[] OUTPUT_SLOTS = {8};

    @BeforeAll
    static void initializeFeedbackTypes() throws ClassNotFoundException {
        try (MockedStatic<Networks> networks = mockStatic(Networks.class);
             MockedStatic<SupportedPluginManager> integrations = mockStatic(SupportedPluginManager.class);
             MockedStatic<Lang> lang = mockStatic(Lang.class)) {
            Networks plugin = mock(Networks.class);
            SupportedPluginManager supported = mock(SupportedPluginManager.class);
            networks.when(Networks::getInstance).thenReturn(plugin);
            networks.when(Networks::getSupportedPluginManager).thenReturn(supported);
            integrations.when(SupportedPluginManager::getInstance).thenReturn(supported);
            when(plugin.getName()).thenReturn("Networks");
            when(plugin.namespace()).thenReturn("networks");
            when(supported.isInfinityExpansion()).thenReturn(false);
            lang.when(() -> Lang.getString(org.mockito.ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> invocation.getArgument(0));

            Class.forName(FeedbackType.class.getName());
        }
    }

    private AbstractManualCrafter crafter;
    private BlockMenu menu;
    private Player player;
    private World world;
    private Location location;

    @BeforeEach
    void setup() {
        crafter = mock(AbstractManualCrafter.class, CALLS_REAL_METHODS);
        menu = mock(BlockMenu.class);
        player = mock(Player.class);
        world = mock(World.class);
        location = new Location(world, 4, 64, 9);

        when(crafter.getInputSlots()).thenReturn(INPUT_SLOTS);
        when(crafter.getOutputSlots()).thenReturn(OUTPUT_SLOTS);
        when(menu.getLocation()).thenReturn(location);
        FeedbackSendable.SUBSCRIBED_LOCATIONS.clear();
    }

    private static ItemStack stack(Material material, int amount) {
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(material);
        when(stack.getAmount()).thenReturn(amount);
        return stack;
    }

    @Test
    void cancelledShapedCraftDoesNotConsumeInputsOrEmitEarlierOutputs() {
        ItemStack input = stack(Material.DIAMOND, 1);
        ItemStack firstOutput = stack(Material.STONE, 1);
        ItemStack secondOutput = stack(Material.DIRT, 1);
        SuperRecipe recipe = new SuperRecipe(true, new ItemStack[]{input}, new ItemStack[]{firstOutput, secondOutput});
        when(menu.getItemInSlot(0)).thenReturn(input);

        PluginManager pluginManager = mock(PluginManager.class);
        AtomicInteger events = new AtomicInteger();
        doAnswer(invocation -> {
            NetworkCraftEvent event = invocation.getArgument(0);
            if (events.incrementAndGet() == 2) {
                event.setCancelled(true);
            }
            return null;
        }).when(pluginManager).callEvent(any());

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedStatic<BlockMenuUtil> menus = mockStatic(BlockMenuUtil.class);
             MockedStatic<StackUtils> stacks = mockStatic(StackUtils.class);
             MockedStatic<SlimefunItem> slimefunItems = mockStatic(SlimefunItem.class)) {
            bukkit.when(Bukkit::getPluginManager).thenReturn(pluginManager);
            menus.when(() -> BlockMenuUtil.fits(menu, recipe.getOutput(), OUTPUT_SLOTS)).thenReturn(true);
            stacks.when(() -> StackUtils.itemsMatch(input, input, false, true)).thenReturn(true);
            slimefunItems.when(() -> SlimefunItem.getByItem(firstOutput)).thenReturn(null);
            slimefunItems.when(() -> SlimefunItem.getByItem(secondOutput)).thenReturn(null);

            assertFalse(crafter.shapedCraft(recipe, player, menu));
            menus.verify(() -> BlockMenuUtil.consumeItem(menu, 0, 1), never());
            menus.verify(() -> BlockMenuUtil.pushItem(any(BlockMenu.class), any(ItemStack.class), any(int[].class)), never());
            verify(player, never()).sendMessage(any(String.class));
        }
    }

    @Test
    void missingShapelessIngredientFailsBeforeOutputOrConsumption() {
        ItemStack input = stack(Material.DIAMOND, 1);
        ItemStack output = stack(Material.STONE, 1);
        SuperRecipe recipe = new SuperRecipe(false, new ItemStack[]{input}, output);

        try (MockedStatic<BlockMenuUtil> menus = mockStatic(BlockMenuUtil.class)) {
            assertFalse(crafter.shapelessCraft(recipe, player, menu));
            menus.verify(() -> BlockMenuUtil.fits(menu, recipe.getOutput(), OUTPUT_SLOTS), never());
            menus.verify(() -> BlockMenuUtil.consumeItem(any(BlockMenu.class), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt()), never());
            menus.verify(() -> BlockMenuUtil.pushItem(any(BlockMenu.class), any(ItemStack.class), any(int[].class)), never());
        }
    }

    @Test
    void multiIngredientShapelessCraftConsumesWithoutMutatingRequirementMap() {
        ItemStack diamond = stack(Material.DIAMOND, 1);
        ItemStack gold = stack(Material.GOLD_INGOT, 1);
        ItemStack output = stack(Material.STONE, 1);
        SuperRecipe recipe = new SuperRecipe(false, new ItemStack[]{diamond, gold}, output);
        when(menu.getItemInSlot(0)).thenReturn(diamond);
        when(menu.getItemInSlot(1)).thenReturn(gold);

        try (MockedStatic<BlockMenuUtil> menus = mockStatic(BlockMenuUtil.class);
             MockedStatic<StackUtils> stacks = mockStatic(StackUtils.class);
             MockedStatic<SlimefunItem> slimefunItems = mockStatic(SlimefunItem.class);
             MockedStatic<Lang> lang = mockStatic(Lang.class)) {
            menus.when(() -> BlockMenuUtil.fits(menu, recipe.getOutput(), OUTPUT_SLOTS)).thenReturn(true);
            menus.when(() -> BlockMenuUtil.pushItem(menu, output, OUTPUT_SLOTS)).thenReturn(null);
            stacks.when(() -> StackUtils.itemsMatch(diamond, diamond)).thenReturn(true);
            stacks.when(() -> StackUtils.itemsMatch(diamond, gold)).thenReturn(false);
            stacks.when(() -> StackUtils.itemsMatch(gold, diamond)).thenReturn(false);
            stacks.when(() -> StackUtils.itemsMatch(gold, gold)).thenReturn(true);
            slimefunItems.when(() -> SlimefunItem.getByItem(output)).thenReturn(null);
            lang.when(() -> Lang.getString("messages.completed-operation.manual_crafter.success"))
                .thenReturn("success");

            assertTrue(crafter.shapelessCraft(recipe, player, menu));
            menus.verify(() -> BlockMenuUtil.consumeItem(menu, 0, 1), times(1));
            menus.verify(() -> BlockMenuUtil.consumeItem(menu, 1, 1), times(1));
            menus.verify(() -> BlockMenuUtil.pushItem(menu, output, OUTPUT_SLOTS), times(1));
        }
    }
}

package io.github.sefiraat.networks.slimefun.network.pusher;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.utils.StackUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Full recipe inputs remain counted when dynamic routing selects only available space. */
class RecipeAwareFullInputBufferTest {
    private final Map<Integer, ItemStack> contents = new HashMap<>();
    private MockedStatic<Networks> networks;
    private BlockMenu menu;

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
        when(menu.getSize()).thenReturn(9);
        when(menu.getItemInSlot(anyInt())).thenAnswer(call -> contents.get((int) call.getArgument(0)));
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
    void reenabledModeDoesNotRefillAfterOffFilledTheMatchingStack() {
        contents.put(0, new ItemStack(Material.IRON_INGOT, 64));
        int existing = count(new ItemStack(Material.IRON_INGOT), new int[] {0, 1}, new int[] {1});

        assertEquals(64, existing);
        assertEquals(0, RecipeAwarePusherPlanner.calculateFairTransferAmount(1, 5, existing, 64, true));
        assertEquals(64, contents.get(0).getAmount());
        verify(menu, never()).replaceExistingItem(anyInt(), any(ItemStack.class));
    }

    @Test
    void fullAndPartialPhysicalStacksBothCountTowardTheTarget() {
        contents.put(0, new ItemStack(Material.IRON_INGOT, 64));
        contents.put(1, new ItemStack(Material.IRON_INGOT, 4));
        int existing = count(new ItemStack(Material.IRON_INGOT), new int[] {0, 1, 2}, new int[] {1});

        assertEquals(68, existing);
        assertEquals(4, RecipeAwarePusherPlanner.calculateFairTransferAmount(9, 8, existing, 60, false));
    }

    @Test
    void outputAndDisplayCopiesCannotInflateTheBuffer() {
        contents.put(0, new ItemStack(Material.IRON_INGOT, 4));
        contents.put(7, new ItemStack(Material.IRON_INGOT, 64));
        contents.put(8, new ItemStack(Material.IRON_INGOT, 64));

        assertEquals(4, count(new ItemStack(Material.IRON_INGOT), new int[] {0, 1}, new int[] {1, 7, 8}));
        verify(menu, never()).getItemInSlot(7);
        verify(menu, never()).getItemInSlot(8);
    }

    @Test
    void absentPhysicalDeclarationRetainsTransportFallback() {
        contents.put(0, new ItemStack(Material.IRON_INGOT, 64));
        contents.put(1, new ItemStack(Material.IRON_INGOT, 3));

        assertEquals(3, count(new ItemStack(Material.IRON_INGOT), new int[0], new int[] {1}));
        verify(menu, never()).getItemInSlot(0);
    }

    @Test
    void entirelyInvalidDeclarationRetainsExistingTransportCount() {
        contents.put(1, new ItemStack(Material.IRON_INGOT, 3));

        assertEquals(3, count(new ItemStack(Material.IRON_INGOT), new int[] {-1, 99}, new int[] {1}));
        verify(menu, never()).getItemInSlot(-1);
        verify(menu, never()).getItemInSlot(99);
    }

    @Test
    void invalidAndDuplicateInputsNeverInventAdditionalQuantity() {
        contents.put(0, new ItemStack(Material.IRON_INGOT, 64));

        assertEquals(64, count(new ItemStack(Material.IRON_INGOT), new int[] {-1, 0, 0, 99}, new int[] {1}));
        verify(menu, times(1)).getItemInSlot(0);
        verify(menu, never()).getItemInSlot(-1);
        verify(menu, never()).getItemInSlot(99);
    }

    @Test
    void countingExistingStockDoesNotGrantPermissionForARejectedInput() {
        ItemStack template = new ItemStack(Material.IRON_INGOT);
        contents.put(0, new ItemStack(Material.IRON_INGOT, 64));

        assertEquals(64, count(template, new int[] {0, 1}, new int[] {1}));
        assertFalse(AbstractNetworkPusher.isRecipeIngredientPresent(menu, template, new int[] {0, 1}, new int[] {1}));
    }

    @Test
    void differentPersistentIdentityDoesNotCountAsBufferedStock() {
        ItemStack template = tagged("required");
        contents.put(0, tagged("foreign"));

        assertEquals(0, count(template, new int[] {0, 1}, new int[] {1}));
    }

    private int count(ItemStack template, int[] physical, int[] accepted) {
        return AbstractNetworkPusher.countRecipeInputItems(menu, template, physical, accepted);
    }

    private static ItemStack tagged(String value) {
        ItemStack stack = new ItemStack(Material.IRON_INGOT, 64);
        var meta = stack.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("test", "identity"), PersistentDataType.STRING, value);
        stack.setItemMeta(meta);
        return stack;
    }
}

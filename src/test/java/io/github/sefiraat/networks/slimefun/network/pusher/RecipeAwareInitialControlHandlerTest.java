package io.github.sefiraat.networks.slimefun.network.pusher;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicReference;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ChestMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
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

/** The first viewer tick must release an old foreign item without changing its identity. */
class RecipeAwareInitialControlHandlerTest {
    private MockedStatic<StorageCacheUtils> storage;
    private AbstractNetworkPusher pusher;
    private BlockMenu menu;
    private Location location;
    private ItemStack foreign;
    private final AtomicReference<ChestMenu.MenuClickHandler> handler = new AtomicReference<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws ReflectiveOperationException {
        var server = MockBukkit.mock();
        location = new Location(server.addSimpleWorld("recipe-handler"), 4, 64, 4);
        foreign = new ItemStack(Material.DIAMOND, 13);
        foreign.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("otheraddon", "owner"), PersistentDataType.STRING, "preserve"));
        menu = mock(BlockMenu.class);
        when(menu.hasViewer()).thenReturn(true);
        when(menu.getLocation()).thenReturn(location);
        when(menu.getItemInSlot(16)).thenReturn(foreign);
        doAnswer(call -> {
            handler.set(call.getArgument(1));
            return null;
        }).when(menu).addMenuClickHandler(eq(16), any(ChestMenu.MenuClickHandler.class));

        pusher = mock(AbstractNetworkPusher.class, CALLS_REAL_METHODS);
        doReturn("TEST_RECIPE_PUSHER").when(pusher).getId();
        doReturn(new int[] {16}).when(pusher).getOtherBackgroundSlots();
        doReturn(null).when(pusher).getOtherBackgroundStack();
        ItemSetting<Integer> batches = mock(ItemSetting.class);
        when(batches.getValue()).thenReturn(8);
        Field setting = AbstractNetworkPusher.class.getDeclaredField("recipeBufferBatches");
        setting.setAccessible(true);
        setting.set(pusher, batches);
        storage = mockStatic(StorageCacheUtils.class);
        storage.when(() -> StorageCacheUtils.isBlock(location, "TEST_RECIPE_PUSHER")).thenReturn(true);
        storage.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(menu);
    }

    @AfterEach
    void tearDown() {
        try {
            if (storage != null) storage.close();
        } finally {
            AbstractNetworkPusher.resetRuntimeState();
            MockBukkit.unmock();
        }
    }

    @Test
    void firstForeignStackReceivesAnOrdinaryPickupHandler() throws ReflectiveOperationException {
        ItemStack original = foreign.clone();
        updateControl();

        assertNotNull(handler.get(), "A preexisting foreign stack must not retain the locked backdrop handler");
        assertTrue(handler.get().onClick(mock(Player.class), 16, foreign, mock(ClickAction.class)));
        assertEquals(original, foreign, "The actual quantity and typed owner metadata must survive");
        verify(menu, never()).replaceExistingItem(anyInt(), any(ItemStack.class));
        storage.verify(() -> StorageCacheUtils.setData(any(Location.class), anyString(), anyString()), never());
    }

    @Test
    void staleMenuStillCannotRecoverOrToggleThroughAReplacementBlock() throws ReflectiveOperationException {
        ItemStack original = foreign.clone();
        updateControl();
        assertNotNull(handler.get());
        storage.when(() -> StorageCacheUtils.getMenu(location)).thenReturn(mock(BlockMenu.class));

        assertFalse(handler.get().onClick(mock(Player.class), 16, foreign, mock(ClickAction.class)));
        assertEquals(original, foreign);
        verify(menu, never()).replaceExistingItem(anyInt(), any(ItemStack.class));
        storage.verify(() -> StorageCacheUtils.setData(any(Location.class), anyString(), anyString()), never());
    }

    private void updateControl() throws ReflectiveOperationException {
        Method update = AbstractNetworkPusher.class.getDeclaredMethod("updateRecipeAwareControl", BlockMenu.class);
        update.setAccessible(true);
        update.invoke(pusher, menu);
    }
}

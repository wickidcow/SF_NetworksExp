package io.github.sefiraat.networks.slimefun.network.grid;

import com.balugaq.netex.api.enums.MinecraftVersion;
import com.ytdd9527.networksexpansion.core.managers.ConfigManager;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.utils.StackUtils;
import java.util.concurrent.atomic.AtomicReference;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.mockbukkit.mockbukkit.MockBukkit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GridCraftingContainerReceiptTest {
    private final AtomicReference<ItemStack> stored = new AtomicReference<>();
    private MockedStatic<Networks> networks;
    private BlockMenu menu;

    @BeforeEach
    void setUp() throws ClassNotFoundException {
        MockBukkit.mock();
        networks = mockStatic(Networks.class);
        Networks plugin = mock(Networks.class);
        ConfigManager config = mock(ConfigManager.class);
        networks.when(Networks::getInstance).thenReturn(plugin);
        networks.when(Networks::getConfigManager).thenReturn(config);
        when(plugin.getMCVersion()).thenReturn(MinecraftVersion.UNKNOWN);
        when(config.useBukkitItemComparison()).thenReturn(true);
        Class.forName(StackUtils.class.getName());
        menu = mock(BlockMenu.class);
        when(menu.getItemInSlot(0)).thenAnswer(invocation -> {
            ItemStack item = stored.get();
            return item == null ? null : item.clone();
        });
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
    void changingOnlyMetadataDoesNotConsumeTheLastIngredient() {
        stored.set(new ItemStack(Material.STONE, 1));
        ItemStack required = stored.get().clone();
        doAnswer(invocation -> {
            var changed = stored.get().clone();
            var meta = changed.getItemMeta();
            meta.displayName(Component.text("Changed but not consumed"));
            changed.setItemMeta(meta);
            stored.set(changed);
            return null;
        }).when(menu).consumeItem(0, 1, true);
        assertThrows(IllegalStateException.class,
            () -> GridCraftingCommitGuard.consumeVerified(menu, 0, required));
        verify(menu, never()).markDirty();
    }

    @Test
    void arbitraryOneItemReplacementIsNotAContainerReceipt() {
        stored.set(new ItemStack(Material.STONE, 1));
        doAnswer(invocation -> {
            stored.set(new ItemStack(Material.EMERALD, 1));
            return null;
        }).when(menu).consumeItem(0, 1, true);
        assertThrows(IllegalStateException.class,
            () -> GridCraftingCommitGuard.consumeVerified(menu, 0, new ItemStack(Material.STONE, 1)));
    }

    @ParameterizedTest
    @CsvSource({"WATER_BUCKET,BUCKET", "MILK_BUCKET,BUCKET", "HONEY_BOTTLE,GLASS_BOTTLE",
        "MUSHROOM_STEW,BOWL", "POTION,GLASS_BOTTLE"})
    void supportedVanillaContainerFormsRemainValid(Material input, Material output) {
        stored.set(new ItemStack(input, 1));
        doAnswer(invocation -> {
            stored.set(new ItemStack(output, 1));
            return null;
        }).when(menu).consumeItem(0, 1, true);
        assertDoesNotThrow(() -> GridCraftingCommitGuard.consumeVerified(menu, 0, new ItemStack(input, 1)));
        assertEquals(output, stored.get().getType());
    }
}

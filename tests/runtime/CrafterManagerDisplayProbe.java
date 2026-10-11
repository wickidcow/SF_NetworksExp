package networksprobe;

import com.ytdd9527.networksexpansion.core.items.machines.AutoCrafter;
import com.ytdd9527.networksexpansion.implementation.machines.managers.CrafterManager;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.network.stackcaches.BlueprintInstance;
import io.github.sefiraat.networks.slimefun.network.grid.GridCache;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable-server regression: uses the real manager, menu and CraftInventory. Never install on a live server. */
public final class CrafterManagerDisplayProbe extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> {
            String result;
            try {
                verify();
                result = "PASS: real CrafterManager display writes, metadata, counts and source isolation";
            } catch (Throwable failure) {
                result = "FAIL: " + failure;
                failure.printStackTrace();
            }
            getLogger().info(result);
            try {
                Files.writeString(getDataFolder().toPath().resolveSibling("crafter-display-result.txt"), result + "\n");
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            } finally {
                Bukkit.shutdown();
            }
        });
    }

    private void verify() {
        var manager = (CrafterManager) SlimefunItem.getById("NTW_EXPANSION_CRAFTER_MANAGER");
        require(manager != null, "Crafter Manager must be registered");
        var world = Bukkit.getWorlds().getFirst();
        var location = new Location(world, 0, 100, 0);
        var crafterLocation = new Location(world, 1, 100, 0);
        world.getChunkAt(location).load();
        var preset = new BlockMenuPreset("NETWORKS_DISPLAY_PROBE", "Display probe") {
            @Override public void init() { setSize(54); }
            @Override public boolean canOpen(Block block, Player player) { return false; }
            @Override public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) { return new int[0]; }
        };
        var menu = new BlockMenu(preset, location) {
            @Override public boolean hasViewer() { return true; }
        };
        var crafter = new BlockMenu(preset, crafterLocation);
        crafter.replaceExistingItem(AutoCrafter.BLUEPRINT_SLOT, new ItemStack(Material.PAPER));
        var root = new NetworkRoot(location, NodeType.CONTROLLER, 16) {
            @Override public Set<BlockMenu> getCrafterOutputs() { return Set.of(crafter); }
        };
        var definition = new NodeDefinition(NodeType.CONTROLLER);
        definition.setNode(root);
        NetworkStorage.registerNode(location, definition);
        manager.getCacheMap().put(location, new GridCache(0, 0, GridCache.SortOrder.ALPHABETICAL));

        var source = new SlimefunItemStack("NETWORKS_DISPLAY_PROBE_ITEM", Material.DIAMOND,
            "&aStored output", "&7Original lore");
        source.setAmount(7);
        ItemMeta meta = source.getItemMeta();
        var opaqueKey = new NamespacedKey("oldaddon", "opaque_payload");
        meta.getPersistentDataContainer().set(opaqueKey, PersistentDataType.BYTE_ARRAY, new byte[]{1, 7, 42});
        var model = meta.getCustomModelDataComponent();
        model.setFloats(List.of(1234.5F));
        model.setStrings(List.of("preserved-model"));
        meta.setCustomModelDataComponent(model);
        source.setItemMeta(meta);
        ItemMeta before = source.getItemMeta();
        AutoCrafter.INSTANCE_MAP.put(crafterLocation, new BlueprintInstance(null, source));
        try {
            manager.updateDisplay(null);
            for (int attempt = 0; attempt < 20; attempt++) {
                manager.updateDisplay(menu);
                ItemStack stored = menu.getItemInSlot(manager.getDisplaySlots()[0]);
                require(stored != null && stored.getType() == source.getType(), "display material");
                require(stored.getAmount() == 7, "display amount");
                ItemMeta expected = source.getItemMeta();
                // The manager deliberately replaces only the display name and explanatory lore.
                expected.displayName(stored.getItemMeta().displayName());
                expected.lore(stored.getItemMeta().lore());
                require(expected.equals(stored.getItemMeta()), "complete metadata outside presentation");
                require(menu.getMenuClickHandler(manager.getDisplaySlots()[0]) != null, "click handler");
                require(menu.isDirty(), "dirty tracking");
            }
            require(before.equals(source.getItemMeta()) && source.getAmount() == 7, "source must remain unchanged");
            menu.getItemInSlot(manager.getDisplaySlots()[0]).editMeta(m -> m.displayName(Component.text("Changed")));
            require(before.equals(source.getItemMeta()), "display must not alias source metadata");

            AutoCrafter.INSTANCE_MAP.put(crafterLocation, new BlueprintInstance(null, new ItemStack(Material.STONE, 13)));
            manager.updateDisplay(menu);
            require(menu.getItemInSlot(manager.getDisplaySlots()[0]).getAmount() == 13, "ordinary output count");
        } finally {
            AutoCrafter.INSTANCE_MAP.remove(crafterLocation);
            manager.getCacheMap().remove(location);
            NetworkStorage.removeNode(location);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

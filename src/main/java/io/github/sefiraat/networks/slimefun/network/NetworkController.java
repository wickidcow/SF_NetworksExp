package io.github.sefiraat.networks.slimefun.network;

import com.balugaq.netex.api.data.ItemFlowRecord;
import com.balugaq.netex.api.events.NetworkRootReadyEvent;
import com.balugaq.netex.utils.Lang;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkNode;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import lombok.Getter;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Getter
public class NetworkController extends NetworkObject {
    @Getter
    private static final Map<Location, ItemFlowRecord> records = new ConcurrentHashMap<>();

    @Getter
    private static final Map<Location, Boolean> recordFlow = new ConcurrentHashMap<>();

    private static final String CRAYON = "crayon";
    private static final Map<Location, NetworkRoot> NETWORKS = new ConcurrentHashMap<>();
    private static final Set<Location> CRAYONS = ConcurrentHashMap.newKeySet();
    protected final Map<Location, Boolean> firstTickMap = new ConcurrentHashMap<>();

    @Getter
    private final @NotNull ItemSetting<Integer> maxNodes;

    public NetworkController(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack @NotNull [] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.CONTROLLER);

        maxNodes = new IntRangeSetting(this, "max_nodes", 10, 8000, 50000);
        addItemSetting(maxNodes);

        addItemHandler(new BlockTicker() {
            @Override
            public boolean isSynchronized() {
                return io.github.sefiraat.networks.Networks.getConfigManager().useSynchronizedMachineTickers();
            }

            @Override
            public void tick(@NotNull Block block, SlimefunItem item, @NotNull SlimefunBlockData data) {
                if (block.getType() == Material.AIR || !getId().equals(data.getSfId())) {
                    firstTickMap.remove(block.getLocation());
                    removeRuntimeState(block.getLocation());
                    return;
                }

                if (!firstTickMap.containsKey(block.getLocation())) {
                    onFirstTick(block, data);
                    firstTickMap.put(block.getLocation(), true);
                }

                addToRegistry(block);
                NetworkRoot networkRoot = new NetworkRoot(
                    block.getLocation(),
                    NodeType.CONTROLLER,
                    maxNodes.getValue(),
                    recordFlow.getOrDefault(block.getLocation(), false),
                    records.get(block.getLocation()));
                networkRoot.addAllChildren();

                boolean crayon = CRAYONS.contains(block.getLocation());
                if (crayon) {
                    networkRoot.setDisplayParticles(true);
                }

                NETWORKS.put(block.getLocation(), networkRoot);

                NodeDefinition definition = NetworkStorage.getNode(block.getLocation());
                if (definition != null) {
                    definition.setNode(networkRoot);
                }
                NetworkRootReadyEvent event = new NetworkRootReadyEvent(networkRoot);
                Bukkit.getPluginManager().callEvent(event);
            }
        });
    }

    public static void enableRecord(Location root) {
        recordFlow.put(root, true);
        records.putIfAbsent(root, new ItemFlowRecord());
    }

    public static void disableRecord(Location root) {
        recordFlow.put(root, false);
        ItemFlowRecord record = records.get(root);
        if (record != null) {
            record.forceGC();
        }
    }

    public static @NotNull Map<Location, NetworkRoot> getNetworks() {
        return NETWORKS;
    }

    public static @NotNull Set<Location> getCrayons() {
        return CRAYONS;
    }

    public static void addCrayon(@NotNull Location location) {
        StorageCacheUtils.setData(location, CRAYON, String.valueOf(true));
        CRAYONS.add(location);
    }

    public static void removeCrayon(@NotNull Location location) {
        StorageCacheUtils.removeData(location, CRAYON);
        CRAYONS.remove(location);
    }

    public static boolean hasCrayon(@NotNull Location location) {
        return CRAYONS.contains(location);
    }

    public static void wipeNetwork(@NotNull Location location) {
        NetworkRoot networkRoot = NETWORKS.remove(location);
        if (networkRoot != null) {
            for (NetworkNode node : networkRoot.getChildrenNodes()) {
                NetworkStorage.removeNode(node.getNodePosition());
            }
        }
    }

    /** Clears every runtime cache associated with a controller location. */
    public static void removeRuntimeState(@NotNull Location location) {
        wipeNetwork(location);
        NETWORKS.remove(location);
        records.remove(location);
        recordFlow.remove(location);
        CRAYONS.remove(location);
    }

    @Override
    protected void postBreak(@NotNull BlockBreakEvent event) {
        super.postBreak(event);
        Location location = event.getBlock().getLocation();
        firstTickMap.remove(location);
        removeRuntimeState(location);
    }

    @SuppressWarnings("unused")
    @Override
    protected void cancelPlace(@NotNull BlockPlaceEvent event) {
        event.getPlayer().sendMessage(Lang.getString("messages.unsupported-operation.controller.cancel_place"));
        event.setCancelled(true);
    }

    private void onFirstTick(@NotNull Block block, @NotNull SlimefunBlockData data) {
        final String crayon = data.getData(CRAYON);
        if (Boolean.parseBoolean(crayon)) {
            CRAYONS.add(block.getLocation());
        }
    }
}

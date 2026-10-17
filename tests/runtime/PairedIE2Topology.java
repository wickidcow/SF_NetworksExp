package networksprobe;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.slimefun.network.NetworkMonitor;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.Comparator;
import java.util.Set;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/** Real physical controller/monitor discovery, confined to the disposable paired fixture. */
final class PairedIE2Topology {
    private final Location storage;
    private final Location controller;
    private final Location eastMonitor;
    private final Location southMonitor;
    private final SlimefunItem controllerItem;
    private final SlimefunItem monitorItem;

    PairedIE2Topology(Location storage) {
        require("disposable".equals(System.getProperty("networks.paired.probe")),
            "Topology fixture must never run against a production world");
        this.storage = storage.clone();
        controller = storage.clone().add(1, 0, 1);
        eastMonitor = storage.clone().add(1, 0, 0);
        southMonitor = storage.clone().add(0, 0, 1);
        controllerItem = registered(NetworkController.class);
        monitorItem = registered(NetworkMonitor.class);
    }

    private static SlimefunItem registered(Class<? extends SlimefunItem> type) {
        return Slimefun.getRegistry().getAllSlimefunItems().stream()
            .filter(item -> item.getClass() == type)
            .sorted(Comparator.comparing(SlimefunItem::getId)).findFirst().orElseThrow();
    }

    void create() {
        require(Bukkit.isPrimaryThread(), "Physical test records must be created on the server thread");
        create(eastMonitor, monitorItem);
        create(southMonitor, monitorItem);
        create(controller, controllerItem);
    }

    private static void create(Location location, SlimefunItem item) {
        var database = Slimefun.getDatabaseManager().getBlockDataController();
        require(StorageCacheUtils.getBlock(location) == null, "New topology node must have no previous record");
        location.getBlock().setType(item.getItem().getType());
        var data = database.createBlock(location, item.getId());
        // Preserve the same normal scheduled initialization used by real nodes.
        // Do not fabricate NodeDefinitions or write a fixture barrel set.
        if (!data.isDataLoaded()) database.loadBlockData(data);
    }

    boolean ready() {
        if (!loaded(eastMonitor, monitorItem) || !loaded(southMonitor, monitorItem)
            || !loaded(controller, controllerItem)) return false;
        if (StorageCacheUtils.getMenu(eastMonitor) == null || StorageCacheUtils.getMenu(southMonitor) == null) {
            return false;
        }
        // Use the registered items' actual BlockTickers. Their own first-tick
        // registration may be deferred; the outer fixture waits for it.
        tick(monitorItem, eastMonitor);
        tick(monitorItem, southMonitor);
        if (NetworkStorage.getValidatedNode(eastMonitor) == null
            || NetworkStorage.getValidatedNode(southMonitor) == null) return false;
        tick(controllerItem, controller);
        var root = NetworkController.getNetworks().get(controller);
        return root != null && root.getMonitors().containsAll(Set.of(eastMonitor, southMonitor));
    }

    private static boolean loaded(Location location, SlimefunItem item) {
        var data = StorageCacheUtils.getBlock(location);
        return data != null && data.isDataLoaded() && item.getId().equals(data.getSfId())
            && location.getBlock().getType() == item.getItem().getType();
    }

    private static void tick(SlimefunItem item, Location location) {
        require(Bukkit.isPrimaryThread(), "Production network ticker must stay on the server thread");
        BlockTicker ticker = item.getBlockTicker();
        require(ticker != null && ticker.isSynchronized(), "Registered network node must have a synchronized ticker");
        var data = StorageCacheUtils.getBlock(location);
        require(data != null && data.isDataLoaded(), "Tick only loaded authoritative block data");
        ticker.tick(location.getBlock(), item, data);
    }

    NetworkRoot resolve() {
        tick(controllerItem, controller);
        var root = NetworkController.getNetworks().get(controller);
        require(root != null && root.getClass() == NetworkRoot.class,
            "Use the actual published controller root, not a fixture subclass");
        require(root.getNodeCount() == 3, "Discover exactly one controller and two connected monitors");
        require(root.getMonitors().equals(Set.of(eastMonitor, southMonitor)),
            "Both physical monitors must be discovered through adjacency");
        for (var nodeLocation : Set.of(controller, eastMonitor, southMonitor)) {
            var definition = NetworkStorage.getValidatedNode(nodeLocation);
            require(definition != null && definition.getNode() != null
                && definition.getNode().getRoot() == root, "All discovered nodes must belong to the published root");
        }
        onlyStorage(root.getInputAbleBarrels(), "input");
        return root;
    }

    NetworkRoot rebuild(NetworkRoot previous) {
        NetworkController.markTopologyDirty(controller);
        var rebuilt = resolve();
        require(rebuilt != previous, "A dirty topology must receive a newly discovered root");
        return rebuilt;
    }

    void verifyView(NetworkRoot root, ItemStack expected, long amount) {
        onlyStorage(root.getInputAbleBarrels(), "input");
        onlyStorage(root.getOutputAbleBarrels(), "output");
        var view = root.getAllNetworkItemsLongType();
        require(view.size() == 1, "Two monitors must expose one item identity, not duplicate storage entries");
        var entry = view.entrySet().iterator().next();
        require(entry.getKey() != null && expected.isSimilar(entry.getKey()) && entry.getValue() == amount,
            "Discovered storage view must preserve exact tagged identity and quantity");
    }

    private void onlyStorage(Set<BarrelIdentity> barrels, String direction) {
        require(barrels.size() == 1 && barrels.iterator().next().getLocation().equals(storage),
            "Production " + direction + " discovery must deduplicate the shared IE2 storage");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

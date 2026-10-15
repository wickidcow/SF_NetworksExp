package networksprobe;

import com.ytdd9527.networksexpansion.core.items.machines.AutoCrafter;
import com.ytdd9527.networksexpansion.implementation.machines.managers.CrafterManager;
import com.ytdd9527.networksexpansion.implementation.machines.managers.QuantumManager;
import com.ytdd9527.networksexpansion.implementation.machines.managers.DrawerManager;
import com.ytdd9527.networksexpansion.implementation.machines.viewer.ItemFlowViewer;
import com.balugaq.netex.api.data.ItemContainer;
import com.balugaq.netex.api.data.StorageUnitData;
import com.balugaq.netex.api.data.ItemFlowRecord;
import com.balugaq.netex.api.enums.StorageUnitType;
import com.balugaq.netex.api.events.NetworkRootLocateStorageEvent;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.network.stackcaches.BlueprintInstance;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.slimefun.network.QuantumCacheLifecycle;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.sefiraat.networks.slimefun.NetworkSlimefunItems;
import io.github.sefiraat.networks.utils.Keys;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable-server regression: uses real managers, viewers, menus and native display writes. Never install on a live server. */
public final class CrafterManagerDisplayProbe extends JavaPlugin {
    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> {
            String result;
            try {
                verify();
                result = "PASS: real Networks manager/viewer display writes, metadata, counts and source isolation";
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

    private void verify() throws Exception {
        var failures = new ArrayList<String>();
        probe("Crafter Manager", this::verifyCrafter, failures);
        probe("Quantum Manager", () -> verifyStorageManager(false), failures);
        probe("Drawer Manager", () -> verifyStorageManager(true), failures);
        probe("Item Flow Viewer main menu", () -> verifyFlow(false), failures);
        probe("Item Flow Viewer submenu", () -> verifyFlow(true), failures);
        probe("Drawer stored-item preview", this::verifyDrawerPreview, failures);
        probe("Linker Grid display", this::verifyLinker, failures);
        probe("Quantum Storage wrapped preview", this::verifyQuantumPreview, failures);
        probe("Quantum world display", this::verifyQuantumWorldDisplay, failures);
        probe("Quantum live item conservation", this::verifyQuantumConservation, failures);
        probe("Real Quantum block and break handler", this::verifyRealQuantumBlockBreak, failures);
        probe("Native menu source snapshots and strict guards", this::verifyNativeMenuSnapshots, failures);
        if (!failures.isEmpty()) throw new AssertionError(String.join("; ", failures));
    }

    private void probe(String name, CheckedProbe action, List<String> failures) {
        try {
            action.run();
            getLogger().info("CASE PASS: " + name);
        } catch (Throwable failure) {
            failures.add(name + ": " + failure);
            getLogger().severe("CASE FAIL: " + name + ": " + failure);
            failure.printStackTrace();
        }
    }

    @FunctionalInterface
    private interface CheckedProbe { void run() throws Exception; }

    private void verifyCrafter() {
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


    private BlockMenu menu(Location location, String id) {
        location.getWorld().getChunkAt(location).load();
        var preset = new BlockMenuPreset(id, "Display probe") {
            @Override public void init() { setSize(54); }
            @Override public boolean canOpen(Block block, Player player) { return false; }
            @Override public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) { return new int[0]; }
        };
        return new BlockMenu(preset, location) {
            @Override public boolean hasViewer() { return true; }
        };
    }

    private ItemStack source() {
        var source = new SlimefunItemStack("NETWORKS_DISPLAY_PROBE_ITEM", Material.DIAMOND,
            "&aStored output", "&7Original lore");
        source.setAmount(7);
        source.editMeta(meta -> {
            meta.getPersistentDataContainer().set(new NamespacedKey("oldaddon", "opaque_payload"),
                PersistentDataType.BYTE_ARRAY, new byte[]{1, 7, 42});
            var model = meta.getCustomModelDataComponent();
            model.setFloats(List.of(1234.5F));
            model.setStrings(List.of("preserved-model"));
            meta.setCustomModelDataComponent(model);
        });
        return source;
    }

    private void checkDisplay(BlockMenu menu, int slot, ItemStack source, int amount) {
        ItemStack stored = menu.getItemInSlot(slot);
        require(stored != null && stored.getType() == source.getType(), "display material");
        require(stored.getAmount() == amount, "display amount");
        ItemMeta expected = source.getItemMeta();
        expected.displayName(stored.getItemMeta().displayName());
        expected.lore(stored.getItemMeta().lore());
        require(expected.equals(stored.getItemMeta()), "metadata outside display decoration");
        require(menu.getMenuClickHandler(slot) != null, "display click handler");
        require(menu.isDirty(), "display dirty tracking");
    }

    private void verifyStorageManager(boolean drawer) {
        var quantum = (QuantumManager) SlimefunItem.getById("NTW_EXPANSION_QUANTUM_MANAGER");
        var drawers = (DrawerManager) SlimefunItem.getById("NTW_EXPANSION_DRAWER_MANAGER");
        require(quantum != null && drawers != null, "storage managers registered");
        var world = Bukkit.getWorlds().getFirst();
        var location = new Location(world, drawer ? 8 : 4, 100, 0);
        var storedLocation = location.clone().add(1, 0, 0);
        var menu = menu(location, drawer ? "DRAWER_DISPLAY_PROBE" : "QUANTUM_DISPLAY_PROBE");
        var source = source();
        ItemMeta before = source.getItemMeta();
        var barrel = new io.github.sefiraat.networks.network.barrel.NetworkStorage(storedLocation, source, 123, 1000);
        var container = new ItemContainer(7, source, 123);
        var contents = new ConcurrentHashMap<Integer, ItemContainer>();
        contents.put(7, container);
        var data = new StorageUnitData(1, (org.bukkit.OfflinePlayer) null,
            StorageUnitType.TINY, true, storedLocation, contents);
        var root = new NetworkRoot(location, NodeType.CONTROLLER, 16) {
            @Override public List<BarrelIdentity> getBarrels(Predicate<BarrelIdentity> filter,
                NetworkRootLocateStorageEvent.Strategy strategy, boolean includeEmpty) {
                return List.of(barrel);
            }
            @Override public Map<StorageUnitData, Location> getCargoStorageUnitDatas(
                NetworkRootLocateStorageEvent.Strategy strategy, boolean includeEmpty) {
                return Map.of(data, storedLocation);
            }
        };
        var definition = new NodeDefinition(NodeType.CONTROLLER);
        definition.setNode(root);
        NetworkStorage.registerNode(location, definition);
        var caches = drawer ? drawers.getCacheMap() : quantum.getCacheMap();
        caches.put(location, new GridCache(0, 0, GridCache.SortOrder.ALPHABETICAL));
        java.util.function.Consumer<BlockMenu> update = drawer ? drawers::updateDisplay : quantum::updateDisplay;
        int slot = (drawer ? drawers.getDisplaySlots() : quantum.getDisplaySlots())[0];
        try {
            update.accept(null);
            for (int attempt = 0; attempt < 20; attempt++) {
                update.accept(menu);
                checkDisplay(menu, slot, source, drawer ? 1 : 7);
            }
            require(before.equals(source.getItemMeta()) && source.getAmount() == 7, "storage sample unchanged");
            require(container.getAmount() == 123 && data.getStoredItemsMap().get(7) == container,
                "drawer contents and identity unchanged");
            require(barrel.getAmount() == 123, "quantum stored count unchanged");
            menu.getItemInSlot(slot).editMeta(m -> m.displayName(Component.text("Changed")));
            require(before.equals(source.getItemMeta()), "display does not alias storage metadata");
            barrel.setItemStack(new ItemStack(Material.STONE, 13));
            contents.put(7, new ItemContainer(7, new ItemStack(Material.STONE), 123));
            update.accept(menu);
            checkDisplay(menu, slot, new ItemStack(Material.STONE), drawer ? 1 : 13);

            var stored = Slimefun.getDatabaseManager().getBlockDataController()
                .createBlock(storedLocation, "NTW_EXPANSION_CRAFTER_MANAGER");
            stored.setData("netex-icon", "sf:NTW_EXPANSION_CRAFTER_MANAGER");
            stored.setData("netex-name", "&bCustom stored item");
            stored.setData("netex-top", "1");
            ItemStack custom = SlimefunItem.getById("NTW_EXPANSION_CRAFTER_MANAGER").getItem();
            ItemMeta templateBefore = custom.getItemMeta();
            update.accept(menu);
            checkDisplay(menu, slot, custom, custom.getAmount());
            require(templateBefore.equals(custom.getItemMeta()), "registered icon template unchanged");
            stored.removeData("netex-icon");
            stored.removeData("netex-name");
            barrel.setItemStack(null);
            contents.clear();
            update.accept(menu);
            require(menu.getItemInSlot(slot) != null, "empty storage fallback");
            require(contents.isEmpty() && barrel.getItemStack() == null, "empty storage remains empty");
        } finally {
            caches.remove(location);
            NetworkStorage.removeNode(location);
        }
    }

    private void verifyFlow(boolean submenu) {
        var viewer = (ItemFlowViewer) SlimefunItem.getById("NTW_EXPANSION_ITEM_FLOW_VIEWER");
        require(viewer != null, "Item Flow Viewer registered");
        var location = new Location(Bukkit.getWorlds().getFirst(), submenu ? 16 : 12, 100, 0);
        var accessor = location.clone().add(1, 0, 0);
        var menu = menu(location, submenu ? "FLOW_SUB_PROBE" : "FLOW_MAIN_PROBE");
        var item = submenu ? SlimefunItem.getById("NTW_EXPANSION_CRAFTER_MANAGER").getItem() : source();
        item = item.clone();
        item.setAmount(1);
        var originalMeta = item.getItemMeta();
        var record = new ItemFlowRecord();
        record.getActions().put(item, List.of(new ItemFlowRecord.TransportAction(accessor, 7, 1L)));
        var root = new NetworkRoot(location, NodeType.CONTROLLER, 16) {
            @Override public boolean isRecordFlow() { return true; }
            @Override public ItemFlowRecord getItemFlowRecord() { return record; }
        };
        Slimefun.getDatabaseManager().getBlockDataController()
            .createBlock(accessor, "NTW_EXPANSION_CRAFTER_MANAGER");
        var cache = new GridCache(0, 0, GridCache.SortOrder.ALPHABETICAL);
        if (submenu) viewer.subMenu(root, menu, cache, "sf:NTW_EXPANSION_CRAFTER_MANAGER");
        else viewer.mainMenu(root, menu, cache);
        ItemStack display = menu.getItemInSlot(viewer.getDisplaySlots()[0]);
        require(display != null && display.getType() == item.getType(), "flow display material");
        require(originalMeta.equals(item.getItemMeta()) && item.getAmount() == 1, "flow source unchanged");
        require(record.getActions().size() == 1 && record.getActions().get(item).getFirst().amount() == 7,
            "flow records unchanged");
    }

    private void verifyDrawerPreview() throws Exception {
        var source = new io.github.bakedlibs.dough.items.CustomItemStack(source(), "Wrapped stored item");
        var before = source.getItemMeta();
        var method = com.ytdd9527.networksexpansion.implementation.machines.unit.NetworksDrawer.class
            .getDeclaredMethod("getDisplayItem", ItemStack.class, int.class, int.class);
        method.setAccessible(true);
        var display = (ItemStack) method.invoke(null, source, 123, 1000);
        var menu = menu(new Location(Bukkit.getWorlds().getFirst(), 32, 100, 0), "DRAWER_PREVIEW_PROBE");
        menu.replaceExistingItem(0, display);
        var actual = menu.getItemInSlot(0);
        var expected = source.getItemMeta();
        expected.lore(actual.getItemMeta().lore());
        require(expected.equals(actual.getItemMeta()) && actual.getAmount() == 7, "drawer preview preserves metadata/count");
        require(before.equals(source.getItemMeta()) && source.getAmount() == 7, "drawer sample unchanged");
    }

    private void verifyLinker() {
        var crafter = SlimefunItem.getById("NTW_EXPANSION_CRAFTER_MANAGER");
        var linker = new com.balugaq.netex.integrations.logitech.LinkerGrid(crafter.getItemGroup(),
            new SlimefunItemStack("NETWORKS_LINKER_DISPLAY_PROBE", Material.CHEST, "Linker probe"),
            io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType.ENHANCED_CRAFTING_TABLE, new ItemStack[9]);
        var location = new Location(Bukkit.getWorlds().getFirst(), 28, 100, 0);
        var menu = menu(location, "LINKER_DISPLAY_PROBE");
        var source = source();
        var before = source.getItemMeta();
        var root = new NetworkRoot(location, NodeType.CONTROLLER, 16) {
            @Override public Map<ItemStack, Long> getAllNetworkItemsLongTypeView() { return Map.of(source, 123L); }
        };
        var cache = new GridCache(0, 0, GridCache.SortOrder.ALPHABETICAL);
        linker.mainMenu(root, menu, cache);
        checkDisplay(menu, linker.getDisplaySlots()[0], source, 7);
        require(before.equals(source.getItemMeta()) && source.getAmount() == 7, "linker source unchanged");
    }

    private void verifyQuantumPreview() {
        var menu = menu(new Location(Bukkit.getWorlds().getFirst(), 20, 100, 0), "QUANTUM_PREVIEW_PROBE");
        var source = new io.github.bakedlibs.dough.items.CustomItemStack(source(), "Wrapped stored item");
        var before = source.getItemMeta();
        var cache = new io.github.sefiraat.networks.network.stackcaches.QuantumCache(source, 123L, 1000L, false, true);
        io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage.updateDisplayItem(menu, cache);
        var shown = menu.getItemInSlot(io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage.ITEM_SLOT);
        require(shown != null && shown.getType() == source.getType() && shown.getAmount() == 1,
            "quantum storage preview");
        var expected = source.getItemMeta();
        expected.lore(shown.getItemMeta().lore());
        expected.setHideTooltip(false);
        // Paper removes explicit default tooltip components when materializing an inventory item.
        var canonicalExpected = new ItemStack(source.getType());
        canonicalExpected.setItemMeta(expected);
        require(canonicalExpected.getItemMeta().equals(shown.getItemMeta()), "quantum preview preserves other metadata");
        require(before.equals(source.getItemMeta()) && source.getAmount() == 7 && cache.getAmountLong() == 123,
            "quantum preview preserves stored item and count");
    }

    private void verifyQuantumWorldDisplay() throws Exception {
        var location = new Location(Bukkit.getWorlds().getFirst(), 24, 100, 0);
        location.getWorld().getChunkAt(location).load();
        location.getBlock().setType(Material.BARREL);
        var source = source();
        var before = source.getItemMeta();
        var keyClass = Class.forName("io.github.sefiraat.networks.utils.QuantumStorageDisplayManager$BlockKey");
        var from = keyClass.getDeclaredMethod("from", Location.class);
        from.setAccessible(true);
        Object key = from.invoke(null, location);
        var update = io.github.sefiraat.networks.utils.QuantumStorageDisplayManager.class
            .getDeclaredMethod("updateDisplay", Block.class, keyClass, ItemStack.class);
        update.setAccessible(true);
        try {
            update.invoke(null, location.getBlock(), key, source);
            var displays = location.getWorld().getNearbyEntities(location, 2, 2, 2).stream()
                .filter(org.bukkit.entity.ItemDisplay.class::isInstance)
                .map(org.bukkit.entity.ItemDisplay.class::cast).toList();
            require(displays.size() == 1, "one quantum world display");
            ItemStack shown = displays.getFirst().getItemStack();
            require(shown.getAmount() == 1 && before.equals(shown.getItemMeta()), "world display preserves metadata");
            require(before.equals(source.getItemMeta()) && source.getAmount() == 7, "world display source unchanged");
        } finally {
            for (var entity : location.getWorld().getNearbyEntities(location, 2, 2, 2)) {
                if (entity instanceof org.bukkit.entity.ItemDisplay) entity.remove();
            }
        }
    }


    /**
     * Native Bukkit ItemStack and metadata conservation checks on a disposable
     * Minecraft server. A full physical block-break/restart test remains separate.
     */
    private void verifyQuantumConservation() {
        final var location = new Location(Bukkit.getWorlds().getFirst(), 48, 100, 0);
        final var ownerKey = new NamespacedKey("networkstest", "owner");
        final ItemStack template = new ItemStack(Material.DIAMOND, 1);
        template.editMeta(meta -> meta.getPersistentDataContainer().set(
            ownerKey, PersistentDataType.STRING, "verified-owner"));
        final var originalMeta = template.getItemMeta();

        final QuantumCache cache = new QuantumCache(template.clone(), 60L, 64L, false, false);
        final ItemStack incoming = template.clone();
        incoming.setAmount(10);
        final int remainder = cache.increaseMatchingAmount(incoming);
        require(remainder == 6 && cache.getAmountLong() == 64L,
            "Quantum must accept only four items when capacity is nearly full");

        final ItemStack withdrawn = cache.withdrawItem(11);
        require(withdrawn != null && withdrawn.getAmount() == 11
            && originalMeta.equals(withdrawn.getItemMeta()) && cache.getAmountLong() == 53L,
            "Quantum withdrawal must preserve counts and custom metadata");

        final ItemStack wrongOwner = template.clone();
        wrongOwner.setAmount(9);
        wrongOwner.editMeta(meta -> meta.getPersistentDataContainer().set(
            ownerKey, PersistentDataType.STRING, "different-owner"));
        require(cache.increaseMatchingAmount(wrongOwner) == 9 && cache.getAmountLong() == 53L,
            "Same-material custom items cannot cross PDC identity boundaries");

        final Map<Location, QuantumCache> active = new ConcurrentHashMap<>();
        active.put(location, cache);
        final long[] serialized = {-1L};
        require(QuantumCacheLifecycle.retireCurrent(active, location, live -> {
            serialized[0] = live.getAmountLong();
            require(originalMeta.equals(live.getItemStack().getItemMeta()),
                "Retired cache must preserve assigned item identity");
        }), "Quantum lifecycle must retire the live cache");
        require(!active.containsKey(location), "Retired Quantum cache must not remain mapped");
        require(!QuantumCacheLifecycle.withCurrent(active, location, cache, live -> {
            throw new AssertionError("A stale tick must never mutate retired storage");
        }), "Detached cache reference must reject queued activity");

        require(serialized[0] + withdrawn.getAmount() + remainder == 70L,
            "Quantity must be conserved across stored, output and returned items");
        require(originalMeta.equals(template.getItemMeta()) && template.getAmount() == 1,
            "Quantum must never mutate its source item template");
    }


    /**
     * Real disposable-world block and Slimefun BlockMenu fixture.
     *
     * Calls the actual registered Quantum Storage block-break handler against a
     * physical Paper block and verifies that dropped storage PDC serializes the
     * exact 22-item custom payload. Tests active storage and a cold cache that
     * must be reconstructed from persisted in-memory Slimefun block data.
     *
     * It intentionally does not pretend that a synthetic BlockBreakEvent is a
     * player-originated event or a process restart: those remain separate gates.
     */
    @SuppressWarnings("deprecation")
    private void verifyRealQuantumBlockBreak() throws Exception {
        final var storage = NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_0;
        require(storage != null && SlimefunItem.getById(storage.getId()) == storage,
            "Actual Network Quantum Storage item must be registered");
        final var material = storage.getItem().getType();
        require(material.isBlock(), "Quantum Storage must be a physically placeable block");
        final var world = Bukkit.getWorlds().getFirst();
        final var controller = Slimefun.getDatabaseManager().getBlockDataController();
        final var ownerKey = new NamespacedKey("networkstest", "physical_owner");
        final var breakHandler = NetworkQuantumStorage.class.getDeclaredMethod(
            "onBreak", BlockBreakEvent.class);
        breakHandler.setAccessible(true);
        final var ticker = NetworkQuantumStorage.class.getDeclaredMethod(
            "onTick", Block.class);
        ticker.setAccessible(true);

        for (boolean coldCache : new boolean[]{false, true}) {
            final Location location = new Location(world, coldCache ? 72 : 68, 100, 0);
            world.getChunkAt(location).load();
            final Block block = location.getBlock();
            block.setType(material);
            final var existingItems = world.getNearbyEntities(location, 2, 2, 2).stream()
                .filter(org.bukkit.entity.Item.class::isInstance)
                .map(org.bukkit.entity.Item.class::cast).toList();
            require(existingItems.isEmpty(), "Physical probe location must start without dropped items");

            final var data = controller.createBlock(location, storage.getId());
            final java.util.List<org.bukkit.entity.Item> spawned = new ArrayList<>();
            try {
                controller.loadBlockData(data);
                final BlockMenu menu = StorageCacheUtils.getMenu(location);
                require(menu != null, "Actual Slimefun Quantum Storage menu must load");

                final ItemStack stored = new ItemStack(Material.DIAMOND);
                stored.editMeta(meta -> meta.getPersistentDataContainer().set(
                    ownerKey, PersistentDataType.STRING, "physical-staging-owner"));
                final ItemMeta original = stored.getItemMeta();
                final QuantumCache cache = new QuantumCache(stored, 22L, 64L, false, false);
                NetworkQuantumStorage.getCaches().put(location, cache);
                NetworkQuantumStorage.updateDisplayItem(menu, cache);
                NetworkQuantumStorage.syncBlock(location, cache);
                menu.replaceExistingItem(NetworkQuantumStorage.OUTPUT_SLOT,
                    new ItemStack(Material.EMERALD, 64));
                require("22".equals(data.getData(NetworkQuantumStorage.BS_AMOUNT)),
                    "Filled Quantum must persist 22 items to its Slimefun block data");

                if (coldCache) {
                    // Model a block reloaded with Slimefun state but without any
                    // live Quantum cache. The break handler must initialize first.
                    NetworkQuantumStorage.getCaches().remove(location, cache);
                } else {
                    // An active tick must keep the original count because the
                    // output slot is blocked by the wrong item type.
                    ticker.invoke(storage, block);
                    require(cache.getAmountLong() == 22L,
                        "Blocked output must not consume stored Quantum items");
                }

                final BlockBreakEvent breakEvent = new BlockBreakEvent(block, (Player) null);
                breakHandler.invoke(storage, breakEvent);
                require(!NetworkQuantumStorage.getCaches().containsKey(location),
                    "Breaking a Quantum Storage must detach its cache");
                require(!breakEvent.isDropItems(),
                    "A full Quantum Storage must suppress default duplicate block drops");

                spawned.addAll(world.getNearbyEntities(location, 2, 2, 2).stream()
                    .filter(org.bukkit.entity.Item.class::isInstance)
                    .map(org.bukkit.entity.Item.class::cast).toList());
                int carryingStorage = 0;
                int emeraldOutput = 0;
                for (var entity : spawned) {
                    final ItemStack stack = entity.getItemStack();
                    final QuantumCache saved = Keys.getQuantumCache(stack.getItemMeta());
                    if (saved != null) {
                        carryingStorage++;
                        require(saved.getAmountLong() == 22L,
                            "Broken Quantum item must preserve exactly 22 stored items");
                        require(original.equals(saved.getItemStack().getItemMeta()),
                            "Stored custom item owner metadata must survive drop serialization");
                    } else if (stack.getType() == Material.EMERALD) {
                        emeraldOutput += stack.getAmount();
                    }
                }
                require(carryingStorage == 1,
                    "Exactly one item must carry the stored Quantum contents");
                require(emeraldOutput == 64,
                    "The physical output inventory must drop 64 emeralds exactly once");
            } finally {
                for (var entity : spawned) entity.remove();
                NetworkQuantumStorage.getCaches().remove(location);
                controller.removeBlock(location);
                block.setType(Material.AIR);
                QuantumCacheLifecycle.clearBreakMarker(location);
            }
        }
    }

    /** Native component fidelity, separate from MockBukkit's limited metadata implementation. */
    private void verifyNativeMenuSnapshots() throws Exception {
        var transfers = io.github.sefiraat.networks.utils.NetworkTransferUtils.class;
        var snapshotMethod = transfers.getDeclaredMethod("snapshotMenuSource", ItemStack.class);
        var guardMethod = transfers.getDeclaredMethod("sourceStillMatches", ItemStack.class, ItemStack.class);
        snapshotMethod.setAccessible(true);
        guardMethod.setAccessible(true);
        var owner = new NamespacedKey("networkstest", "snapshot_owner");
        var payload = new NamespacedKey("networkstest", "snapshot_payload");
        var counter = new NamespacedKey("networkstest", "snapshot_counter");
        ItemStack template = ItemStack.of(Material.GUNPOWDER, 16);
        template.editMeta(meta -> {
            meta.displayName(Component.text("Native extractor dust fixture"));
            meta.lore(List.of(Component.text("Preserve this item's existing data")));
            meta.getPersistentDataContainer().set(owner, PersistentDataType.STRING, "original-owner");
            meta.getPersistentDataContainer().set(payload, PersistentDataType.BYTE_ARRAY, new byte[]{3, 1, 4});
            meta.getPersistentDataContainer().set(counter, PersistentDataType.LONG, 9876543210L);
            var models = meta.getCustomModelDataComponent();
            models.setFloats(List.of(7001.0F, 55.0F));
            models.setStrings(List.of("original-model"));
            models.setFlags(List.of(true, false));
            models.setColors(List.of(org.bukkit.Color.fromRGB(12, 34, 56)));
            meta.setCustomModelDataComponent(models);
        });
        for (String kind : List.of("native", "rebuilding", "aliasing", "slimefun")) {
            ItemStack live = switch (kind) {
                case "rebuilding" -> new ModelRebuildingStack(template, false);
                case "aliasing" -> new ModelRebuildingStack(template, true);
                case "slimefun" -> new SlimefunItemStack("NETWORKS_SNAPSHOT_PROBE_ITEM", template);
                default -> template.clone();
            };
            ItemMeta before = live.getItemMeta();
            if (kind.equals("rebuilding")) {
                ItemStack rebuilt = live.clone();
                require(!rebuilt.isSimilar(live), "Native comparison must detect a model-only clone change");
                require(rebuilt.getItemMeta().getCustomModelDataComponent().getFloats()
                    .equals(List.of(7002.0F, 55.0F)), "Rebuilding fixture must change its first model float");
                require(before.equals(live.getItemMeta()), "Rebuilding clone cannot mutate its source");
            }
            ItemStack snapshot = (ItemStack) snapshotMethod.invoke(null, live);
            require(snapshot != live && snapshot.getAmount() == 16 && snapshot.isSimilar(live),
                "Detached native snapshot must preserve full component identity and amount: " + kind);
            require(before.equals(snapshot.getItemMeta()), "Snapshot metadata must be exact: " + kind);
            var actual = snapshot.getItemMeta();
            var models = actual.getCustomModelDataComponent();
            require(models.getFloats().equals(List.of(7001.0F, 55.0F)), "All model floats must survive");
            require(models.getStrings().equals(List.of("original-model")), "Model strings must survive");
            require(models.getFlags().equals(List.of(true, false)), "Model flags must survive");
            require(models.getColors().equals(List.of(org.bukkit.Color.fromRGB(12, 34, 56))),
                "Model colors must survive");
            require("original-owner".equals(actual.getPersistentDataContainer().get(owner, PersistentDataType.STRING)),
                "Owner identity must survive");
            require(Long.valueOf(9876543210L).equals(actual.getPersistentDataContainer().get(counter, PersistentDataType.LONG)),
                "Long PDC data must survive without narrowing");
            require(java.util.Arrays.equals(new byte[]{3, 1, 4},
                actual.getPersistentDataContainer().get(payload, PersistentDataType.BYTE_ARRAY)),
                "Opaque PDC payload must survive");
            require((boolean) guardMethod.invoke(null, snapshot, live), "Unchanged native source must match");
            for (String mutation : List.of("amount", "owner", "model")) {
                ItemStack changed = snapshot.clone();
                if (mutation.equals("amount")) changed.setAmount(15);
                else changed.editMeta(meta -> {
                    if (mutation.equals("owner")) {
                        meta.getPersistentDataContainer().set(owner, PersistentDataType.STRING, "different-owner");
                    } else {
                        var changedModels = meta.getCustomModelDataComponent();
                        changedModels.setFloats(List.of(7003.0F, 55.0F));
                        meta.setCustomModelDataComponent(changedModels);
                    }
                });
                require(!(boolean) guardMethod.invoke(null, snapshot, changed),
                    "Strict native source guard must reject a real " + mutation + " change");
            }
            snapshot.setAmount(1);
            snapshot.editMeta(meta -> meta.displayName(Component.text("Detached edit")));
            require(live.getAmount() == 16 && before.equals(live.getItemMeta()),
                "Snapshot quantity and metadata edits cannot alias the live source");
        }
    }

    /** Models a model-only rebuilding or aliasing addon clone, not a loaded Infinity extractor. */
    private static final class ModelRebuildingStack extends ItemStack {
        private final boolean alias;
        private ModelRebuildingStack(ItemStack template, boolean alias) {
            super(template.getType(), template.getAmount());
            this.alias = alias;
            setItemMeta(template.getItemMeta());
        }
        @Override public ItemStack clone() {
            if (alias) return this;
            ItemStack result = ItemStack.of(getType(), getAmount());
            var meta = getItemMeta();
            var models = meta.getCustomModelDataComponent();
            models.setFloats(List.of(7002.0F, 55.0F));
            meta.setCustomModelDataComponent(models);
            result.setItemMeta(meta);
            return result;
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

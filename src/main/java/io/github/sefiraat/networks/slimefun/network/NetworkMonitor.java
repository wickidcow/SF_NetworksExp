package io.github.sefiraat.networks.slimefun.network;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.utils.TextUtil;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.utils.DisplayNameUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class NetworkMonitor extends NetworkDirectional {

    /*
     * The entire upper four rows belong to the topology inspector. The classic Network Monitor automatically
     * discovers supported storage touching any of its six faces, so the inspector never exposes direction controls.
     * Directional input/output monitor variants remain directional elsewhere.
     */
    private static final int[] INSPECTOR_DISPLAY_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26,
        27, 28, 29, 30, 31, 32, 33, 34, 35
    };
    private static final int PAGE_PREVIOUS_SLOT = 36;
    private static final int AUTO_CONNECT_SLOT = 37;
    private static final int SUMMARY_SLOT = 38;
    private static final int REFRESH_SLOT = 40;
    private static final int LEGEND_SLOT = 42;
    private static final int PAGE_NEXT_SLOT = 44;
    private static final double MAX_VISIBLE_HIGHLIGHT_DISTANCE_SQUARED = 128.0D * 128.0D;

    private static final Map<Location, ViewState> VIEW_STATE_MAP = new ConcurrentHashMap<>();
    private static final Map<Location, MonitorSnapshot> SNAPSHOT_MAP = new ConcurrentHashMap<>();
    private static final Map<Location, RenderState> RENDERED_STATE_MAP = new ConcurrentHashMap<>();
    private static final Map<Location, Long> LAST_REFRESH_REQUEST = new ConcurrentHashMap<>();
    private static final AtomicLong SNAPSHOT_REVISION = new AtomicLong();

    public NetworkMonitor(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.STORAGE_MONITOR);
    }

    @Override
    protected boolean usesDirectionalGridControls() {
        // The classic Network Monitor auto-connects to supported adjacent storage on all six faces.
        return false;
    }

    @Override
    protected int @NotNull [] getBackgroundSlots() {
        return new int[]{
            0, 1, 2, 3, 4, 5, 6, 7, 8,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
        };
    }

    @Override
    public void updateGui(@Nullable BlockMenu blockMenu) {
        super.updateGui(blockMenu);

        if (blockMenu == null
            || !blockMenu.hasViewer()
            || !Networks.getConfigManager().isNetworkMonitorInspectorEnabled()) {
            return;
        }

        renderInspector(blockMenu, false);
    }

    @Override
    protected void onBreak(@NotNull BlockBreakEvent event) {
        final Location location = key(event.getBlock().getLocation());
        VIEW_STATE_MAP.remove(location);
        SNAPSHOT_MAP.remove(location);
        RENDERED_STATE_MAP.remove(location);
        LAST_REFRESH_REQUEST.remove(location);
        super.onBreak(event);
    }

    private void renderInspector(@NotNull BlockMenu blockMenu, boolean force) {
        final Location monitorLocation = key(blockMenu.getLocation());
        final MonitorSnapshot snapshot = SNAPSHOT_MAP.computeIfAbsent(
            monitorLocation,
            ignored -> createSnapshot(blockMenu));

        ViewState view = VIEW_STATE_MAP.getOrDefault(monitorLocation, ViewState.overview());
        final MachineGroup selected = view.groupKey() == null ? null : findGroup(snapshot, view.groupKey());
        if (view.groupKey() != null && selected == null) {
            view = ViewState.overview();
            VIEW_STATE_MAP.put(monitorLocation, view);
        }

        if (selected == null) {
            renderOverview(blockMenu, snapshot, monitorLocation, view, force);
        } else {
            renderGroupDetails(blockMenu, snapshot, selected, monitorLocation, view, force);
        }
    }

    private void renderOverview(
        @NotNull BlockMenu blockMenu,
        @NotNull MonitorSnapshot snapshot,
        @NotNull Location monitorLocation,
        @NotNull ViewState view,
        boolean force) {

        final int maxPage = Math.max(0, pageCount(snapshot.groups().size()) - 1);
        final int page = Math.min(Math.max(0, view.page()), maxPage);
        final ViewState normalized = new ViewState(null, page, NodeFilter.ALL);
        VIEW_STATE_MAP.put(monitorLocation, normalized);

        final RenderState previous = RENDERED_STATE_MAP.get(monitorLocation);
        final RenderState currentState = new RenderState(snapshot.revision(), null, page, NodeFilter.ALL);
        if (!force && currentState.equals(previous)) {
            return;
        }

        final int start = page * INSPECTOR_DISPLAY_SLOTS.length;
        final int end = Math.min(start + INSPECTOR_DISPLAY_SLOTS.length, snapshot.groups().size());

        for (int i = 0; i < INSPECTOR_DISPLAY_SLOTS.length; i++) {
            final int slot = INSPECTOR_DISPLAY_SLOTS[i];
            final int entryIndex = start + i;

            if (entryIndex < end) {
                final MachineGroup group = snapshot.groups().get(entryIndex);
                blockMenu.replaceExistingItem(slot, groupDisplay(group));
                blockMenu.addMenuClickHandler(slot, (player, clickedSlot, item, action) -> {
                    VIEW_STATE_MAP.put(monitorLocation, new ViewState(group.key(), 0, NodeFilter.ALL));
                    RENDERED_STATE_MAP.remove(monitorLocation);
                    renderInspector(blockMenu, true);
                    return false;
                });
            } else {
                fillBackground(blockMenu, slot);
            }
        }

        addPageButtons(blockMenu, monitorLocation, page, maxPage, normalized, snapshot, null);
        addAutoConnectInfo(blockMenu);

        blockMenu.replaceExistingItem(SUMMARY_SLOT, summaryItem(snapshot, page, maxPage));
        blockMenu.addMenuClickHandler(SUMMARY_SLOT, (player, slot, item, action) -> false);

        blockMenu.replaceExistingItem(REFRESH_SLOT, refreshItem(false));
        blockMenu.addMenuClickHandler(REFRESH_SLOT, (player, slot, item, action) -> {
            requestTopologyRefresh(blockMenu);
            return false;
        });

        blockMenu.replaceExistingItem(LEGEND_SLOT, legendItem());
        blockMenu.addMenuClickHandler(LEGEND_SLOT, (player, slot, item, action) -> false);

        RENDERED_STATE_MAP.put(monitorLocation, currentState);
    }

    private void renderGroupDetails(
        @NotNull BlockMenu blockMenu,
        @NotNull MonitorSnapshot snapshot,
        @NotNull MachineGroup group,
        @NotNull Location monitorLocation,
        @NotNull ViewState view,
        boolean force) {

        final List<NodeSnapshot> visibleNodes = filteredNodes(group.nodes(), view.filter());
        final int maxPage = Math.max(0, pageCount(visibleNodes.size()) - 1);
        final int page = Math.min(Math.max(0, view.page()), maxPage);
        final ViewState normalized = new ViewState(group.key(), page, view.filter());
        VIEW_STATE_MAP.put(monitorLocation, normalized);

        final RenderState previous = RENDERED_STATE_MAP.get(monitorLocation);
        final RenderState currentState =
            new RenderState(snapshot.revision(), group.key(), page, view.filter());
        if (!force && currentState.equals(previous)) {
            return;
        }

        final int start = page * INSPECTOR_DISPLAY_SLOTS.length;
        final int end = Math.min(start + INSPECTOR_DISPLAY_SLOTS.length, visibleNodes.size());

        for (int i = 0; i < INSPECTOR_DISPLAY_SLOTS.length; i++) {
            final int slot = INSPECTOR_DISPLAY_SLOTS[i];
            final int entryIndex = start + i;

            if (entryIndex < end) {
                final NodeSnapshot node = visibleNodes.get(entryIndex);
                blockMenu.replaceExistingItem(slot, nodeDisplay(group, node, entryIndex + 1));
                blockMenu.addMenuClickHandler(slot, (player, clickedSlot, item, action) -> {
                    highlightNode(player, node);
                    return false;
                });
            } else {
                fillBackground(blockMenu, slot);
            }
        }

        addPageButtons(blockMenu, monitorLocation, page, maxPage, normalized, snapshot, group);
        addAutoConnectInfo(blockMenu);

        blockMenu.replaceExistingItem(SUMMARY_SLOT, backButton(group));
        blockMenu.addMenuClickHandler(SUMMARY_SLOT, (player, slot, item, action) -> {
            VIEW_STATE_MAP.put(monitorLocation, ViewState.overview());
            RENDERED_STATE_MAP.remove(monitorLocation);
            renderInspector(blockMenu, true);
            return false;
        });

        blockMenu.replaceExistingItem(REFRESH_SLOT, refreshItem(false));
        blockMenu.addMenuClickHandler(REFRESH_SLOT, (player, slot, item, action) -> {
            requestTopologyRefresh(blockMenu);
            return false;
        });

        blockMenu.replaceExistingItem(LEGEND_SLOT, groupFilterItem(group, view.filter(), visibleNodes.size()));
        blockMenu.addMenuClickHandler(LEGEND_SLOT, (player, slot, item, action) -> {
            final NodeFilter next = view.filter().next();
            VIEW_STATE_MAP.put(monitorLocation, new ViewState(group.key(), 0, next));
            RENDERED_STATE_MAP.remove(monitorLocation);
            renderInspector(blockMenu, true);
            return false;
        });

        RENDERED_STATE_MAP.put(monitorLocation, currentState);
    }

    private void addPageButtons(
        @NotNull BlockMenu blockMenu,
        @NotNull Location monitorLocation,
        int page,
        int maxPage,
        @NotNull ViewState view,
        @NotNull MonitorSnapshot snapshot,
        @Nullable MachineGroup group) {

        blockMenu.replaceExistingItem(PAGE_PREVIOUS_SLOT, pageButton(false, page, maxPage));
        blockMenu.addMenuClickHandler(PAGE_PREVIOUS_SLOT, (player, slot, item, action) -> {
            if (page > 0) {
                VIEW_STATE_MAP.put(
                    monitorLocation,
                    new ViewState(view.groupKey(), page - 1, view.filter()));
                RENDERED_STATE_MAP.remove(monitorLocation);
                if (group == null) {
                    renderOverview(blockMenu, snapshot, monitorLocation,
                        new ViewState(null, page - 1, NodeFilter.ALL), true);
                } else {
                    renderGroupDetails(blockMenu, snapshot, group, monitorLocation,
                        new ViewState(group.key(), page - 1, view.filter()), true);
                }
            }
            return false;
        });

        blockMenu.replaceExistingItem(PAGE_NEXT_SLOT, pageButton(true, page, maxPage));
        blockMenu.addMenuClickHandler(PAGE_NEXT_SLOT, (player, slot, item, action) -> {
            if (page < maxPage) {
                VIEW_STATE_MAP.put(
                    monitorLocation,
                    new ViewState(view.groupKey(), page + 1, view.filter()));
                RENDERED_STATE_MAP.remove(monitorLocation);
                if (group == null) {
                    renderOverview(blockMenu, snapshot, monitorLocation,
                        new ViewState(null, page + 1, NodeFilter.ALL), true);
                } else {
                    renderGroupDetails(blockMenu, snapshot, group, monitorLocation,
                        new ViewState(group.key(), page + 1, view.filter()), true);
                }
            }
            return false;
        });
    }

    private void addAutoConnectInfo(@NotNull BlockMenu blockMenu) {
        final List<String> lore = List.of(
            "",
            TextUtil.GRAY + "Automatically exposes supported storage",
            TextUtil.GRAY + "touching any side of this Monitor.",
            "",
            TextUtil.GREEN + "No direction setup required.",
            TextUtil.DARK_GRAY + "Input/Output-only Monitors remain directional."
        );

        blockMenu.replaceExistingItem(
            AUTO_CONNECT_SLOT,
            control(Material.HOPPER, TextUtil.GREEN + "Storage Auto-Connect", lore));
        blockMenu.addMenuClickHandler(AUTO_CONNECT_SLOT, (player, slot, item, action) -> false);
    }

    private void requestTopologyRefresh(@NotNull BlockMenu blockMenu) {
        final Location monitorLocation = key(blockMenu.getLocation());
        final long now = System.currentTimeMillis();
        final long previous = LAST_REFRESH_REQUEST.getOrDefault(monitorLocation, 0L);

        if (now - previous < 1000L) {
            return;
        }
        LAST_REFRESH_REQUEST.put(monitorLocation, now);

        final NetworkRoot root = findRoot(blockMenu);
        if (root == null) {
            SNAPSHOT_MAP.remove(monitorLocation);
            RENDERED_STATE_MAP.remove(monitorLocation);
            renderInspector(blockMenu, true);
            return;
        }

        final Location controller = root.getController();
        if (controller != null) {
            /*
             * The button is intentionally a real topology refresh, not just a GUI repaint.
             * It tells the controller to rediscover neighbours on its next Slimefun tick so the
             * screen answers the owner's real question: "Is Networks actually reading everything?"
             */
            NetworkController.markTopologyDirty(controller);
        }

        SNAPSHOT_MAP.remove(monitorLocation);
        RENDERED_STATE_MAP.remove(monitorLocation);
        blockMenu.replaceExistingItem(REFRESH_SLOT, refreshItem(true));
        blockMenu.addMenuClickHandler(REFRESH_SLOT, (player, slot, item, action) -> false);

        final long slimefunTickRate = Math.max(1L, Slimefun.getTickerTask().getTickRate());
        final long delay = Math.max(2L, slimefunTickRate * 2L + 2L);

        Bukkit.getScheduler().runTaskLater(Networks.getInstance(), () -> {
            final BlockMenu current = StorageCacheUtils.getMenu(monitorLocation);
            if (current == null) {
                return;
            }

            SNAPSHOT_MAP.put(monitorLocation, createSnapshot(current));
            RENDERED_STATE_MAP.remove(monitorLocation);
            if (current.hasViewer()) {
                renderInspector(current, true);
            }
        }, delay);
    }

    private @NotNull MonitorSnapshot createSnapshot(@NotNull BlockMenu blockMenu) {
        final NetworkRoot root = findRoot(blockMenu);
        if (root == null) {
            return new MonitorSnapshot(
                SNAPSHOT_REVISION.incrementAndGet(),
                List.of(),
                0,
                0,
                0,
                0,
                false,
                false);
        }

        final Map<String, MutableMachineGroup> groups = new HashMap<>();
        int activeNodes = 0;
        int inactiveNodes = 0;

        for (Location rawLocation : root.getNodeLocations()) {
            final Location location = key(rawLocation);
            final NodeDefinition definition = NetworkStorage.getNode(location);
            final NodeType nodeType = definition == null ? null : definition.getType();

            final World world = location.getWorld();
            final boolean chunkLoaded = world != null
                && world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4);

            final SlimefunBlockData data = chunkLoaded ? StorageCacheUtils.getBlock(location) : null;
            final String sfId = data == null ? null : data.getSfId();
            SlimefunItem slimefunItem = sfId == null ? null : SlimefunItem.getById(sfId);
            if (slimefunItem == null && chunkLoaded) {
                slimefunItem = StorageCacheUtils.getSlimefunItem(location);
            }

            final NodeHealth health = nodeHealth(root, definition, data, slimefunItem, chunkLoaded);
            final boolean active = health == NodeHealth.ACTIVE;
            if (active) {
                activeNodes++;
            } else {
                inactiveNodes++;
            }

            final String groupKey;
            final String displayName;
            final ItemStack icon;

            if (slimefunItem != null) {
                groupKey = "sf:" + slimefunItem.getId();
                displayName = cleanName(DisplayNameUtils.getDisplayName(slimefunItem.getItem()));
                icon = slimefunItem.getItem().clone();
            } else if (sfId != null && !sfId.isBlank()) {
                groupKey = "unresolved:" + sfId;
                displayName = "Unresolved: " + sfId;
                icon = new ItemStack(Material.BARRIER);
            } else {
                final String typeName = nodeType == null ? "Unknown Node" : prettyNodeType(nodeType);
                groupKey = "node:" + (nodeType == null ? "UNKNOWN" : nodeType.name());
                displayName = "Unresolved: " + typeName;
                icon = new ItemStack(Material.BARRIER);
            }

            final NodeSnapshot node = new NodeSnapshot(location, nodeType, health, sfId);
            groups.computeIfAbsent(groupKey, ignored -> new MutableMachineGroup(groupKey, displayName, icon))
                .add(node);
        }

        final List<MachineGroup> entries = groups.values().stream()
            .map(MutableMachineGroup::freeze)
            .sorted(Comparator.comparing(MachineGroup::displayName, String.CASE_INSENSITIVE_ORDER))
            .toList();

        return new MonitorSnapshot(
            SNAPSHOT_REVISION.incrementAndGet(),
            entries,
            root.getNodeCount(),
            activeNodes,
            inactiveNodes,
            root.getMaxNodes(),
            root.isOverburdened(),
            true);
    }

    private static @NotNull NodeHealth nodeHealth(
        @NotNull NetworkRoot root,
        @Nullable NodeDefinition definition,
        @Nullable SlimefunBlockData data,
        @Nullable SlimefunItem item,
        boolean chunkLoaded) {

        if (!chunkLoaded) {
            return NodeHealth.CHUNK_UNLOADED;
        }
        if (definition == null) {
            return NodeHealth.NODE_MISSING;
        }
        if (definition.getNode() == null) {
            return NodeHealth.NODE_UNASSIGNED;
        }
        if (definition.getNode().getRoot() != root) {
            return NodeHealth.WRONG_ROOT;
        }
        if (data == null) {
            return NodeHealth.BLOCK_DATA_MISSING;
        }
        if (data.isPendingRemove()) {
            return NodeHealth.PENDING_REMOVE;
        }
        if (item == null) {
            return NodeHealth.ITEM_UNRESOLVED;
        }

        final String sfId = data.getSfId();
        if (sfId == null || sfId.isBlank()) {
            return NodeHealth.ITEM_ID_MISSING;
        }
        if (!sfId.equals(item.getId())) {
            return NodeHealth.ITEM_ID_MISMATCH;
        }

        return NodeHealth.ACTIVE;
    }

    private static @Nullable NetworkRoot findRoot(@NotNull BlockMenu blockMenu) {
        final NodeDefinition definition = NetworkStorage.getNode(blockMenu.getLocation());
        if (definition == null || definition.getNode() == null) {
            return null;
        }
        return definition.getNode().getRoot();
    }

    private static @Nullable MachineGroup findGroup(
        @NotNull MonitorSnapshot snapshot,
        @NotNull String key) {

        for (MachineGroup group : snapshot.groups()) {
            if (group.key().equals(key)) {
                return group;
            }
        }
        return null;
    }

    private static @NotNull List<NodeSnapshot> filteredNodes(
        @NotNull List<NodeSnapshot> nodes,
        @NotNull NodeFilter filter) {

        return nodes.stream()
            .filter(node -> filter.accepts(node.health() == NodeHealth.ACTIVE))
            .sorted(Comparator
                .comparing((NodeSnapshot node) -> node.health() == NodeHealth.ACTIVE)
                .thenComparing(node -> worldName(node.location()), String.CASE_INSENSITIVE_ORDER)
                .thenComparingInt(node -> node.location().getBlockX())
                .thenComparingInt(node -> node.location().getBlockY())
                .thenComparingInt(node -> node.location().getBlockZ()))
            .toList();
    }

    private static @NotNull ItemStack groupDisplay(@NotNull MachineGroup group) {
        final ItemStack display = group.icon().clone();
        display.setAmount(1);
        final ItemMeta meta = display.getItemMeta();

        if (meta != null) {
            meta.displayName(TextUtil.component(
                TextUtil.WHITE + group.displayName() + TextUtil.GRAY + "  x" + group.total()));

            final List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(TextUtil.GRAY + "Total connected: " + TextUtil.WHITE + group.total());
            lore.add(TextUtil.GREEN + "Active: " + group.active());
            lore.add(TextUtil.RED + "Inactive: " + group.inactive());

            if (!group.nodeTypes().isEmpty()) {
                lore.add(TextUtil.GRAY + "Node type: " + TextUtil.WHITE + nodeTypes(group.nodeTypes()));
            }

            lore.add("");
            lore.add(TextUtil.YELLOW + "Click to view each connected node.");
            lore.add(TextUtil.DARK_GRAY + "Inactive nodes are listed first");
            lore.add(TextUtil.DARK_GRAY + "inside the detail view.");
            meta.lore(TextUtil.components(lore));
            display.setItemMeta(meta);
        }

        return display;
    }

    private static @NotNull ItemStack nodeDisplay(
        @NotNull MachineGroup group,
        @NotNull NodeSnapshot node,
        int ordinal) {

        final ItemStack display = group.icon().clone();
        display.setAmount(1);
        final ItemMeta meta = display.getItemMeta();

        if (meta != null) {
            final Location location = node.location();
            meta.displayName(TextUtil.component(
                (node.health() == NodeHealth.ACTIVE ? TextUtil.GREEN : TextUtil.RED)
                    + group.displayName() + TextUtil.GRAY + " #" + ordinal));

            final List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(TextUtil.GRAY + "Status: " + node.health().coloredLabel());
            if (node.health() != NodeHealth.ACTIVE) {
                lore.add(TextUtil.GRAY + "Reason: " + TextUtil.RED + node.health().description());
            }
            lore.add(TextUtil.GRAY + "World: " + TextUtil.WHITE + worldName(location));
            lore.add(TextUtil.GRAY + "Location: " + TextUtil.WHITE
                + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ());
            if (node.nodeType() != null) {
                lore.add(TextUtil.GRAY + "Node type: " + TextUtil.WHITE + prettyNodeType(node.nodeType()));
            }
            if (node.sfId() != null && !node.sfId().isBlank()) {
                lore.add(TextUtil.GRAY + "Slimefun ID: " + TextUtil.DARK_GRAY + node.sfId());
            }

            lore.add("");
            if (node.health() == NodeHealth.CHUNK_UNLOADED) {
                lore.add(TextUtil.DARK_GRAY + "Chunk is unloaded; Monitor will");
                lore.add(TextUtil.DARK_GRAY + "not force-load it for diagnostics.");
            } else {
                lore.add(TextUtil.YELLOW + "Click to highlight this block.");
            }

            meta.lore(TextUtil.components(lore));
            display.setItemMeta(meta);
        }

        return display;
    }

    private static @NotNull ItemStack summaryItem(
        @NotNull MonitorSnapshot snapshot,
        int page,
        int maxPage) {

        final List<String> lore = new ArrayList<>();
        lore.add("");
        if (!snapshot.connected()) {
            lore.add(TextUtil.RED + "No active NetworkRoot found.");
            lore.add(TextUtil.GRAY + "Use Refresh after the controller");
            lore.add(TextUtil.GRAY + "and this monitor are connected.");
        } else {
            lore.add(TextUtil.GRAY + "Connected nodes: " + TextUtil.WHITE
                + snapshot.totalNodes() + " / " + snapshot.maxNodes());
            lore.add(TextUtil.GREEN + "Active: " + snapshot.activeNodes());
            lore.add(TextUtil.RED + "Inactive: " + snapshot.inactiveNodes());
            lore.add(TextUtil.GRAY + "Machine types: " + TextUtil.WHITE + snapshot.groups().size());
            lore.add(TextUtil.GRAY + "Page: " + TextUtil.WHITE + (page + 1) + " / " + (maxPage + 1));
            lore.add(TextUtil.GRAY + "Network state: "
                + (snapshot.overburdened() ? TextUtil.RED + "Overburdened" : TextUtil.GREEN + "OK"));
        }
        lore.add("");
        lore.add(TextUtil.YELLOW + "Click a machine type for individual nodes.");
        lore.add(TextUtil.DARK_GRAY + "This is the controller's current");
        lore.add(TextUtil.DARK_GRAY + "Networks topology, not EnergyNet.");

        return control(Material.COMPASS, TextUtil.GOLD + "Network Overview", lore);
    }

    private static @NotNull ItemStack backButton(@NotNull MachineGroup group) {
        return control(
            Material.OAK_DOOR,
            TextUtil.WHITE + "Back to Machine Types",
            List.of(
                "",
                TextUtil.GRAY + "Currently viewing:",
                TextUtil.WHITE + group.displayName(),
                "",
                TextUtil.YELLOW + "Click to return."));
    }

    private static @NotNull ItemStack groupFilterItem(
        @NotNull MachineGroup group,
        @NotNull NodeFilter filter,
        int visibleCount) {

        return control(
            filter.material(),
            TextUtil.AQUA + "Filter: " + filter.displayName(),
            List.of(
                "",
                TextUtil.GRAY + "Machine: " + TextUtil.WHITE + group.displayName(),
                TextUtil.GRAY + "Total: " + TextUtil.WHITE + group.total(),
                TextUtil.GREEN + "Active: " + group.active(),
                TextUtil.RED + "Inactive: " + group.inactive(),
                TextUtil.GRAY + "Currently shown: " + TextUtil.WHITE + visibleCount,
                "",
                TextUtil.YELLOW + "Click to cycle All / Active / Inactive."));
    }

    private static @NotNull ItemStack refreshItem(boolean refreshing) {
        if (refreshing) {
            return control(
                Material.YELLOW_DYE,
                TextUtil.YELLOW + "Refreshing Network...",
                List.of(
                    "",
                    TextUtil.GRAY + "Controller topology rediscovery",
                    TextUtil.GRAY + "has been queued."));
        }

        return control(
            Material.LIME_DYE,
            TextUtil.GREEN + "Refresh Network",
            List.of(
                "",
                TextUtil.GRAY + "Forces the Network Controller to",
                TextUtil.GRAY + "rediscover its connected nodes,",
                TextUtil.GRAY + "then rebuilds this machine list.",
                "",
                TextUtil.YELLOW + "Use after adding/removing machines."));
    }

    private static @NotNull ItemStack legendItem() {
        return control(
            Material.BOOK,
            TextUtil.AQUA + "Monitor Help",
            List.of(
                "",
                TextUtil.GRAY + "Hover a machine icon to see:",
                TextUtil.WHITE + "Total / Active / Inactive",
                "",
                TextUtil.YELLOW + "Click a machine type to inspect",
                TextUtil.YELLOW + "each individual connected node.",
                "",
                TextUtil.GREEN + "Active " + TextUtil.GRAY + "= loaded, resolved,",
                TextUtil.GRAY + "and assigned to this NetworkRoot.",
                TextUtil.RED + "Inactive " + TextUtil.GRAY + "= root contains the",
                TextUtil.GRAY + "node but its runtime state has an issue.",
                "",
                TextUtil.DARK_GRAY + "Storage direction is now the hopper",
                TextUtil.DARK_GRAY + "button in the bottom toolbar."));
    }

    private static @NotNull ItemStack pageButton(boolean next, int page, int maxPage) {
        final boolean enabled = next ? page < maxPage : page > 0;
        final String label = next ? "Next Page" : "Previous Page";
        return control(
            enabled ? Material.ARROW : Material.GRAY_DYE,
            (enabled ? TextUtil.WHITE : TextUtil.DARK_GRAY) + label,
            List.of(
                "",
                TextUtil.GRAY + "Page " + (page + 1) + " / " + (maxPage + 1)));
    }

    private static @NotNull ItemStack background() {
        return control(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private static void fillBackground(@NotNull BlockMenu blockMenu, int slot) {
        blockMenu.replaceExistingItem(slot, background());
        blockMenu.addMenuClickHandler(slot, (player, clickedSlot, item, action) -> false);
    }

    private void highlightNode(@NotNull Player player, @NotNull NodeSnapshot node) {
        final Location location = node.location();
        final World world = location.getWorld();

        if (!Networks.getConfigManager().isNetworkMonitorHighlightEnabled()) {
            player.sendMessage(TextUtil.YELLOW + "Network Monitor highlighting is disabled in config.");
            sendNodeCoordinates(player, location);
            return;
        }

        if (world == null) {
            player.sendMessage(TextUtil.RED + "This node's world is not available.");
            return;
        }

        if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            player.sendMessage(TextUtil.RED + "That node's chunk is unloaded; it will not be force-loaded.");
            sendNodeCoordinates(player, location);
            return;
        }

        if (!player.getWorld().equals(world)) {
            player.sendMessage(TextUtil.YELLOW + "That node is in another world and cannot be highlighted here.");
            sendNodeCoordinates(player, location);
            return;
        }

        if (player.getLocation().distanceSquared(location) > MAX_VISIBLE_HIGHLIGHT_DISTANCE_SQUARED) {
            player.sendMessage(TextUtil.YELLOW + "That node is too far away for a useful particle highlight.");
            sendNodeCoordinates(player, location);
            return;
        }

        final int seconds = Networks.getConfigManager().getNetworkMonitorHighlightSeconds();
        player.sendMessage(
            TextUtil.GREEN + "Highlighting network node for " + seconds + "s at "
                + coordinateText(location));

        new BukkitRunnable() {
            private int remainingPasses = Math.max(1, seconds * 2);

            @Override
            public void run() {
                if (!player.isOnline()
                    || !player.getWorld().equals(world)
                    || !world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                    cancel();
                    return;
                }

                spawnHighlightFrame(player, location);
                remainingPasses--;
                if (remainingPasses <= 0) {
                    cancel();
                }
            }
        }.runTaskTimer(Networks.getInstance(), 0L, 10L);
    }

    private static void spawnHighlightFrame(@NotNull Player player, @NotNull Location blockLocation) {
        final World world = blockLocation.getWorld();
        if (world == null) {
            return;
        }

        final double x = blockLocation.getBlockX();
        final double y = blockLocation.getBlockY();
        final double z = blockLocation.getBlockZ();
        final double low = 0.08D;
        final double high = 0.92D;

        final double[][] points = {
            {low, low, low}, {high, low, low}, {low, low, high}, {high, low, high},
            {low, high, low}, {high, high, low}, {low, high, high}, {high, high, high},
            {0.50D, low, low}, {0.50D, low, high}, {0.50D, high, low}, {0.50D, high, high},
            {low, 0.50D, low}, {high, 0.50D, low}, {low, 0.50D, high}, {high, 0.50D, high},
            {low, low, 0.50D}, {high, low, 0.50D}, {low, high, 0.50D}, {high, high, 0.50D}
        };

        for (double[] point : points) {
            player.spawnParticle(
                Particle.END_ROD,
                new Location(world, x + point[0], y + point[1], z + point[2]),
                1,
                0.0D,
                0.0D,
                0.0D,
                0.0D);
        }
    }

    private static void sendNodeCoordinates(@NotNull Player player, @NotNull Location location) {
        player.sendMessage(TextUtil.GRAY + "Node: " + TextUtil.WHITE + coordinateText(location));
    }

    private static @NotNull String coordinateText(@NotNull Location location) {
        return worldName(location) + " "
            + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ();
    }

    private static @NotNull ItemStack control(
        @NotNull Material material,
        @NotNull String name,
        @NotNull List<String> lore) {

        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.lore(TextUtil.components(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private static int pageCount(int entries) {
        return Math.max(1, (entries + INSPECTOR_DISPLAY_SLOTS.length - 1) / INSPECTOR_DISPLAY_SLOTS.length);
    }

    private static @NotNull String cleanName(@NotNull String name) {
        final String stripped = TextUtil.stripColor(name);
        return stripped == null || stripped.isBlank() ? "Unknown Machine" : stripped;
    }

    private static @NotNull String prettyNodeType(@NotNull NodeType type) {
        final String[] words = type.name().toLowerCase(Locale.ROOT).split("_");
        final StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (!builder.isEmpty()) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private static @NotNull String nodeTypes(@NotNull Set<NodeType> types) {
        return types.stream()
            .map(NetworkMonitor::prettyNodeType)
            .sorted(String.CASE_INSENSITIVE_ORDER)
            .reduce((left, right) -> left + ", " + right)
            .orElse("Unknown");
    }

    private static @NotNull String worldName(@NotNull Location location) {
        final World world = location.getWorld();
        return world == null ? "unknown-world" : world.getName();
    }

    private static @NotNull Location key(@NotNull Location location) {
        final Location copy = location.clone();
        copy.setX(location.getBlockX());
        copy.setY(location.getBlockY());
        copy.setZ(location.getBlockZ());
        copy.setYaw(0.0F);
        copy.setPitch(0.0F);
        return copy;
    }

    private static final class MutableMachineGroup {

        private final String key;
        private final String displayName;
        private final ItemStack icon;
        private final Set<NodeType> nodeTypes = EnumSet.noneOf(NodeType.class);
        private final List<NodeSnapshot> nodes = new ArrayList<>();
        private int active;

        private MutableMachineGroup(
            @NotNull String key,
            @NotNull String displayName,
            @NotNull ItemStack icon) {

            this.key = key;
            this.displayName = displayName;
            this.icon = icon;
        }

        private void add(@NotNull NodeSnapshot node) {
            nodes.add(node);
            if (node.health() == NodeHealth.ACTIVE) {
                active++;
            }
            if (node.nodeType() != null) {
                nodeTypes.add(node.nodeType());
            }
        }

        private @NotNull MachineGroup freeze() {
            return new MachineGroup(
                key,
                displayName,
                icon.clone(),
                Set.copyOf(nodeTypes),
                List.copyOf(nodes),
                nodes.size(),
                active,
                nodes.size() - active);
        }
    }

    private enum NodeFilter {
        ALL("All", Material.COMPASS),
        ACTIVE("Active", Material.LIME_DYE),
        INACTIVE("Inactive", Material.RED_DYE);

        private final String displayName;
        private final Material material;

        NodeFilter(@NotNull String displayName, @NotNull Material material) {
            this.displayName = displayName;
            this.material = material;
        }

        private boolean accepts(boolean active) {
            return this == ALL || (this == ACTIVE && active) || (this == INACTIVE && !active);
        }

        private @NotNull NodeFilter next() {
            return switch (this) {
                case ALL -> ACTIVE;
                case ACTIVE -> INACTIVE;
                case INACTIVE -> ALL;
            };
        }

        private @NotNull String displayName() {
            return displayName;
        }

        private @NotNull Material material() {
            return material;
        }
    }

    private enum NodeHealth {
        ACTIVE("Active", "Loaded, resolved and assigned to this NetworkRoot."),
        CHUNK_UNLOADED("Inactive", "Chunk unloaded"),
        NODE_MISSING("Inactive", "Runtime node definition missing"),
        NODE_UNASSIGNED("Inactive", "Node is not assigned to a live NetworkRoot"),
        WRONG_ROOT("Inactive", "Node is assigned to a different NetworkRoot"),
        BLOCK_DATA_MISSING("Inactive", "Slimefun block data is unavailable"),
        PENDING_REMOVE("Inactive", "Slimefun block is pending removal"),
        ITEM_UNRESOLVED("Inactive", "Slimefun item could not be resolved"),
        ITEM_ID_MISSING("Inactive", "Slimefun item ID is missing"),
        ITEM_ID_MISMATCH("Inactive", "Runtime Slimefun item does not match stored ID");

        private final String label;
        private final String description;

        NodeHealth(@NotNull String label, @NotNull String description) {
            this.label = label;
            this.description = description;
        }

        private @NotNull String coloredLabel() {
            return (this == ACTIVE ? TextUtil.GREEN : TextUtil.RED) + label;
        }

        private @NotNull String description() {
            return description;
        }
    }

    private record NodeSnapshot(
        @NotNull Location location,
        @Nullable NodeType nodeType,
        @NotNull NodeHealth health,
        @Nullable String sfId) {
    }

    private record MachineGroup(
        @NotNull String key,
        @NotNull String displayName,
        @NotNull ItemStack icon,
        @NotNull Set<NodeType> nodeTypes,
        @NotNull List<NodeSnapshot> nodes,
        int total,
        int active,
        int inactive) {
    }

    private record MonitorSnapshot(
        long revision,
        @NotNull List<MachineGroup> groups,
        int totalNodes,
        int activeNodes,
        int inactiveNodes,
        int maxNodes,
        boolean overburdened,
        boolean connected) {
    }

    private record ViewState(
        @Nullable String groupKey,
        int page,
        @NotNull NodeFilter filter) {

        private static @NotNull ViewState overview() {
            return new ViewState(null, 0, NodeFilter.ALL);
        }
    }

    private record RenderState(
        long revision,
        @Nullable String groupKey,
        int page,
        @NotNull NodeFilter filter) {
    }
}

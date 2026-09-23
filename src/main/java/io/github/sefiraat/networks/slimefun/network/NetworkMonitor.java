package io.github.sefiraat.networks.slimefun.network;

import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
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
import me.mrCookieSlime.CSCoreLibPlugin.general.Inventory.ClickAction;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
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
     * Keep the six inherited directional slots untouched so Network Monitor still performs its historical
     * storage-monitor job. The remaining four upper rows become a read-only topology inspector when enabled.
     */
    private static final int[] INSPECTOR_DISPLAY_SLOTS = {
        0, 1, 2, 3, 4, 5, 6, 7, 8,
        9, 10, 11, 13, 14, 16, 17, 18, 19,
        21, 23, 24, 25, 26, 27, 28, 29,
        31, 32, 34, 35
    };
    private static final int PAGE_PREVIOUS_SLOT = 36;
    private static final int SUMMARY_SLOT = 38;
    private static final int REFRESH_SLOT = 40;
    private static final int LEGEND_SLOT = 42;
    private static final int PAGE_NEXT_SLOT = 44;

    private static final Map<Location, Integer> PAGE_MAP = new ConcurrentHashMap<>();
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
        PAGE_MAP.remove(location);
        SNAPSHOT_MAP.remove(location);
        RENDERED_STATE_MAP.remove(location);
        LAST_REFRESH_REQUEST.remove(location);
        super.onBreak(event);
    }

    private void renderInspector(@NotNull BlockMenu blockMenu, boolean force) {
        final Location monitorLocation = key(blockMenu.getLocation());
        MonitorSnapshot snapshot = SNAPSHOT_MAP.computeIfAbsent(monitorLocation, ignored -> createSnapshot(blockMenu));

        final int maxPage = Math.max(0, pageCount(snapshot.groups().size()) - 1);
        final int page = Math.min(Math.max(0, PAGE_MAP.getOrDefault(monitorLocation, 0)), maxPage);
        PAGE_MAP.put(monitorLocation, page);

        final RenderState previous = RENDERED_STATE_MAP.get(monitorLocation);
        if (!force && previous != null && previous.revision() == snapshot.revision() && previous.page() == page) {
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
                blockMenu.addMenuClickHandler(slot, (player, clickedSlot, item, action) -> false);
            } else {
                blockMenu.replaceExistingItem(slot, background());
                blockMenu.addMenuClickHandler(slot, (player, clickedSlot, item, action) -> false);
            }
        }

        blockMenu.replaceExistingItem(PAGE_PREVIOUS_SLOT, pageButton(false, page, maxPage));
        blockMenu.addMenuClickHandler(PAGE_PREVIOUS_SLOT, (player, slot, item, action) -> {
            if (page > 0) {
                PAGE_MAP.put(monitorLocation, page - 1);
                renderInspector(blockMenu, true);
            }
            return false;
        });

        blockMenu.replaceExistingItem(PAGE_NEXT_SLOT, pageButton(true, page, maxPage));
        blockMenu.addMenuClickHandler(PAGE_NEXT_SLOT, (player, slot, item, action) -> {
            if (page < maxPage) {
                PAGE_MAP.put(monitorLocation, page + 1);
                renderInspector(blockMenu, true);
            }
            return false;
        });

        blockMenu.replaceExistingItem(SUMMARY_SLOT, summaryItem(snapshot, page, maxPage));
        blockMenu.addMenuClickHandler(SUMMARY_SLOT, (player, slot, item, action) -> false);

        blockMenu.replaceExistingItem(LEGEND_SLOT, legendItem());
        blockMenu.addMenuClickHandler(LEGEND_SLOT, (player, slot, item, action) -> false);

        blockMenu.replaceExistingItem(REFRESH_SLOT, refreshItem(false));
        blockMenu.addMenuClickHandler(REFRESH_SLOT, (player, slot, item, action) -> {
            requestTopologyRefresh(player, blockMenu);
            return false;
        });

        RENDERED_STATE_MAP.put(monitorLocation, new RenderState(snapshot.revision(), page));
    }

    private void requestTopologyRefresh(@NotNull Player player, @NotNull BlockMenu blockMenu) {
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
        blockMenu.addMenuClickHandler(REFRESH_SLOT, (p, slot, item, action) -> false);

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
                slimefunItem = StorageCacheUtils.getSfItem(location);
            }

            final boolean active = isActive(root, definition, data, slimefunItem, chunkLoaded);
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

            groups.computeIfAbsent(groupKey, ignored -> new MutableMachineGroup(displayName, icon))
                .add(nodeType, active);
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

    private static boolean isActive(
        @NotNull NetworkRoot root,
        @Nullable NodeDefinition definition,
        @Nullable SlimefunBlockData data,
        @Nullable SlimefunItem item,
        boolean chunkLoaded) {

        if (!chunkLoaded
            || definition == null
            || definition.getNode() == null
            || definition.getNode().getRoot() != root
            || data == null
            || data.isPendingRemove()
            || !data.isDataLoaded()
            || item == null) {
            return false;
        }

        final String sfId = data.getSfId();
        return sfId != null && sfId.equals(item.getId());
    }

    private static @Nullable NetworkRoot findRoot(@NotNull BlockMenu blockMenu) {
        final NodeDefinition definition = NetworkStorage.getNode(blockMenu.getLocation());
        if (definition == null || definition.getNode() == null) {
            return null;
        }
        return definition.getNode().getRoot();
    }

    private static @NotNull ItemStack groupDisplay(@NotNull MachineGroup group) {
        final ItemStack display = group.icon().clone();
        display.setAmount(1);
        final ItemMeta meta = display.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(ChatColor.WHITE + group.displayName() + ChatColor.GRAY + "  x" + group.total());

            final List<String> lore = new ArrayList<>();
            lore.add("");
            lore.add(ChatColor.GRAY + "Total connected: " + ChatColor.WHITE + group.total());
            lore.add(ChatColor.GREEN + "Active: " + group.active());
            lore.add(ChatColor.RED + "Inactive: " + group.inactive());

            if (!group.nodeTypes().isEmpty()) {
                lore.add(ChatColor.GRAY + "Node type: " + ChatColor.WHITE + nodeTypes(group.nodeTypes()));
            }

            lore.add("");
            lore.add(ChatColor.DARK_GRAY + "Active = loaded and resolved");
            lore.add(ChatColor.DARK_GRAY + "by the current NetworkRoot.");
            meta.setLore(lore);
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
            lore.add(ChatColor.RED + "No active NetworkRoot found.");
            lore.add(ChatColor.GRAY + "Use Refresh after the controller");
            lore.add(ChatColor.GRAY + "and this monitor are connected.");
        } else {
            lore.add(ChatColor.GRAY + "Connected nodes: " + ChatColor.WHITE
                + snapshot.totalNodes() + " / " + snapshot.maxNodes());
            lore.add(ChatColor.GREEN + "Active: " + snapshot.activeNodes());
            lore.add(ChatColor.RED + "Inactive: " + snapshot.inactiveNodes());
            lore.add(ChatColor.GRAY + "Machine types: " + ChatColor.WHITE + snapshot.groups().size());
            lore.add(ChatColor.GRAY + "Page: " + ChatColor.WHITE + (page + 1) + " / " + (maxPage + 1));
            lore.add(ChatColor.GRAY + "Network state: "
                + (snapshot.overburdened() ? ChatColor.RED + "Overburdened" : ChatColor.GREEN + "OK"));
        }
        lore.add("");
        lore.add(ChatColor.DARK_GRAY + "This is the controller's current");
        lore.add(ChatColor.DARK_GRAY + "Networks topology, not EnergyNet.");

        return control(Material.COMPASS, ChatColor.GOLD + "Network Overview", lore);
    }

    private static @NotNull ItemStack refreshItem(boolean refreshing) {
        if (refreshing) {
            return control(
                Material.YELLOW_DYE,
                ChatColor.YELLOW + "Refreshing Network...",
                List.of(
                    "",
                    ChatColor.GRAY + "Controller topology rediscovery",
                    ChatColor.GRAY + "has been queued."));
        }

        return control(
            Material.LIME_DYE,
            ChatColor.GREEN + "Refresh Network",
            List.of(
                "",
                ChatColor.GRAY + "Forces the Network Controller to",
                ChatColor.GRAY + "rediscover its connected nodes,",
                ChatColor.GRAY + "then rebuilds this machine list.",
                "",
                ChatColor.YELLOW + "Use after adding/removing machines."));
    }

    private static @NotNull ItemStack legendItem() {
        return control(
            Material.BOOK,
            ChatColor.AQUA + "Monitor Help",
            List.of(
                "",
                ChatColor.GRAY + "Hover a machine icon to see:",
                ChatColor.WHITE + "Total / Active / Inactive",
                "",
                ChatColor.GREEN + "Active " + ChatColor.GRAY + "= loaded, resolved,",
                ChatColor.GRAY + "and assigned to this NetworkRoot.",
                ChatColor.RED + "Inactive " + ChatColor.GRAY + "= present in the",
                ChatColor.GRAY + "root snapshot but not fully resolved.",
                "",
                ChatColor.DARK_GRAY + "The six center controls still set",
                ChatColor.DARK_GRAY + "the Monitor's storage-facing side."));
    }

    private static @NotNull ItemStack pageButton(boolean next, int page, int maxPage) {
        final boolean enabled = next ? page < maxPage : page > 0;
        final String label = next ? "Next Page" : "Previous Page";
        return control(
            enabled ? Material.ARROW : Material.GRAY_DYE,
            (enabled ? ChatColor.WHITE : ChatColor.DARK_GRAY) + label,
            List.of(
                "",
                ChatColor.GRAY + "Page " + (page + 1) + " / " + (maxPage + 1)));
    }

    private static @NotNull ItemStack background() {
        return control(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
    }

    private static @NotNull ItemStack control(
        @NotNull Material material,
        @NotNull String name,
        @NotNull List<String> lore) {

        final ItemStack item = new ItemStack(material);
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static int pageCount(int entries) {
        return Math.max(1, (entries + INSPECTOR_DISPLAY_SLOTS.length - 1) / INSPECTOR_DISPLAY_SLOTS.length);
    }

    private static @NotNull String cleanName(@NotNull String name) {
        final String stripped = ChatColor.stripColor(name);
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

        private final String displayName;
        private final ItemStack icon;
        private final Set<NodeType> nodeTypes = EnumSet.noneOf(NodeType.class);
        private int total;
        private int active;

        private MutableMachineGroup(@NotNull String displayName, @NotNull ItemStack icon) {
            this.displayName = displayName;
            this.icon = icon;
        }

        private void add(@Nullable NodeType nodeType, boolean isActive) {
            total++;
            if (isActive) {
                active++;
            }
            if (nodeType != null) {
                nodeTypes.add(nodeType);
            }
        }

        private @NotNull MachineGroup freeze() {
            return new MachineGroup(
                displayName,
                icon.clone(),
                Set.copyOf(nodeTypes),
                total,
                active,
                total - active);
        }
    }

    private record MachineGroup(
        @NotNull String displayName,
        @NotNull ItemStack icon,
        @NotNull Set<NodeType> nodeTypes,
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

    private record RenderState(long revision, int page) {
    }
}

package io.github.sefiraat.networks.network;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.slimefun.network.NetworkPowerNode;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import lombok.Getter;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkNode {

    public static final Set<BlockFace> VALID_FACES =
        EnumSet.of(BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST);

    private static final Set<Location> PENDING_CONTROLLER_REMOVALS = ConcurrentHashMap.newKeySet();

    @Getter
    protected final Set<NetworkNode> childrenNodes = new HashSet<>();

    protected final Location nodePosition;
    protected final NodeType nodeType;

    @Getter
    protected final long power;

    @Getter
    protected @Nullable NetworkNode parent = null;

    @Getter
    protected NetworkRoot root = null;

    public NetworkNode(Location location, NodeType type) {
        this.nodePosition = location;
        this.nodeType = type;
        this.power = retrieveBlockCharge();
    }

    public void addChild(@NotNull NetworkNode child) {
        child.setParent(this);
        child.setRoot(this.getRoot());
        if (this.root != null) {
            this.root.addRootPower(child.getPower());
            this.root.registerNode(child.nodePosition, child.nodeType);
        }
        this.childrenNodes.add(child);
    }

    @NotNull
    public Location getNodePosition() {
        return nodePosition;
    }

    @NotNull
    public NodeType getNodeType() {
        return nodeType;
    }

    public boolean networkContains(@NotNull NetworkNode networkNode) {
        return networkContains(networkNode.nodePosition);
    }

    public boolean networkContains(@NotNull Location location) {
        if (this.root == null) {
            return false;
        }

        return this.root.getNodeLocations().contains(location);
    }

    private void setRoot(@Nullable NetworkRoot root) {
        this.root = root;
    }

    private void setParent(@Nullable NetworkNode parent) {
        this.parent = parent;
    }

    public void addAllChildren() {
        if (this.root == null) {
            return;
        }

        final Location rootControllerLocation = normalizeBlockLocation(this.root.nodePosition);
        Deque<NetworkNode> nodeStack = new ArrayDeque<>(200);
        nodeStack.push(this);

        while (!nodeStack.isEmpty()) {
            NetworkNode currentNode = nodeStack.pop();

            /*
             * Full controller rebuilds can visit thousands of nodes. Reuse one mutable Location for the six
             * neighbour lookups instead of cloning a Location for every graph edge. NetworkStorage#getNode is
             * read-only with respect to caller keys, and a real neighbour gets its own owned clone below.
             */
            final Location baseLocation = currentNode.nodePosition;
            final Location probeLocation = baseLocation.clone();
            final double baseX = baseLocation.getX();
            final double baseY = baseLocation.getY();
            final double baseZ = baseLocation.getZ();

            // Loop through all possible locations
            for (BlockFace face : VALID_FACES) {
                probeLocation.setX(baseX + face.getModX());
                probeLocation.setY(baseY + face.getModY());
                probeLocation.setZ(baseZ + face.getModZ());

                final NodeDefinition testDefinition = NetworkStorage.getTopologyNode(probeLocation);

                if (testDefinition == null) {
                    continue;
                }

                final Location testLocation = probeLocation.clone();
                final NodeType testType = testDefinition.getType();

                // Resolve invalid multi-controller topologies deterministically. If both controller
                // tickers discover each other in the same cycle they must choose the same loser; otherwise each
                // side can queue deletion of the other and remove both controllers.
                if (testType == NodeType.CONTROLLER && !testLocation.equals(this.root.nodePosition)) {
                    final Location otherControllerLocation = normalizeBlockLocation(testLocation);
                    final Location removalTarget = chooseControllerForRemoval(
                        rootControllerLocation,
                        NetworkController.getNetworks().containsKey(rootControllerLocation),
                        otherControllerLocation,
                        NetworkController.getNetworks().containsKey(otherControllerLocation));
                    if (removalTarget != null) {
                        final Location survivor = removalTarget.equals(rootControllerLocation)
                            ? normalizeBlockLocation(testLocation)
                            : rootControllerLocation;
                        queueAdditionalControllerRemoval(
                            removalTarget,
                            survivor,
                            this.root.getMaxNodes());
                        if (removalTarget.equals(rootControllerLocation)) {
                            return;
                        }
                    }
                    continue;
                }

                // Check if it's in the network already and, if not, create a child node and propagate further.
                if (testType != NodeType.CONTROLLER && !currentNode.networkContains(testLocation)) {
                    if (this.root.getNodeCount() >= this.root.getMaxNodes()) {
                        this.root.setOverburdened(true);
                        return;
                    }
                    final NetworkNode networkNode = new NetworkNode(testLocation, testType);
                    currentNode.addChild(networkNode);

                    nodeStack.push(networkNode);
                    /*
                     * testDefinition came directly from NetworkStorage#getNode, so this physical node is
                     * already present in the authoritative registry and chunk index. Full topology discovery
                     * only needs to attach the fresh runtime NetworkNode; re-registering the same definition
                     * would repeat key normalization, CHM compute and ChunkPosition/index work for every node.
                     */
                    testDefinition.setNode(networkNode);
                }
            }
        }
    }

    private void queueAdditionalControllerRemoval(
        @NotNull Location removalTarget,
        @NotNull Location survivingController,
        int maximumNodes) {

        final Location controllerLocation = normalizeBlockLocation(removalTarget);
        final Location survivorLocation = normalizeBlockLocation(survivingController);

        // Both roots in a bad topology choose the same deterministic loser. The reservation then ensures that
        // multiple graph edges and both controller tickers still produce only one physical removal/drop.
        if (!PENDING_CONTROLLER_REMOVALS.add(controllerLocation)) {
            return;
        }

        // The surviving root may have had some NodeDefinition assignments temporarily touched by the losing
        // discovery pass. Force one clean discovery after conflict resolution.
        NetworkController.markTopologyDirty(survivorLocation);

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Re-read both controller identities and the physical registry graph on the server thread.
                    // If the winner was broken or the connecting cable was removed before this delayed task ran,
                    // the former loser is valid again and must not be destroyed.
                    SlimefunItem liveLoser = StorageCacheUtils.getSlimefunItem(controllerLocation);
                    SlimefunItem liveSurvivor = StorageCacheUtils.getSlimefunItem(survivorLocation);
                    if (!(liveLoser instanceof NetworkController)
                        || !(liveSurvivor instanceof NetworkController)
                        || !controllersStillConnected(survivorLocation, controllerLocation, maximumNodes)) {
                        return;
                    }

                    NetworkController.removeRuntimeState(controllerLocation);
                    NetworkStorage.removeNodeOnly(controllerLocation);
                    Slimefun.getDatabaseManager().getBlockDataController().removeBlock(controllerLocation);

                    if (controllerLocation.getWorld() != null
                        && controllerLocation.getBlock().getType() != Material.AIR) {
                        controllerLocation.getWorld().dropItemNaturally(controllerLocation, liveLoser.getItem());
                        controllerLocation.getBlock().setType(Material.AIR);
                    }

                    NetworkController.markTopologyDirty(survivorLocation);
                } finally {
                    PENDING_CONTROLLER_REMOVALS.remove(controllerLocation);
                }
            }
        };

        try {
            runnable.runTask(Networks.getInstance());
        } catch (RuntimeException exception) {
            PENDING_CONTROLLER_REMOVALS.remove(controllerLocation);
            throw exception;
        }
    }

    /**
     * Selects one controller to remove without depending on ticker/discovery order.
     *
     * <p>Connected controllers necessarily share a world. When exactly one side already owns a published root,
     * that established controller survives. If both or neither are established, the lower block-coordinate tuple
     * is retained and the higher tuple is removed. These rules matter only for an already-invalid multi-controller
     * topology; normal player placement is rejected before the topology exists.</p>
     */
    static @Nullable Location chooseControllerForRemoval(
        @NotNull Location firstController,
        @NotNull Location secondController) {
        return chooseControllerForRemoval(firstController, false, secondController, false);
    }

    static @Nullable Location chooseControllerForRemoval(
        @NotNull Location firstController,
        boolean firstHasActiveRoot,
        @NotNull Location secondController,
        boolean secondHasActiveRoot) {

        final Location first = normalizeBlockLocation(firstController);
        final Location second = normalizeBlockLocation(secondController);

        if (first.getWorld() != second.getWorld() || first.equals(second)) {
            return null;
        }

        // Preserve an already-established network when exactly one controller owns a published root.
        // This makes a newly introduced conflicting controller the loser without depending on tick order.
        if (firstHasActiveRoot != secondHasActiveRoot) {
            return firstHasActiveRoot ? second : first;
        }

        // If both or neither are established, use an order-independent coordinate tie-break.
        int comparison = Integer.compare(first.getBlockX(), second.getBlockX());
        if (comparison == 0) {
            comparison = Integer.compare(first.getBlockY(), second.getBlockY());
        }
        if (comparison == 0) {
            comparison = Integer.compare(first.getBlockZ(), second.getBlockZ());
        }

        return comparison <= 0 ? second : first;
    }

    public static boolean isControllerRemovalPending(@NotNull Location location) {
        return PENDING_CONTROLLER_REMOVALS.contains(normalizeBlockLocation(location));
    }

    /** Clears delayed-removal reservations during plugin/runtime shutdown. */
    public static void clearPendingControllerRemovals() {
        PENDING_CONTROLLER_REMOVALS.clear();
    }

    /**
     * Revalidates a delayed destructive action against the loaded physical node registry.
     *
     * <p>Controller removal is rare, so a bounded graph walk is preferable to deleting a controller after the
     * topology has already changed. Other controllers terminate traversal; only the selected loser is accepted.</p>
     */
    private static boolean controllersStillConnected(
        @NotNull Location survivingController,
        @NotNull Location removalTarget,
        int maximumNodes) {

        final Location survivor = normalizeBlockLocation(survivingController);
        final Location target = normalizeBlockLocation(removalTarget);
        if (survivor.getWorld() != target.getWorld() || survivor.equals(target)) {
            return false;
        }

        final NodeDefinition survivorDefinition = NetworkStorage.getTopologyNode(survivor);
        final NodeDefinition targetDefinition = NetworkStorage.getTopologyNode(target);
        if (survivorDefinition == null
            || targetDefinition == null
            || survivorDefinition.getType() != NodeType.CONTROLLER
            || targetDefinition.getType() != NodeType.CONTROLLER) {
            return false;
        }

        final int visitBudget = Math.max(1, maximumNodes) + 2;
        final Set<Location> visited = new HashSet<>();
        final Deque<Location> pending = new ArrayDeque<>();
        pending.add(survivor);

        while (!pending.isEmpty() && visited.size() <= visitBudget) {
            final Location current = pending.removeFirst();
            if (!visited.add(current)) {
                continue;
            }

            for (BlockFace face : VALID_FACES) {
                final Location adjacent = normalizeBlockLocation(current.clone().add(face.getDirection()));
                final NodeDefinition definition = NetworkStorage.getTopologyNode(adjacent);
                if (definition == null) {
                    continue;
                }

                if (adjacent.equals(target)) {
                    return definition.getType() == NodeType.CONTROLLER;
                }

                if (definition.getType() == NodeType.CONTROLLER || visited.contains(adjacent)) {
                    continue;
                }

                pending.addLast(adjacent);
            }
        }

        return false;
    }

    private static @NotNull Location normalizeBlockLocation(@NotNull Location location) {
        Location normalized = location.clone();
        normalized.setX(location.getBlockX());
        normalized.setY(location.getBlockY());
        normalized.setZ(location.getBlockZ());
        normalized.setYaw(0.0F);
        normalized.setPitch(0.0F);
        return normalized;
    }

    protected long retrieveBlockCharge() {
        if (this.nodeType == NodeType.POWER_NODE) {
            long blockCharge = 0L;
            final SlimefunItem item = StorageCacheUtils.getSlimefunItem(this.nodePosition);
            if (item instanceof NetworkPowerNode powerNode) {
                blockCharge = powerNode.getChargeLong(this.nodePosition);
            }
            return blockCharge;
        }
        return 0;
    }
}

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

        Deque<NetworkNode> nodeStack = new ArrayDeque<>(200);
        nodeStack.push(this);

        while (!nodeStack.isEmpty()) {
            NetworkNode currentNode = nodeStack.pop();

            // Loop through all possible locations
            for (BlockFace face : VALID_FACES) {
                final Location testLocation = currentNode.nodePosition.clone().add(face.getDirection());
                final NodeDefinition testDefinition = NetworkStorage.getNode(testLocation);

                if (testDefinition == null) {
                    continue;
                }

                final NodeType testType = testDefinition.getType();

                // Kill additional controllers if it isn't the root
                if (testType == NodeType.CONTROLLER && !testLocation.equals(this.root.nodePosition)) {
                    killAdditionalController(testLocation);
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
                    testDefinition.setNode(networkNode);
                    NetworkStorage.registerNode(testLocation, testDefinition);
                }
            }
        }
    }

    private void killAdditionalController(@NotNull Location location) {
        final Location controllerLocation = normalizeBlockLocation(location);

        // A controller can be reached from more than one graph edge during the same discovery pass. Without this
        // reservation, each edge can queue its own delayed removal and drop the same controller item more than once.
        if (!PENDING_CONTROLLER_REMOVALS.add(controllerLocation)) {
            return;
        }

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                try {
                    // Re-read the live Slimefun identity on the server thread. If the controller was already broken,
                    // replaced, or cleaned up by another path, there is nothing left for this task to drop.
                    SlimefunItem liveItem = StorageCacheUtils.getSfItem(controllerLocation);
                    if (!(liveItem instanceof NetworkController)) {
                        return;
                    }

                    NetworkController.removeRuntimeState(controllerLocation);
                    NetworkStorage.removeNodeOnly(controllerLocation);
                    Slimefun.getDatabaseManager().getBlockDataController().removeBlock(controllerLocation);

                    if (controllerLocation.getWorld() != null
                        && controllerLocation.getBlock().getType() != Material.AIR) {
                        controllerLocation.getWorld().dropItemNaturally(controllerLocation, liveItem.getItem());
                        controllerLocation.getBlock().setType(Material.AIR);
                    }
                } finally {
                    PENDING_CONTROLLER_REMOVALS.remove(controllerLocation);
                }
            }
        };
        runnable.runTask(Networks.getInstance());
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
            int blockCharge = 0;
            final SlimefunItem item = StorageCacheUtils.getSfItem(this.nodePosition);
            if (item instanceof NetworkPowerNode powerNode) {
                blockCharge = powerNode.getCharge(this.nodePosition);
            }
            return blockCharge;
        }
        return 0;
    }
}

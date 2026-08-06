package io.github.sefiraat.networks.slimefun.network;

import com.balugaq.netex.api.data.ItemFlowRecord;
import com.balugaq.netex.api.events.NetworkRootReadyEvent;
import com.balugaq.netex.utils.Lang;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.NetworkNode;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.utils.FailureCircuitBreaker;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import lombok.Getter;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

@Getter
public class NetworkController extends NetworkObject {
    private static final int DEFAULT_FAILURE_THRESHOLD = 3;
    private static final long DEFAULT_COOLDOWN_MILLIS = 30_000L;
    private static final long DEFAULT_MAXIMUM_COOLDOWN_MILLIS = 300_000L;

    @Getter
    private static final Map<Location, ItemFlowRecord> records = new ConcurrentHashMap<>();

    @Getter
    private static final Map<Location, Boolean> recordFlow = new ConcurrentHashMap<>();

    private static final String CRAYON = "crayon";
    private static final Map<Location, NetworkRoot> NETWORKS = new ConcurrentHashMap<>();
    private static final Set<Location> CRAYONS = ConcurrentHashMap.newKeySet();
    private static final Map<Location, Boolean> INITIALIZED_CONTROLLERS = new ConcurrentHashMap<>();

    private static volatile boolean circuitBreakerEnabled = true;
    private static volatile FailureCircuitBreaker<Location> controllerCircuitBreaker = new FailureCircuitBreaker<>(
        DEFAULT_FAILURE_THRESHOLD,
        DEFAULT_COOLDOWN_MILLIS,
        DEFAULT_MAXIMUM_COOLDOWN_MILLIS);

    /** Preserved for binary/source compatibility with controller subclasses. */
    protected final Map<Location, Boolean> firstTickMap = INITIALIZED_CONTROLLERS;

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
                return Networks.getConfigManager().useSynchronizedMachineTickers();
            }

            @Override
            public void tick(@NotNull Block block, SlimefunItem item, @NotNull SlimefunBlockData data) {
                final Location location = block.getLocation();
                if (block.getType() == Material.AIR || !getId().equals(data.getSfId())) {
                    firstTickMap.remove(location);
                    removeRuntimeState(location);
                    return;
                }

                final long now = System.currentTimeMillis();
                if (circuitBreakerEnabled && !controllerCircuitBreaker.canAttempt(location, now)) {
                    return;
                }

                NetworkRoot candidate = null;
                try {
                    if (!firstTickMap.containsKey(location)) {
                        onFirstTick(block, data);
                        firstTickMap.put(location, true);
                    }

                    addToRegistry(block);
                    candidate = new NetworkRoot(
                        location,
                        NodeType.CONTROLLER,
                        maxNodes.getValue(),
                        recordFlow.getOrDefault(location, false),
                        records.get(location));
                    candidate.addAllChildren();

                    if (CRAYONS.contains(location)) {
                        candidate.setDisplayParticles(true);
                    }

                    NetworkRoot previous = NETWORKS.put(location, candidate);
                    if (previous != null && previous != candidate) {
                        NetworkStorage.clearRuntimeAssignments(previous);
                    }

                    NodeDefinition definition = NetworkStorage.getNode(location);
                    if (definition != null) {
                        definition.setNode(candidate);
                    }
                    Bukkit.getPluginManager().callEvent(new NetworkRootReadyEvent(candidate));

                    FailureCircuitBreaker.FailureSnapshot previous = controllerCircuitBreaker.recordSuccess(location);
                    if (previous != null && previous.consecutiveFailures() > 0) {
                        Networks.getInstance().getLogger().info(
                            "Network controller recovered at " + format(location)
                                + " after " + previous.consecutiveFailures() + " failed rebuild attempt(s).");
                    }
                } catch (RuntimeException | LinkageError exception) {
                    discardFailedBuild(location, candidate);
                    FailureCircuitBreaker.FailureSnapshot failure =
                        controllerCircuitBreaker.recordFailure(location, exception, now);
                    if (failure.consecutiveFailures() == 1 || failure.trippedNow()) {
                        long cooldownSeconds = Math.max(1L, failure.remainingCooldownMillis(now) / 1000L);
                        String action = failure.trippedNow() && circuitBreakerEnabled
                            ? " Rebuilds are paused for approximately " + cooldownSeconds + " second(s)."
                            : " Networks will retry on a later controller tick.";
                        Networks.getInstance().getLogger().log(
                            Level.WARNING,
                            "Network controller rebuild failed at " + format(location)
                                + " (attempt " + failure.consecutiveFailures() + ", "
                                + failure.failureType() + ": " + failure.failureMessage() + ")." + action);
                    }
                }
            }
        });
    }

    public static void configureRuntimeSafety(@NotNull Networks plugin) {
        circuitBreakerEnabled = plugin.getConfig().getBoolean("stability.controller-circuit-breaker.enabled", true);
        int configuredThreshold = Math.max(1, plugin.getConfig().getInt(
            "stability.controller-circuit-breaker.failure-threshold", DEFAULT_FAILURE_THRESHOLD));
        int threshold = circuitBreakerEnabled ? configuredThreshold : Integer.MAX_VALUE;
        long initialCooldown = secondsToMillis(plugin.getConfig().getLong(
            "stability.controller-circuit-breaker.cooldown-seconds", DEFAULT_COOLDOWN_MILLIS / 1000L));
        long maximumCooldown = Math.max(initialCooldown, secondsToMillis(plugin.getConfig().getLong(
            "stability.controller-circuit-breaker.maximum-cooldown-seconds",
            DEFAULT_MAXIMUM_COOLDOWN_MILLIS / 1000L)));
        controllerCircuitBreaker = new FailureCircuitBreaker<>(threshold, initialCooldown, maximumCooldown);
    }

    public static void resetRuntimeSafety() {
        controllerCircuitBreaker.clearAll();
        INITIALIZED_CONTROLLERS.clear();
        circuitBreakerEnabled = true;
    }

    public static @NotNull Map<Location, FailureCircuitBreaker.FailureSnapshot> getControllerFailures() {
        return controllerCircuitBreaker.snapshot();
    }

    public static int getTrackedControllerFailureCount() {
        return controllerCircuitBreaker.getTrackedKeyCount();
    }

    public static int getQuarantinedControllerCount(long nowMillis) {
        return circuitBreakerEnabled ? controllerCircuitBreaker.getBlockedKeyCount(nowMillis) : 0;
    }

    public static long getTotalControllerFailureCount() {
        return controllerCircuitBreaker.getTotalFailures();
    }

    public static long getTotalControllerTripCount() {
        return controllerCircuitBreaker.getTotalTrips();
    }

    public static void clearControllerFailure(@NotNull Location location) {
        controllerCircuitBreaker.clear(location);
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

    /**
     * Drops only a built runtime tree. Loaded node registrations remain available for the controller's next
     * rebuild, which is required for transient failures and chunk unload/reload cycles.
     */
    public static void discardRuntimeNetwork(@NotNull Location location) {
        NetworkRoot root = NETWORKS.remove(location);
        if (root != null) {
            NetworkStorage.clearRuntimeAssignments(root);
        }
    }

    public static void onChunkUnload(@NotNull Chunk chunk) {
        for (Location location : new HashSet<>(NETWORKS.keySet())) {
            if (isInChunk(location, chunk)) {
                discardRuntimeNetwork(location);
                INITIALIZED_CONTROLLERS.remove(location);
                CRAYONS.remove(location);
                controllerCircuitBreaker.clear(location);
            }
        }
        INITIALIZED_CONTROLLERS.keySet().removeIf(location -> isInChunk(location, chunk));
    }

    /** Clears every runtime cache associated with a controller location. */
    public static void removeRuntimeState(@NotNull Location location) {
        wipeNetwork(location);
        NETWORKS.remove(location);
        records.remove(location);
        recordFlow.remove(location);
        CRAYONS.remove(location);
        INITIALIZED_CONTROLLERS.remove(location);
        controllerCircuitBreaker.clear(location);
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

    private static void discardFailedBuild(@NotNull Location location, NetworkRoot candidate) {
        NetworkRoot installed = NETWORKS.remove(location);
        if (installed != null) {
            NetworkStorage.clearRuntimeAssignments(installed);
        }
        if (candidate != null && candidate != installed) {
            NetworkStorage.clearRuntimeAssignments(candidate);
        }
    }

    private static long secondsToMillis(long seconds) {
        long positiveSeconds = Math.max(1L, seconds);
        return positiveSeconds > Long.MAX_VALUE / 1000L ? Long.MAX_VALUE : positiveSeconds * 1000L;
    }

    private static boolean isInChunk(@NotNull Location location, @NotNull Chunk chunk) {
        World world = location.getWorld();
        return world != null
            && world.getUID().equals(chunk.getWorld().getUID())
            && (location.getBlockX() >> 4) == chunk.getX()
            && (location.getBlockZ() >> 4) == chunk.getZ();
    }

    private static @NotNull String format(@NotNull Location location) {
        String world = location.getWorld() == null ? "unknown" : location.getWorld().getName();
        return world + ':' + location.getBlockX() + ',' + location.getBlockY() + ',' + location.getBlockZ();
    }
}

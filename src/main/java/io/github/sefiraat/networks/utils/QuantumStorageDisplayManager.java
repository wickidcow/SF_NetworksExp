package io.github.sefiraat.networks.utils;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Optional world-side visuals for Network Quantum Storage.
 *
 * <p>The item display is cosmetic and is disabled by default in the Legacy build. When disabled,
 * previously tagged Networks displays are removed gradually from already-loaded chunks and from
 * chunks as they load. No cleanup path force-loads chunks and no Quantum Storage item or amount
 * data is changed.</p>
 */
public final class QuantumStorageDisplayManager {

    private static final Map<BlockKey, DisplayState> STATES = new HashMap<>();
    private static final Deque<Chunk> CLEANUP_QUEUE = new ArrayDeque<>();
    private static final Set<ChunkKey> CLEANUP_QUEUED = new HashSet<>();

    private static final float DISPLAY_SCALE = 0.50F;
    private static final long REFRESH_INTERVAL = 5L;
    private static final int CLEANUP_CHUNKS_PER_TICK = 4;
    private static final long CLEANUP_BUDGET_NANOS = 2_000_000L;

    private static boolean initialized;
    private static boolean lastDisplayEnabled;
    private static NamespacedKey displayKey;

    private QuantumStorageDisplayManager() {
    }

    public static void initialize() {
        if (initialized) {
            return;
        }

        final Networks plugin = Networks.getInstance();
        if (plugin == null) {
            return;
        }

        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, QuantumStorageDisplayManager::initialize);
            return;
        }

        initialized = true;
        displayKey = new NamespacedKey(plugin, "quantum_storage_item_display");
        lastDisplayEnabled = isDisplayEnabled();

        Networks.getPluginManager().registerEvents(new CleanupListener(), plugin);

        if (!lastDisplayEnabled) {
            queueAllLoadedChunksForCleanup();
        }

        Bukkit.getScheduler().runTaskTimer(
            plugin,
            QuantumStorageDisplayManager::refreshDisplays,
            1L,
            REFRESH_INTERVAL
        );
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            QuantumStorageDisplayManager::showHoverText,
            10L,
            5L
        );
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            QuantumStorageDisplayManager::processCleanupQueue,
            1L,
            1L
        );
        Bukkit.getScheduler().runTaskTimer(
            plugin,
            QuantumStorageDisplayManager::pruneStateCache,
            600L,
            600L
        );

        plugin.getLogger().info(
            "Quantum Storage world item displays are " + (lastDisplayEnabled ? "enabled." : "disabled by default."));
    }

    /**
     * Refreshes all currently cached Quantum Storage blocks on the server thread when the
     * optional item display is enabled.
     */
    private static void refreshDisplays() {
        final boolean enabled = isDisplayEnabled();
        handleDisplayToggle(enabled);
        if (!enabled) {
            return;
        }

        final Set<BlockKey> activeKeys = new HashSet<>();

        for (Map.Entry<Location, QuantumCache> entry : NetworkQuantumStorage.getCaches().entrySet()) {
            final Location location = entry.getKey();
            final QuantumCache cache = entry.getValue();
            if (location == null || cache == null || location.getWorld() == null) {
                continue;
            }

            final BlockKey key = BlockKey.from(location);
            activeKeys.add(key);

            final World world = location.getWorld();
            if (!world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
                continue;
            }

            final Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (!(StorageCacheUtils.getSfItem(block.getLocation()) instanceof NetworkQuantumStorage)) {
                continue;
            }

            final ItemStack storedItem;
            synchronized (cache) {
                final ItemStack cachedItem = cache.getItemStack();
                storedItem = cachedItem == null ? null : cachedItem.clone();
            }

            updateDisplay(block, key, storedItem);
        }

        cleanupInactiveDisplays(activeKeys);
    }

    private static void handleDisplayToggle(boolean enabled) {
        if (enabled == lastDisplayEnabled) {
            return;
        }

        lastDisplayEnabled = enabled;
        STATES.clear();

        if (enabled) {
            CLEANUP_QUEUE.clear();
            CLEANUP_QUEUED.clear();
            Networks.getInstance().getLogger().info("Quantum Storage world item displays enabled.");
        } else {
            queueAllLoadedChunksForCleanup();
            Networks.getInstance().getLogger().info(
                "Quantum Storage world item displays disabled; tagged displays will be removed as chunks are processed.");
        }
    }

    private static void updateDisplay(
        @NotNull Block block,
        @NotNull BlockKey key,
        ItemStack storedItem
    ) {
        if (storedItem == null || storedItem.getType() == Material.AIR || storedItem.getType().isAir()) {
            removeDisplay(block, key);
            return;
        }

        final ItemStack shownItem = storedItem.clone();
        shownItem.setAmount(1);
        final BlockFace face = getDisplayFace(block);
        final DisplayState state = STATES.get(key);

        if (state != null && state.face() == face && state.item().isSimilar(shownItem)) {
            final Entity existing = Bukkit.getServer().getEntity(state.entityId());
            if (existing instanceof ItemDisplay && existing.isValid()) {
                return;
            }
        }

        ItemDisplay display = findExistingDisplay(block, key.ownerKey());
        if (display == null) {
            display = block.getWorld().spawn(getDisplayLocation(block, face), ItemDisplay.class);
        }

        configureDisplay(display, block, key.ownerKey(), face, shownItem);
        STATES.put(
            key,
            new DisplayState(shownItem.clone(), face, display.getUniqueId())
        );
    }

    private static void configureDisplay(
        @NotNull ItemDisplay display,
        @NotNull Block block,
        @NotNull String ownerKey,
        @NotNull BlockFace face,
        @NotNull ItemStack shownItem
    ) {
        final Location target = getDisplayLocation(block, face);
        if (display.getWorld() != target.getWorld()
            || display.getLocation().distanceSquared(target) > 0.0001D) {
            display.teleport(target);
        }

        display.setItemStack(shownItem);
        display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        display.setBillboard(Display.Billboard.CENTER);

        final Transformation transformation = display.getTransformation();
        transformation.getScale().set(DISPLAY_SCALE, DISPLAY_SCALE, DISPLAY_SCALE);
        display.setTransformation(transformation);

        display.setGravity(false);
        display.setInvulnerable(true);
        display.setSilent(true);
        // Runtime visuals are reconstructable from Quantum Storage data. Do not save new display
        // entities into chunk data, which also prevents stale holograms after a clean restart.
        display.setPersistent(false);
        display.setShadowRadius(0.0F);
        display.setShadowStrength(0.0F);
        display.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, ownerKey);
    }

    private static ItemDisplay findExistingDisplay(@NotNull Block block, @NotNull String ownerKey) {
        final Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        ItemDisplay result = null;

        for (Entity entity : block.getWorld().getNearbyEntities(center, 1.25D, 1.25D, 1.25D)) {
            if (!(entity instanceof ItemDisplay display) || !ownerKey.equals(getDisplayOwner(display))) {
                continue;
            }

            if (result == null) {
                result = display;
            } else {
                // Clean up duplicate displays left by a reload or interrupted update.
                display.remove();
            }
        }

        return result;
    }

    private static String getDisplayOwner(@NotNull ItemDisplay display) {
        if (displayKey == null) {
            return null;
        }
        return display.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING);
    }

    private static void cleanupInactiveDisplays(@NotNull Set<BlockKey> activeKeys) {
        final Iterator<Map.Entry<BlockKey, DisplayState>> iterator = STATES.entrySet().iterator();
        while (iterator.hasNext()) {
            final Map.Entry<BlockKey, DisplayState> entry = iterator.next();
            final BlockKey key = entry.getKey();
            if (activeKeys.contains(key)) {
                continue;
            }

            final World world = Bukkit.getWorld(key.worldId());
            if (world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ())) {
                iterator.remove();
                continue;
            }

            final Block block = world.getBlockAt(key.x(), key.y(), key.z());
            if (StorageCacheUtils.getSfItem(block.getLocation()) instanceof NetworkQuantumStorage) {
                // The Slimefun block is present but its cache has not been rebuilt yet.
                continue;
            }

            removeEntity(block, key.ownerKey(), entry.getValue());
            iterator.remove();
        }
    }

    private static void removeDisplay(@NotNull Block block, @NotNull BlockKey key) {
        final DisplayState state = STATES.remove(key);
        removeEntity(block, key.ownerKey(), state);
    }

    private static void removeEntity(
        @NotNull Block block,
        @NotNull String ownerKey,
        DisplayState state
    ) {
        if (state != null) {
            final Entity entity = Bukkit.getServer().getEntity(state.entityId());
            if (entity instanceof ItemDisplay) {
                entity.remove();
            }
        }

        final Location center = block.getLocation().add(0.5D, 0.5D, 0.5D);
        for (Entity entity : block.getWorld().getNearbyEntities(center, 1.25D, 1.25D, 1.25D)) {
            if (entity instanceof ItemDisplay display && ownerKey.equals(getDisplayOwner(display))) {
                display.remove();
            }
        }
    }

    private static BlockFace getDisplayFace(@NotNull Block block) {
        if (block.getBlockData() instanceof Directional directional) {
            return directional.getFacing();
        }

        return BlockFace.SOUTH;
    }

    private static Location getDisplayLocation(@NotNull Block block, @NotNull BlockFace face) {
        final Vector offset = new Vector(face.getModX(), face.getModY(), face.getModZ()).multiply(0.57D);
        return block.getLocation().add(0.5D, 0.5D, 0.5D).add(offset);
    }

    private static void showHoverText() {
        if (!isHoverTextEnabled()) {
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            final RayTraceResult result = player.rayTraceBlocks(5.0D);
            if (result == null || result.getHitBlock() == null) {
                continue;
            }

            final Block block = result.getHitBlock();
            if (!(StorageCacheUtils.getSfItem(block.getLocation()) instanceof NetworkQuantumStorage)
                || !isInsideFrontHoverZone(result, block)) {
                continue;
            }

            final QuantumCache cache = NetworkQuantumStorage.getCaches().get(block.getLocation());
            if (cache == null) {
                continue;
            }

            final ItemStack item;
            final long amount;
            synchronized (cache) {
                final ItemStack cachedItem = cache.getItemStack();
                if (cachedItem == null || cachedItem.getType().isAir()) {
                    continue;
                }
                item = cachedItem.clone();
                amount = cache.getAmountLong();
            }

            final String amountText = String.format(Locale.US, "%,d", amount);
            player.sendActionBar(
                getActualItemName(item)
                    .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                    .append(Component.text(amountText, NamedTextColor.YELLOW))
                    .append(Component.text(" stored", NamedTextColor.GRAY))
            );
        }
    }

    private static boolean isInsideFrontHoverZone(
        @NotNull RayTraceResult result,
        @NotNull Block block
    ) {
        final BlockFace face = getDisplayFace(block);
        if (result.getHitBlockFace() != face) {
            return false;
        }

        final Vector hit = result.getHitPosition();
        final double localX = hit.getX() - block.getX();
        final double localY = hit.getY() - block.getY();
        final double localZ = hit.getZ() - block.getZ();

        return switch (face) {
            case NORTH, SOUTH -> inCenterHalf(localX) && inCenterHalf(localY);
            case EAST, WEST -> inCenterHalf(localZ) && inCenterHalf(localY);
            case UP, DOWN -> inCenterHalf(localX) && inCenterHalf(localZ);
            default -> false;
        };
    }

    private static boolean inCenterHalf(double coordinate) {
        return coordinate >= 0.25D && coordinate <= 0.75D;
    }

    @NotNull
    private static Component getActualItemName(@NotNull ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            final Component customName = meta.customName();
            if (customName != null) {
                return customName;
            }

            if (meta.hasItemName()) {
                return meta.itemName();
            }
        }

        return item.effectiveName();
    }

    private static void queueAllLoadedChunksForCleanup() {
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                queueChunkForCleanup(chunk);
            }
        }
    }

    private static void queueChunkForCleanup(@NotNull Chunk chunk) {
        if (!chunk.isLoaded()) {
            return;
        }

        final ChunkKey key = ChunkKey.from(chunk);
        if (CLEANUP_QUEUED.add(key)) {
            CLEANUP_QUEUE.addLast(chunk);
        }
    }

    private static void processCleanupQueue() {
        final boolean enabled = isDisplayEnabled();
        handleDisplayToggle(enabled);
        if (enabled) {
            return;
        }

        final long deadline = System.nanoTime() + CLEANUP_BUDGET_NANOS;
        int processed = 0;

        while (processed < CLEANUP_CHUNKS_PER_TICK
            && System.nanoTime() < deadline
            && !CLEANUP_QUEUE.isEmpty()) {
            final Chunk chunk = CLEANUP_QUEUE.pollFirst();
            if (chunk == null) {
                break;
            }

            CLEANUP_QUEUED.remove(ChunkKey.from(chunk));
            if (chunk.isLoaded()) {
                cleanupTaggedDisplays(chunk);
            }
            processed++;
        }
    }

    private static void cleanupTaggedDisplays(@NotNull Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof ItemDisplay display && getDisplayOwner(display) != null) {
                display.remove();
            }
        }
    }

    private static boolean isDisplayEnabled() {
        final Networks plugin = Networks.getInstance();
        return plugin != null && plugin.getConfig().getBoolean("quantum-storage.display.enabled", false);
    }

    private static boolean isHoverTextEnabled() {
        final Networks plugin = Networks.getInstance();
        return plugin != null && plugin.getConfig().getBoolean("quantum-storage.display.hover-text", true);
    }

    private static void pruneStateCache() {
        if (!isDisplayEnabled()) {
            STATES.clear();
            return;
        }

        STATES.entrySet().removeIf(entry -> {
            final BlockKey key = entry.getKey();
            final World world = Bukkit.getWorld(key.worldId());
            return world == null || !world.isChunkLoaded(key.chunkX(), key.chunkZ());
        });
    }

    private static final class CleanupListener implements Listener {

        @EventHandler
        public void onChunkLoad(@NotNull ChunkLoadEvent event) {
            if (!isDisplayEnabled()) {
                queueChunkForCleanup(event.getChunk());
            }
        }
    }

    private record BlockKey(UUID worldId, int x, int y, int z) {

        private static BlockKey from(@NotNull Location location) {
            return new BlockKey(
                location.getWorld().getUID(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ()
            );
        }

        private int chunkX() {
            return x >> 4;
        }

        private int chunkZ() {
            return z >> 4;
        }

        @NotNull
        private String ownerKey() {
            return worldId + ":" + x + ":" + y + ":" + z;
        }
    }

    private record ChunkKey(UUID worldId, int x, int z) {

        private static ChunkKey from(@NotNull Chunk chunk) {
            return new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        }
    }

    private record DisplayState(ItemStack item, BlockFace face, UUID entityId) {
    }
}

package networksprobe;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.integrations.infinityexpansion2.InfinityExpansion2Integration;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Disposable two-JVM fixture. Never distributed in either plugin's JAR. */
public final class PairedIE2StorageProbe extends JavaPlugin {
    private static final int TOTAL = 192;
    private static final String OWNER = "paired-storage-owner";
    private SlimefunItem unit;
    private InfinityExpansion2Integration adapter;
    private Method tick;
    private String phase;

    @Override
    public void onEnable() {
        if (!"disposable".equals(System.getProperty("networks.paired.probe"))) {
            getLogger().severe("Refusing to run outside the disposable paired-test harness.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                phase = Files.readString(file("phase.txt")).trim();
                var ie2 = Bukkit.getPluginManager().getPlugin("InfinityExpansion2");
                require(ie2 != null && ie2.isEnabled(), "The actual IE2 plugin must be enabled");
                require(Bukkit.isPrimaryThread(), "Fixture must use the server thread");
                adapter = new InfinityExpansion2Integration(ie2);
                unit = Slimefun.getRegistry().getAllSlimefunItems().stream()
                    .filter(item -> item.getClass().getName().equals(InfinityExpansion2Integration.STORAGE_UNIT_CLASS))
                    .sorted(Comparator.comparing(SlimefunItem::getId)).findFirst().orElseThrow();
                require(adapter.isStorageUnit(unit), "Networks must recognize the registered IE2 storage");
                var ticker = (BlockTicker) unit.getClass().getMethod("getBlockTicker").invoke(unit);
                require(ticker != null && ticker.isSynchronized(), "IE2 storage ticker must be synchronized");
                tick = unit.getClass().getDeclaredMethod("tick", Block.class);
                tick.setAccessible(true);
                location().getWorld().getChunkAt(location()).load();
                if ("seed".equals(phase)) seed();
                else if ("verify".equals(phase)) restore();
                else throw new IllegalStateException("Unknown phase: " + phase);
            } catch (Throwable failure) { fail(failure); }
        }, 40L);
    }

    private Path file(String name) {
        return getDataFolder().toPath().getParent().resolve("paired-ie2-" + name);
    }

    private Location location() { return new Location(Bukkit.getWorlds().getFirst(), 160, 100, 160); }

    private ItemStack sample(int count) {
        var item = new ItemStack(Material.DIAMOND, count);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("networkstest", "paired_owner"), PersistentDataType.STRING, OWNER));
        return item;
    }

    private void seed() throws Exception {
        var controller = Slimefun.getDatabaseManager().getBlockDataController();
        require(StorageCacheUtils.getBlock(location()) == null, "Seed must start without an existing record");
        location().getBlock().setType(unit.getItem().getType());
        var data = controller.createBlock(location(), unit.getId());
        if (StorageCacheUtils.getMenu(location()) == null) controller.loadBlockData(data);
        var menu = StorageCacheUtils.getMenu(location());
        require(menu != null && !menu.hasViewer(), "Create the real IE2 menu without opening its GUI");
        var staging = buffer();
        var root = root();
        long introduced = 0;
        for (int batch = 0; batch < 3; batch++) {
            staging.replaceExistingItem(0, sample(64));
            introduced += 64;
            root.uncontrolAccessInput(staging.getLocation());
            int moved = NetworkTransferUtils.moveMenuSlotIntoNetwork(
                root, staging.getLocation(), staging, 0, 64);
            require(moved == 64 && count(staging.getItemInSlot(0)) == 0, "Seed deposit must debit all 64 source items");
            tick.invoke(unit, location().getBlock());
            require(total(menu) == introduced, "Seed conservation after input/cache/output movement");
        }
        for (int round = 0; round < 24; round++) {
            root.uncontrolAccessOutput(staging.getLocation());
            int moved = NetworkTransferUtils.moveNetworkItemIntoMenu(
                root, staging.getLocation(), staging, sample(1), 17, 0);
            require(moved == 17, "Expected a 17-item withdrawal in round " + round);
            require(total(menu) + checked(staging.getItemInSlot(0)) == TOTAL, "Withdrawal conservation");
            tick.invoke(unit, location().getBlock());
            root.uncontrolAccessInput(staging.getLocation());
            require(NetworkTransferUtils.moveMenuSlotIntoNetwork(
                root, staging.getLocation(), staging, 0, 17) == 17, "Return exactly the owned 17 items");
            tick.invoke(unit, location().getBlock());
            require(total(menu) == TOTAL && count(staging.getItemInSlot(0)) == 0, "Round-trip conservation");
        }
        var wrong = sample(5);
        wrong.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("networkstest", "paired_owner"), PersistentDataType.STRING, "other-owner"));
        staging.replaceExistingItem(0, wrong);
        root.uncontrolAccessInput(staging.getLocation());
        require(NetworkTransferUtils.moveMenuSlotIntoNetwork(
            root, staging.getLocation(), staging, 0, 5) == 0, "Reject a different typed-PDC owner");
        require(wrong.isSimilar(staging.getItemInSlot(0)) && count(staging.getItemInSlot(0)) == 5,
            "Rejected source must remain unchanged");
        require(total(menu) == TOTAL, "Rejection must not alter the stored item count");
        require(cacheAmount() > 0, "Restart must test a nonempty cache, not only its output buffer");
        require(NetworkTransferUtils.getHeldWithdrawalSourceCount() == 0, "Ordinary transfers must not quarantine");
        Files.writeString(file("storage-id.txt"), unit.getId());
        menu.markDirty();
        controller.saveBlockInventoryAsync(data).whenComplete((ignored, error) ->
            Bukkit.getScheduler().runTask(this, () -> {
                if (error != null) { fail(error); return; }
                try {
                    location().getWorld().save();
                    finish("PASS: seed real IE2 storage roundtrips=24 total=" + TOTAL + " no-viewer=true");
                } catch (Throwable failure) { fail(failure); }
            }));
    }

    private void restore() {
        var controller = Slimefun.getDatabaseManager().getBlockDataController();
        controller.getChunkDataAsync(location().getChunk()).whenComplete((chunk, error) ->
            Bukkit.getScheduler().runTask(this, () -> {
                if (error != null) { fail(error); return; }
                new BukkitRunnable() {
                    int attempts;
                    @Override public void run() {
                        try {
                            var data = StorageCacheUtils.getBlock(location());
                            var menu = StorageCacheUtils.getMenu(location());
                            if (data != null && data.isDataLoaded() && menu != null) {
                                cancel(); verify(menu);
                            } else if (++attempts >= 160) {
                                cancel(); throw new AssertionError("Persisted IE2 block/menu did not load");
                            }
                        } catch (Throwable failure) { cancel(); fail(failure); }
                    }
                }.runTaskTimer(PairedIE2StorageProbe.this, 1L, 5L);
            }));
    }

    private void verify(BlockMenu menu) throws Exception {
        require(unit.getId().equals(Files.readString(file("storage-id.txt"))), "Storage item ID survived restart");
        require(location().getBlock().getType() == unit.getItem().getType(), "Physical storage block survived");
        require(!menu.hasViewer(), "Reload must not rely on opening the GUI");
        require(total(menu) == TOTAL, "Restart preserves cache plus physical input/output count");
        var cache = cache();
        var assigned = (ItemStack) cache.getClass().getMethod("getItemStack").invoke(cache);
        require(assigned != null && sample(1).isSimilar(assigned), "Nonempty cache retains its item template after restart");
        var root = root();
        var staging = buffer();
        int delivered = 0;
        for (int batch = 0; batch < 3; batch++) {
            tick.invoke(unit, location().getBlock());
            root.uncontrolAccessOutput(staging.getLocation());
            int moved = NetworkTransferUtils.moveNetworkItemIntoMenu(
                root, staging.getLocation(), staging, sample(1), 64, 0);
            require(moved == 64 && checked(staging.getItemInSlot(0)) == 64, "Saved cache must remain withdrawable");
            delivered += moved;
            staging.replaceExistingItem(0, null); // Accounted delivery to the fixture's external ledger.
            require(total(menu) + delivered == TOTAL, "Conservation while draining restored storage");
        }
        require(delivered == TOTAL && total(menu) == 0, "Exactly 192 items recovered, no extra item");
        finish("PASS: verify independent JVM restored and withdrew exactly " + delivered + " tagged items");
    }

    private NetworkRoot root() {
        return new NetworkRoot(location().clone().add(2, 0, 0), NodeType.CONTROLLER, 100) {
            @Override public Set<BarrelIdentity> getInputAbleBarrels() { return Set.of(barrel()); }
            @Override public Set<BarrelIdentity> getOutputAbleBarrels() { return Set.of(barrel()); }
        };
    }

    private BarrelIdentity barrel() {
        try {
            var barrel = adapter.getBarrel(location(), unit, true);
            require(barrel != null, "Actual IE2 adapter must create a storage identity");
            return barrel;
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
    }

    private Object cache() throws ReflectiveOperationException {
        var caches = (Map<?, ?>) unit.getClass().getMethod("getCaches").invoke(unit);
        require(caches.size() == 1, "The disposable fixture must have exactly one storage cache");
        return caches.values().iterator().next();
    }

    private long cacheAmount() throws ReflectiveOperationException {
        var cache = cache();
        return ((Number) cache.getClass().getMethod("getAmount").invoke(cache)).longValue();
    }

    private long total(BlockMenu menu) throws ReflectiveOperationException {
        long count = cacheAmount();
        for (int slot : adapter.getInputSlots(unit)) count += checked(menu.getItemInSlot(slot));
        for (int slot : adapter.getOutputSlots(unit)) count += checked(menu.getItemInSlot(slot));
        return count;
    }

    private int checked(ItemStack item) {
        int count = count(item);
        if (count > 0) require(sample(1).isSimilar(item), "Physical item metadata must match the original owner");
        return count;
    }

    private static int count(ItemStack item) { return item == null || item.getType().isAir() ? 0 : item.getAmount(); }

    private BlockMenu buffer() {
        var where = location().clone().add(1, 0, 0);
        var preset = new BlockMenuPreset("PAIRED_IE2_BUFFER", "Disposable transfer buffer") {
            @Override public void init() { setSize(9); }
            @Override public boolean canOpen(Block block, Player player) { return false; }
            @Override public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) { return new int[]{0}; }
        };
        return new BlockMenu(preset, where);
    }

    private void fail(Throwable error) { error.printStackTrace(); finish("FAIL: " + phase + " " + error); }

    private void finish(String message) {
        getLogger().info(message);
        try { Files.writeString(file("result.txt"), message + "\n"); }
        catch (Exception failure) { failure.printStackTrace(); }
        finally { Bukkit.shutdown(); }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

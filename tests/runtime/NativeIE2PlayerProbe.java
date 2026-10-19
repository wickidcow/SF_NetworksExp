package networksprobe;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.integrations.infinityexpansion2.InfinityExpansion2Integration;
import io.github.sefiraat.networks.network.barrel.InfinityExpansion2Barrel;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/** Test-only actual-client IE2 menu oracle; never used by the production plugin. */
public final class NativeIE2PlayerProbe extends JavaPlugin implements Listener {
    private static final String NAME = "NetworksProbe";
    private static final String OWNER = "paired-storage-owner";
    private BlockMenu menu;
    private SlimefunItem unit;
    private InfinityExpansion2Integration adapter;
    private Object cache;
    private Player player;
    private int stage;
    private InfinityExpansion2Barrel heldBarrel;
    private boolean finished;
    private ItemStack[] heldInventory;
    private boolean heldVoid;
    private final Map<Integer, Integer> clicks = new HashMap<>();

    private Path file(String name) { return getDataFolder().toPath().getParent().resolve("native-ie2-" + name); }
    private Location location() { return new Location(Bukkit.getWorlds().getFirst(), 160, 100, 160); }

    @Override public void onEnable() {
        if (!"disposable".equals(System.getProperty("networks.native.ie2"))) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        new BukkitRunnable() {
            int attempts;
            @Override public void run() {
                try {
                    location().getChunk().load();
                    var plugin = Bukkit.getPluginManager().getPlugin("InfinityExpansion2");
                    unit = StorageCacheUtils.getSlimefunItem(location());
                    menu = StorageCacheUtils.getMenu(location());
                    if (plugin != null && plugin.isEnabled() && unit != null && menu != null) {
                        adapter = new InfinityExpansion2Integration(plugin);
                        if (adapter.isStorageUnit(unit)) {
                            var caches = (Map<?, ?>) unit.getClass().getMethod("getCaches").invoke(unit);
                            if (caches.size() == 1) {
                                cache = caches.values().iterator().next();
                                require(storageCount() == 0, "Prior native Grid ledger must have drained storage");
                                require(!blocked(), "No initial IE2 transfer quarantine");
                                Files.writeString(file("ready.txt"), "READY: actual IE2 menu client\n");
                                cancel();
                                return;
                            }
                        }
                    }
                    if (++attempts > 120) throw new AssertionError("Real IE2 menu/cache failed to initialize");
                } catch (Throwable error) { cancel(); fail(error); }
            }
        }.runTaskTimer(this, 40L, 5L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!finished) fail(new AssertionError("IE2 player deadline at stage " + stage));
        }, 4000L);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        if (!NAME.equals(joining.getName()) || joining.getAddress() == null
            || !joining.getAddress().getAddress().isLoopbackAddress() || player != null) {
            joining.kickPlayer("Disposable loopback fixture only");
            return;
        }
        player = joining;
        joining.setGameMode(GameMode.SURVIVAL);
        joining.setInvulnerable(true);
        joining.setGravity(false);
        joining.addAttachment(this, "slimefun.inventory.bypass", true);
        joining.teleport(location().add(0.5, 1, 1.5));
    }

    @EventHandler(priority = EventPriority.MONITOR) public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() != player || menu == null
            || event.getView().getTopInventory() != menu.toInventory()) return;
        clicks.merge(event.getRawSlot(), 1, Integer::sum);
        getLogger().info("Actual IE2 click stage=" + stage + " raw=" + event.getRawSlot()
            + " type=" + event.getClick() + " cancelled=" + event.isCancelled());
    }

    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer() != player || !event.getMessage().startsWith("/ie2fixture ")) return;
        event.setCancelled(true);
        String command = event.getMessage().substring("/ie2fixture ".length()).trim();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { check(command); } catch (Throwable error) { fail(error); }
        }, 5L);
    }

    private void check(String command) throws Exception {
        require(!finished && cache != null && player != null, "Fixture must be active");
        if (stage < 12) require(!blocked(), "Healthy IE2 operation must not quarantine its cache");
        switch (command) {
            case "start" -> {
                require(stage == 0 && storageCount() == 0, "Unexpected IE2 seed");
                // The preceding independently verified Grid/crafting ledger is complete.
                player.getInventory().clear();
                player.setItemOnCursor(null);
                menu.replaceExistingItem(adapter.getInputSlots(unit)[0], token(64));
                unit.getBlockTicker().tick(location().getBlock(), unit, StorageCacheUtils.getBlock(location()));
                require(storageCount() == 64 && cacheAmount() == 0, "Seed must be physical output, not hidden credit");
                for (int slot = 9; slot < 13; slot++) player.getInventory().setItem(slot, token(64));
                requireLedger(320, 256);
                menu.open(player);
                stage = 1; signal("deposit-ready");
            }
            case "deposit" -> {
                expect(1, 28, 1); requireLedger(320, 0);
                require(cacheAmount() == 256, "Deposit button must credit the actual 256 input items");
                stage = 2; signal("single-ready");
            }
            case "single" -> {
                expect(2, 34, 1); requireLedger(320, 64);
                stage = 3; signal("all-ready");
            }
            case "all" -> {
                expect(3, 35, 1); requireLedger(320, 256);
                require(cacheAmount() == 0 && storageCount() == 64, "Withdraw All preserves the separate physical output");
                stage = 4; signal("close-ready");
            }
            case "reopen" -> {
                require(stage == 4, "Unexpected reopen"); requireLedger(320, 256);
                menu.open(player);
                stage = 5; signal("partial-prepare");
            }
            case "partial-prepare" -> {
                require(stage == 5, "Main ledger must pass before the next fixture reset"); requireLedger(320, 256);
                getLogger().info("PASS: actual IE2 deposit/single/all/close/reopen, exact 320-item ledger");
                player.getInventory().clear();
                for (int slot = 9; slot < 12; slot++) player.getInventory().setItem(slot, token(64));
                requireLedger(256, 192);
                stage = 6; signal("partial-deposit-ready");
            }
            case "partial-deposit" -> {
                expect(6, 28, 2); requireLedger(256, 0);
                for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Material.BARRIER, 64));
                }
                player.getInventory().setItem(0, token(60));
                requireLedger(316, 60);
                stage = 7; signal("partial-single-ready");
            }
            case "partial-single" -> {
                expect(7, 34, 2); requireLedger(316, 64);
                heldInventory = snapshotInventory();
                stage = 8; signal("full-single-ready");
            }
            case "full-single" -> {
                expect(8, 34, 3); requireLedger(316, 64); requireInventoryUnchanged();
                stage = 9; signal("full-all-ready");
            }
            case "full-all" -> {
                expect(9, 35, 2); requireLedger(316, 64); requireInventoryUnchanged();
                player.getInventory().setItem(1, null); // Remove only an unrelated fixture barrier.
                stage = 10; signal("limited-all-ready");
            }
            case "limited-all" -> {
                expect(10, 35, 3); requireLedger(316, 128);
                getLogger().info("PASS: partial/full inventory ledger conserved 316 tagged items");
                // End that verified ledger before isolating the unrelated-item rejection case.
                player.getInventory().setItem(0, new ItemStack(Material.BARRIER, 64));
                player.getInventory().setItem(1, new ItemStack(Material.BARRIER, 64));
                player.getInventory().setItem(2, otherToken(8));
                heldInventory = snapshotInventory();
                stage = 11; signal("wrong-owner-ready");
            }
            case "wrong-owner" -> {
                expect(11, 28, 3); requireLedger(188, 0); requireInventoryUnchanged();
                heldBarrel = adapter.getBarrel(location(), unit, true);
                require(heldBarrel != null, "Expected real IE2 storage adapter before synthetic hold");
                // Explicit fault injection: this tests player retry behavior under an
                // existing hold, not a claim that a live exploit triggered that hold.
                Field held = cache.getClass().getDeclaredField("transferUnverified");
                held.setAccessible(true); held.setBoolean(cache, true);
                require(blocked(), "Synthetic incident hold was not installed");
                heldVoid = (Boolean) cache.getClass().getMethod("getVoidExcess").invoke(cache);
                stage = 12; signal("held-close-ready");
            }
            case "held-reopen" -> {
                require(stage == 12, "Unexpected held reopen"); requireHeld();
                verifyHeldNetworksBoundary();
                menu.open(player); stage = 13; signal("held-single-ready");
            }
            case "held-single" -> {
                expect(13, 34, 4); requireHeld(); stage = 14; signal("held-all-ready");
            }
            case "held-all" -> {
                expect(14, 35, 4); requireHeld(); stage = 15; signal("held-deposit-ready");
            }
            case "held-deposit" -> {
                expect(15, 28, 4); requireHeld(); stage = 16; signal("held-void-ready");
            }
            case "finish" -> {
                expect(16, 27, 1); requireHeld();
                finished = true;
                String report = "PASS: native-ie2-player deposit-clicks=4 single-clicks=4 all-clicks=4"
                    + " main-total=320 partial-total=316 full-inventory=true metadata=true close-reopen=true"
                    + " blocked-retries=synthetic-hold void-toggle-blocked=true\n";
                Files.writeString(file("result.txt"), report);
                getLogger().info(report.trim()); signal("PASS");
                Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 10L);
            }
            default -> throw new AssertionError("Unknown IE2 fixture command: " + command);
        }
    }

    /**
     * Reproduce the IE2 cache hold at the REAL Networks boundary, using a
     * stale barrel facade captured BEFORE synthetic quarantine.
     * No real exploit is claimed: this is disposable JVM fault injection.
     */
    private void verifyHeldNetworksBoundary() throws Exception {
        final int[] outputs = adapter.getOutputSlots(unit);
        final int[] inputs = adapter.getInputSlots(unit);
        require(outputs.length > 0 && inputs.length > 0, "Missing physical IE2 transport slots");
        final int outputSlot = outputs[0];
        final int inputSlot = inputs[0];
        final ItemStack outputBefore = freeze(menu.getItemInSlot(outputSlot));
        final ItemStack inputBefore = freeze(menu.getItemInSlot(inputSlot));
        require(outputBefore != null && token(1).isSimilar(outputBefore)
                && outputBefore.getAmount() > 0,
            "A real owner-tagged IE2 output buffer must exist at the held boundary");

        final ItemStack withdrawn = heldBarrel.requestItem(new ItemRequest(token(1), 1));
        final boolean outputUnchanged = sameExact(outputBefore, menu.getItemInSlot(outputSlot));

        final ItemStack offer = token(4);
        final ItemStack offeredBefore = freeze(offer);
        heldBarrel.depositItemStack(new ItemStack[] {offer});
        final boolean inputUnchanged = sameExact(inputBefore, menu.getItemInSlot(inputSlot));
        final boolean offerUnchanged = sameExact(offeredBefore, offer);
        final boolean hiddenFromDiscovery = adapter.getBarrel(location(), unit, true) == null;

        require(withdrawn == null && outputUnchanged && inputUnchanged
                && offerUnchanged && hiddenFromDiscovery,
            "IE2 held Networks boundary: output=" + withdrawn
                + " unchangedOutput=" + outputUnchanged
                + " unchangedInput=" + inputUnchanged
                + " unchangedOffer=" + offerUnchanged
                + " hiddenDiscovery=" + hiddenFromDiscovery);
        requireHeld();
    }

    private static ItemStack freeze(ItemStack stack) {
        if (count(stack) == 0) return null;
        final ItemStack detached = stack.clone();
        require(detached != stack && detached.getAmount() == stack.getAmount()
                && detached.isSimilar(stack), "Test snapshot lost actual item identity");
        return detached;
    }

    private static boolean sameExact(ItemStack expected, ItemStack observed) {
        if (count(expected) == 0) return count(observed) == 0;
        return count(observed) > 0 && expected.getAmount() == observed.getAmount()
            && expected.isSimilar(observed);
    }

    private void requireHeld() throws Exception {
        require(blocked(), "Incident hold must survive player retries and GUI reopen");
        requireLedger(188, 0); requireInventoryUnchanged();
        require(heldVoid == (Boolean) cache.getClass().getMethod("getVoidExcess").invoke(cache), "Held void state changed");
    }
    private void expect(int expectedStage, int slot, int count) {
        require(stage == expectedStage && clicks.getOrDefault(slot, 0) == count,
            "Missing/extra actual IE2 click at stage " + stage + ", expected raw=" + slot + " count=" + count);
    }
    private boolean blocked() throws Exception { return (Boolean) cache.getClass().getMethod("hasBlockedTransfer").invoke(cache); }
    private int cacheAmount() throws Exception { return ((Number) cache.getClass().getMethod("getAmount").invoke(cache)).intValue(); }
    private long storageCount() throws Exception {
        long total = cacheAmount();
        require(total >= 0, "Negative cache balance");
        var template = (ItemStack) cache.getClass().getMethod("getItemStack").invoke(cache);
        if (total > 0) require(template != null && token(1).isSimilar(template), "Storage template metadata changed");
        for (int slot : adapter.getInputSlots(unit)) total += owned(menu.getItemInSlot(slot), true);
        for (int slot : adapter.getOutputSlots(unit)) total += owned(menu.getItemInSlot(slot), true);
        return total;
    }
    private int playerCount() {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) total += owned(item, false);
        return total;
    }
    private void requireLedger(int total, int playerAmount) throws Exception {
        require(playerCount() == playerAmount && storageCount() + playerAmount == total
            && count(player.getItemOnCursor()) == 0,
            "IE2 conservation mismatch stage=" + stage + " player=" + playerCount() + " storage=" + storageCount()
                + " cursor=" + player.getItemOnCursor() + " expected-total=" + total + " expected-player=" + playerAmount);
    }
    private ItemStack[] snapshotInventory() {
        return Arrays.stream(player.getInventory().getContents()).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
    }
    private void requireInventoryUnchanged() { require(Arrays.equals(heldInventory, player.getInventory().getContents()), "Unrelated/held inventory changed"); }
    private static int owned(ItemStack item, boolean strict) {
        if (count(item) == 0) return 0;
        if (token(1).isSimilar(item)) return item.getAmount();
        if (strict) throw new AssertionError("Unexpected storage item identity: " + item);
        require(item.getType() == Material.BARRIER || otherToken(1).isSimilar(item), "Unexpected player item identity: " + item);
        return 0;
    }
    private static ItemStack token(int amount) {
        var item = new ItemStack(Material.DIAMOND, amount);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey("networkstest", "paired_owner"),
            PersistentDataType.STRING, OWNER));
        return item;
    }
    private static ItemStack otherToken(int amount) {
        var item = token(amount);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey("networkstest", "paired_owner"),
            PersistentDataType.STRING, "other-owner"));
        return item;
    }
    private static int count(ItemStack item) { return item == null || item.getType().isAir() ? 0 : item.getAmount(); }
    private void signal(String message) { player.sendMessage("NATIVEIE2:" + message); }
    private void fail(Throwable error) {
        if (finished) return;
        finished = true; error.printStackTrace();
        try { Files.writeString(file("result.txt"), "FAIL: native-ie2-player stage=" + stage + " " + error + "\n"); }
        catch (Exception writeFailure) { writeFailure.printStackTrace(); }
        if (player != null) signal("FAIL");
        Bukkit.shutdown();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

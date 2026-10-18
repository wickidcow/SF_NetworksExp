package networksprobe;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.events.NetworkCraftEvent;
import io.github.sefiraat.networks.integrations.infinityexpansion2.InfinityExpansion2Integration;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.slimefun.network.grid.NetworkCraftingGrid;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
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

/** Test-only server oracle driven by an actual loopback Minecraft client. */
public final class NativeGridPlayerProbe extends JavaPlugin implements Listener {
    private static final String NAME = "NetworksProbe";
    private static final int[] INPUTS = {6, 7, 8, 15, 16, 17, 24, 25, 26};
    private NetworkCraftingGrid grid;
    private SlimefunItem storageItem;
    private InfinityExpansion2Integration adapter;
    private BlockMenu menu;
    private Player player;
    private int stage;
    private int clicks;
    private int shifts;
    private int craftEvents;
    private int eventBaseline;
    private boolean cancelCraft;
    private boolean reenterCraft;
    private boolean finished;

    private Path file(String name) { return getDataFolder().toPath().getParent().resolve("native-grid-" + name); }
    private Location storageLocation() { return new Location(Bukkit.getWorlds().getFirst(), 160, 100, 160); }
    private Location controllerLocation() { return storageLocation().add(1, 0, 1); }
    private Location gridLocation() { return storageLocation().add(2, 0, 1); }

    @Override public void onEnable() {
        if (!"disposable".equals(System.getProperty("networks.native.player"))) {
            getLogger().severe("Refusing to enable the test oracle outside its disposable harness.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                require(Bukkit.isPrimaryThread(), "Setup must use the server thread");
                grid = (NetworkCraftingGrid) Slimefun.getRegistry().getAllSlimefunItems().stream()
                    .filter(item -> item.getClass() == NetworkCraftingGrid.class).findFirst().orElseThrow();
                var ie2 = Bukkit.getPluginManager().getPlugin("InfinityExpansion2");
                require(ie2 != null && ie2.isEnabled(), "Real IE2 must be enabled");
                adapter = new InfinityExpansion2Integration(ie2);
                gridLocation().getChunk().load();
                var db = Slimefun.getDatabaseManager().getBlockDataController();
                require(StorageCacheUtils.getBlock(gridLocation()) == null, "New grid must have no previous record");
                gridLocation().getBlock().setType(grid.getItem().getType());
                var data = db.createBlock(gridLocation(), grid.getId());
                if (!data.isDataLoaded()) db.loadBlockData(data);
                awaitNetwork();
            } catch (Throwable failure) { fail(failure); }
        }, 40L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!finished) fail(new AssertionError("Native player scenario exceeded its deadline at stage " + stage));
        }, 4000L);
    }

    private void awaitNetwork() {
        new BukkitRunnable() {
            int attempts;
            @Override public void run() {
                try {
                    menu = StorageCacheUtils.getMenu(gridLocation());
                    var data = StorageCacheUtils.getBlock(gridLocation());
                    if (menu != null && data != null && data.isDataLoaded()) {
                        grid.getBlockTicker().tick(gridLocation().getBlock(), grid, data);
                        var controller = StorageCacheUtils.getSlimefunItem(controllerLocation());
                        if (controller instanceof NetworkController) {
                            var cd = StorageCacheUtils.getBlock(controllerLocation());
                            controller.getBlockTicker().tick(controllerLocation().getBlock(), controller, cd);
                            var definition = NetworkStorage.getNode(gridLocation());
                            if (definition != null && definition.getNode() != null) {
                                storageItem = StorageCacheUtils.getSlimefunItem(storageLocation());
                                require(adapter.isStorageUnit(storageItem), "Discovered real IE2 storage is required");
                                require(storageCount() == 0, "Prior paired ledger must have been fully drained");
                                require(!NetworkTransferUtils.isNetworkRootHeld(root(), gridLocation()), "No initial quarantine");
                                Files.writeString(file("ready.txt"), "READY: actual player may connect\n");
                                cancel();
                                return;
                            }
                        }
                    }
                    if (++attempts > 120) throw new AssertionError("Native crafting grid topology did not initialize");
                } catch (Throwable failure) { cancel(); fail(failure); }
            }
        }.runTaskTimer(this, 1L, 5L);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        if (!NAME.equals(joining.getName()) || joining.getAddress() == null
            || !joining.getAddress().getAddress().isLoopbackAddress() || player != null) {
            joining.kickPlayer("Disposable loopback test only");
            return;
        }
        player = joining;
        joining.setGameMode(GameMode.SURVIVAL);
        joining.setInvulnerable(true);
        joining.setGravity(false);
        joining.addAttachment(this, "slimefun.inventory.bypass", true);
        joining.teleport(gridLocation().add(0.5, 1, 1.5));
    }

    @EventHandler(priority = EventPriority.MONITOR) public void onClick(InventoryClickEvent event) {
        if (event.getWhoClicked() != player || menu == null) return;
        if (event.getView().getTopInventory() != menu.toInventory()) return;
        clicks++;
        if (event.isShiftClick()) shifts++;
        getLogger().info("Native client click raw=" + event.getRawSlot() + " type=" + event.getClick()
            + " cancelled=" + event.isCancelled());
    }

    @EventHandler public void onCraft(NetworkCraftEvent event) {
        if (event.getPlayer() != player || event.getMachine() != grid) return;
        craftEvents++;
        if (cancelCraft) event.setCancelled(true);
        if (reenterCraft) {
            reenterCraft = false;
            try {
                Method craft = NetworkCraftingGrid.class.getDeclaredMethod("tryCraft", BlockMenu.class, Player.class);
                craft.setAccessible(true);
                craft.invoke(grid, menu, player);
            } catch (Throwable failure) { fail(failure); }
        }
    }

    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer() != player || !event.getMessage().startsWith("/networksfixture ")) return;
        event.setCancelled(true);
        String command = event.getMessage().substring("/networksfixture ".length()).trim();
        // Let normal click application and inventory packets settle before reading the ledger.
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { check(command); } catch (Throwable failure) { fail(failure); }
        }, 5L);
    }

    private void check(String command) throws Exception {
        require(!finished && menu != null && player != null, "Fixture must be ready");
        require(!NetworkTransferUtils.isNetworkRootHeld(root(), gridLocation()), "Healthy player operation quarantined");
        switch (command) {
            case "start" -> {
                require(stage == 0, "Unexpected start");
                player.getInventory().clear();
                player.setItemOnCursor(null);
                player.getInventory().setItem(9, token(16));
                menu.open(player);
                stage = 1;
                signal("shift-ready");
            }
            case "check-shift" -> {
                require(stage == 1 && shifts >= 1, "A real shift-click must have occurred");
                require(storageCount() == 16 && playerTokens() == 0 && count(player.getItemOnCursor()) == 0,
                    "Shift-click must move exactly 16 owned items to storage; storage=" + storageCount()
                        + " inventory=" + playerTokens() + " cursor=" + count(player.getItemOnCursor()));
                stage = 2;
                signal("withdraw-ready");
            }
            case "check-cursor" -> {
                require(stage == 2 && clicks >= 2, "Real display click required");
                require(storageCount() == 0 && playerTokens() == 0 && checkedToken(player.getItemOnCursor()) == 16,
                    "Display withdrawal must yield exactly 16 on the real cursor");
                stage = 3;
                signal("close-ready");
            }
            case "check-close" -> {
                require(stage == 3, "Unexpected close check");
                require(count(player.getItemOnCursor()) == 0 && playerTokens() == 16 && storageCount() == 0,
                    "Closing the actual window must conserve the 16 cursor items");
                stage = 4;
                signal("transfer-pass");
            }
            case "prepare-craft" -> {
                require(stage == 4, "Transfer ledger must pass before resetting fixture inputs");
                getLogger().info("PASS: native shift deposit, display withdrawal and close: exactly 16 tagged items");
                player.getInventory().clear(); // Ends the independently checked transfer ledger.
                prepareRecipe();
                menu.open(player);
                stage = 5;
                signal("craft-ready");
            }
            case "check-craft" -> {
                require(stage == 5 && craftEvents == eventBaseline + 1, "Exactly one native craft event expected");
                requireRecipe(1, 4);
                stage = 6;
                signal("second-craft-ready");
            }
            case "check-second" -> {
                require(stage == 6 && craftEvents == eventBaseline + 2, "Exactly two craft events expected");
                requireRecipe(0, 8);
                stage = 7;
                signal("craft-pass");
            }
            case "prepare-cancel" -> {
                require(stage == 7, "Craft ledger must pass before resetting the next case");
                getLogger().info("PASS: two actual craft-button clicks consumed four planks and made eight sticks");
                prepareRecipe();
                cancelCraft = true;
                stage = 8;
                signal("cancel-ready");
            }
            case "check-cancel" -> {
                require(stage == 8 && craftEvents == eventBaseline + 1, "Cancelled craft event missing");
                requireRecipe(2, 0);
                cancelCraft = false;
                reenterCraft = true;
                eventBaseline = craftEvents;
                stage = 9;
                signal("reentry-ready");
            }
            case "check-reentry" -> {
                require(stage == 9 && craftEvents == eventBaseline + 1, "Recursive callback must not craft twice");
                requireRecipe(1, 4);
                stage = 10;
                signal("reopen-ready");
            }
            case "reopen" -> {
                require(stage == 10, "Unexpected reopen");
                requireRecipe(1, 4);
                menu.open(player);
                stage = 11;
                signal("output-ready");
            }
            case "finish" -> {
                require(stage == 11 && shifts >= 2, "Native output shift-click required");
                requireRecipe(1, 0);
                int sticks = 0;
                for (ItemStack item : player.getInventory().getStorageContents()) {
                    if (item != null && item.getType() == Material.STICK) sticks += item.getAmount();
                }
                require(sticks == 4 && count(player.getItemOnCursor()) == 0 && storageCount() == 0,
                    "Output shift and reopen must not duplicate crafted items");
                require(clicks >= 7, "At least seven actual inventory click events required");
                finished = true;
                String report = "PASS: native-player clicks=" + clicks + " shifts=" + shifts
                    + " craft-events=" + craftEvents + " transfer-total=16 ordinary-crafts=2"
                    + " cancelled-craft=true reentry-blocked=true close-reopen=true\n";
                Files.writeString(file("result.txt"), report);
                getLogger().info(report.trim());
                player.sendMessage("NATIVEGRID:PASS");
                Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 10L);
            }
            default -> throw new AssertionError("Unknown native fixture command: " + command);
        }
    }

    private void prepareRecipe() {
        for (int slot : INPUTS) menu.replaceExistingItem(slot, null);
        menu.replaceExistingItem(43, null);
        menu.replaceExistingItem(6, new ItemStack(Material.OAK_PLANKS, 2));
        menu.replaceExistingItem(15, new ItemStack(Material.OAK_PLANKS, 2));
        menu.markDirty();
        eventBaseline = craftEvents;
    }
    private void requireRecipe(int planksPerSlot, int sticks) {
        for (int slot : INPUTS) {
            ItemStack actual = menu.getItemInSlot(slot);
            int expected = (slot == 6 || slot == 15) ? planksPerSlot : 0;
            require(count(actual) == expected && (expected == 0 || actual.getType() == Material.OAK_PLANKS),
                "Unexpected ingredient slot " + slot + ": " + actual);
        }
        ItemStack output = menu.getItemInSlot(43);
        require(count(output) == sticks && (sticks == 0 || output.getType() == Material.STICK),
            "Expected " + sticks + " crafting output, got " + output);
    }
    private NetworkRoot root() {
        var node = NetworkStorage.getNode(gridLocation());
        require(node != null && node.getNode() != null && node.getNode().getRoot() != null, "Grid must have a real root");
        return node.getNode().getRoot();
    }
    private long storageCount() throws Exception {
        var caches = (Map<?, ?>) storageItem.getClass().getMethod("getCaches").invoke(storageItem);
        require(caches.size() == 1, "One real IE2 storage cache expected");
        Object cache = caches.values().iterator().next();
        long sum = ((Number) cache.getClass().getMethod("getAmount").invoke(cache)).longValue();
        ItemStack template = (ItemStack) cache.getClass().getMethod("getItemStack").invoke(cache);
        if (sum > 0) require(template != null && token(1).isSimilar(template), "Cache owner identity changed");
        BlockMenu storage = StorageCacheUtils.getMenu(storageLocation());
        require(storage != null, "Real storage menu unavailable");
        for (int slot : adapter.getInputSlots(storageItem)) sum += checkedToken(storage.getItemInSlot(slot));
        for (int slot : adapter.getOutputSlots(storageItem)) sum += checkedToken(storage.getItemInSlot(slot));
        return sum;
    }
    private int playerTokens() {
        int total = 0;
        for (ItemStack item : player.getInventory().getStorageContents()) total += checkedToken(item);
        return total;
    }
    private static ItemStack token(int amount) {
        ItemStack item = new ItemStack(Material.DIAMOND, amount);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey("networkstest", "paired_owner"),
            PersistentDataType.STRING, "paired-storage-owner"));
        return item;
    }
    private static int checkedToken(ItemStack item) {
        int amount = count(item);
        if (amount > 0) require(token(1).isSimilar(item), "Unexpected item identity in token ledger: " + item);
        return amount;
    }
    private static int count(ItemStack item) { return item == null || item.getType().isAir() ? 0 : item.getAmount(); }
    private void signal(String state) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!finished) player.sendMessage("NATIVEGRID:" + state);
        }, 10L);
    }
    private void fail(Throwable failure) {
        if (finished) return;
        finished = true;
        failure.printStackTrace();
        try { Files.writeString(file("result.txt"), "FAIL: native-player stage=" + stage + " " + failure + "\n"); }
        catch (Exception secondary) { secondary.printStackTrace(); }
        if (player != null) player.sendMessage("NATIVEGRID:FAIL " + failure);
        Bukkit.shutdown();
    }
    private static void require(boolean test, String message) { if (!test) throw new AssertionError(message); }
}

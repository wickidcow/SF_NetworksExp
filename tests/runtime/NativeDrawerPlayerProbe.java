package networksprobe;

import com.balugaq.netex.api.data.ItemContainer;
import com.balugaq.netex.api.data.StorageUnitData;
import com.balugaq.netex.api.enums.StorageUnitType;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.implementation.ExpansionItems;
import com.ytdd9527.networksexpansion.implementation.machines.unit.NetworksDrawer;
import com.ytdd9527.networksexpansion.utils.databases.DataStorage;
import com.ytdd9527.networksexpansion.utils.itemstacks.ItemStackUtil;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.sefiraat.networks.utils.Keys;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentQuantumStorageType;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/** Actual Drawer GUI and Quantum clicks in a disposable world, followed by independent-JVM reload. */
public final class NativeDrawerPlayerProbe extends JavaPlugin implements Listener {
    private static final long LARGE = 3_000_000_000L;
    private static final long LIMIT = 4_000_000_000L;
    private static final long TOTAL = LARGE + 86;
    private static final NamespacedKey TOKEN = new NamespacedKey("networkstest", "drawer_token");
    private static final NamespacedKey CONTAINER = new NamespacedKey("networkstest", "drawer_container");
    private NetworksDrawer drawerItem;
    private NetworkQuantumStorage quantumItem;
    private StorageUnitData drawer;
    private BlockMenu menu;
    private Player player;
    private boolean verify;
    private boolean finished;
    private int stage;
    private int clickCount;
    private int checkedClicks;
    private int lastRaw;
    private String lastClick;

    private Path file(String name) { return getDataFolder().toPath().getParent().resolve("native-drawer-" + name); }
    private Location location() { return new Location(Bukkit.getWorlds().getFirst(), 224, 100, 224); }

    @Override public void onEnable() {
        if (!"disposable".equals(System.getProperty("networks.native.drawer"))) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        try {
            String phase = Files.readString(file("phase.txt")).trim();
            require(phase.equals("seed") || phase.equals("verify"), "Unknown Drawer fixture phase");
            verify = phase.equals("verify");
        } catch (Throwable error) { fail(error); return; }
        Bukkit.getPluginManager().registerEvents(this, this);
        new BukkitRunnable() {
            int attempts;
            boolean created;
            @Override public void run() {
                try {
                    if (!created) {
                        drawerItem = ExpansionItems.CARGO_STORAGE_UNIT_1;
                        quantumItem = Slimefun.getRegistry().getAllSlimefunItems().stream()
                            .filter(NetworkQuantumStorage.class::isInstance).map(NetworkQuantumStorage.class::cast)
                            .max(Comparator.comparingLong(NetworkQuantumStorage::getMaxAmount)).orElseThrow();
                        location().getChunk().load();
                        if (!verify) create(location(), drawerItem);
                        created = true;
                    }
                    var block = StorageCacheUtils.getBlock(location());
                    menu = StorageCacheUtils.getMenu(location());
                    if (block == null || !block.isDataLoaded() || menu == null) {
                        require(++attempts < 150, "Drawer block/menu readiness timed out");
                        return;
                    }
                    require(block.getSfId().equals(drawerItem.getId()), "Physical Drawer identity mismatch");
                    if (drawer == null && !verify) {
                        UUID owner = UUID.nameUUIDFromBytes("OfflinePlayer:DrawerProbe".getBytes(StandardCharsets.UTF_8));
                        drawer = DataStorage.createStorageUnitData(Bukkit.getOfflinePlayer(owner), StorageUnitType.TINY, location());
                        NetworksDrawer.addBlockInfo(location(), drawer.getId(), false, false);
                        NetworksDrawer.requestData(location(), drawer.getId());
                        ItemStack seed = token(13);
                        drawer.depositItemStack0(location(), seed, false);
                        require(seed.getAmount() == 0 && drawer.getTotalAmountLong() == 13, "Drawer seed was not accepted exactly");
                        menu.replaceExistingItem(9, quantum("large", LARGE));
                        menu.replaceExistingItem(27, token(1));
                    } else if (verify) {
                        drawer = NetworksDrawer.getStorageData(location());
                        if (drawer == null) {
                            require(++attempts < 150, "Production Drawer data reload timed out");
                            return;
                        }
                    }
                    require(drawer == NetworksDrawer.getStorageData(location()), "Drawer is not the published canonical instance");
                    require(drawer.getSizeType() == StorageUnitType.TINY, "Wrong fixture Drawer size");
                    require(!menu.hasViewer(), "Readiness must precede client GUI actions");
                    NetworksDrawer.update(location(), true);
                    Files.writeString(file("ready.txt"), "READY: native drawer " + (verify ? "verify" : "seed") + "\n");
                    cancel();
                } catch (Throwable error) { cancel(); fail(error); }
            }
        }.runTaskTimer(this, 40L, 5L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!finished) fail(new AssertionError("Drawer fixture deadline stage=" + stage));
        }, 5000L);
    }

    private static void create(Location location, SlimefunItem item) {
        require(Bukkit.isPrimaryThread(), "Fixture creation must run on the server thread");
        require(StorageCacheUtils.getBlock(location) == null, "Fixture cannot replace an existing record");
        location.getBlock().setType(item.getItem().getType());
        var database = Slimefun.getDatabaseManager().getBlockDataController();
        var data = database.createBlock(location, item.getId());
        if (!data.isDataLoaded()) database.loadBlockData(data);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        if (!"DrawerProbe".equals(joining.getName()) || joining.getAddress() == null
            || !joining.getAddress().getAddress().isLoopbackAddress() || player != null) {
            joining.kickPlayer("Disposable loopback Drawer fixture only");
            return;
        }
        player = joining;
        joining.setGameMode(GameMode.SURVIVAL);
        joining.setInvulnerable(true);
        joining.setGravity(false);
        joining.addAttachment(this, "slimefun.inventory.bypass", true);
        joining.teleport(location().add(0.5, 1, 1.5));
        if (!verify) {
            require(playerOwned() == 0, "Fresh fixture player already owns tagged stock");
            joining.getInventory().clear();
            for (int i = 0; i < 36; i++) joining.getInventory().setItem(i, new ItemStack(Material.STONE, 64));
            joining.getInventory().setItem(0, token(59));
            joining.getInventory().setItem(35, quantum("small", 13));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR) public void onClick(InventoryClickEvent event) {
        if (finished || event.getWhoClicked() != player || menu == null
            || event.getView().getTopInventory() != menu.toInventory()) return;
        clickCount++;
        lastRaw = event.getRawSlot();
        lastClick = event.getClick().name();
        getLogger().info("Actual Drawer click stage=" + stage + " raw=" + lastRaw
            + " click=" + lastClick + " cancelled=" + event.isCancelled());
    }

    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer() != player || !event.getMessage().startsWith("/drawerfixture ")) return;
        event.setCancelled(true);
        String command = event.getMessage().substring("/drawerfixture ".length()).trim();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { check(command); } catch (Throwable error) { fail(error); }
        }, 5L);
    }

    private void check(String command) throws Exception {
        require(!finished && player != null && menu != null, "Drawer fixture must be active");
        requireLedger();
        switch (command) {
            case "start" -> {
                require(stage == 0, "Unexpected start");
                if (verify) {
                    require(drawer.getTotalAmountLong() == 13 && playerTokens() == 0, "Drawer/player stock failed restart");
                    requireQuantum(menu.getItemInSlot(9), "small", 0);
                    requireQuantum(player.getInventory().getItem(9), "large", LARGE + 72);
                    require(token(1).equals(menu.getItemInSlot(27)), "Chosen identity failed restart");
                    require(NetworksDrawer.isLocked(location()) && NetworksDrawer.isVoidExcess(location()), "Drawer toggles failed restart");
                    require("FROM_QUANTUM".equals(StorageCacheUtils.getData(location(), "quickTransferMode")), "Quantum mode failed restart");
                    stage = 30; menu.open(player); signal("verify-withdraw");
                } else {
                    require(drawer.getTotalAmountLong() == 13 && playerTokens() == 59, "Fresh Drawer/player seed differs");
                    requireQuantum(menu.getItemInSlot(9), "large", LARGE);
                    requireQuantum(player.getInventory().getItem(35), "small", 13);
                    stage = 1; menu.open(player); signal("lock");
                }
            }
            case "lock" -> { expect(1, 8, "LEFT"); require(NetworksDrawer.isLocked(location()), "Actual lock toggle failed"); next("void"); }
            case "void" -> { expect(2, 7, "LEFT"); require(NetworksDrawer.isVoidExcess(location()), "Actual Void Excess toggle failed"); next("partial"); }
            case "partial" -> {
                expect(3, 10, "RIGHT"); require(drawer.getTotalAmountLong() == 8 && playerTokens() == 64,
                    "Partial five-item inventory acceptance lost the eight-item Drawer remainder"); next("full");
            }
            case "full" -> {
                expect(4, 10, "RIGHT"); require(drawer.getTotalAmountLong() == 8 && playerTokens() == 64,
                    "Full inventory consumed Drawer stock"); next("bulk");
            }
            case "bulk" -> {
                expect(5, 10, "SHIFT_RIGHT"); require(drawer.getTotalAmountLong() == 72 && playerTokens() == 0,
                    "Actual inventory deposit did not move exactly 64"); next("cursor-pickup");
            }
            case "cursor-pickup" -> { expect(6, 27, "LEFT"); require(token(1).equals(player.getItemOnCursor()), "Chosen token not on cursor"); next("cursor-deposit"); }
            case "cursor-deposit" -> {
                expect(7, 11, "LEFT"); require(drawer.getTotalAmountLong() == 73 && plainCount(player.getItemOnCursor()) == 0,
                    "Actual cursor deposit did not move one token"); next("choose-withdraw");
            }
            case "choose-withdraw" -> { expect(8, 10, "LEFT"); require(drawer.getTotalAmountLong() == 72 && playerTokens() == 1, "Single withdrawal failed"); next("choose-pickup"); }
            case "choose-pickup" -> { expect(9, 81, "LEFT"); require(token(1).equals(player.getItemOnCursor()), "Player token not picked up"); next("choose-place"); }
            case "choose-place" -> { expect(10, 27, "LEFT"); require(token(1).equals(menu.getItemInSlot(27)), "Chosen template changed"); next("quantum-from"); }
            case "quantum-from" -> {
                expect(11, 18, "LEFT"); require(drawer.getTotalAmountLong() == 256, "Quantum source did not fill exactly live capacity");
                requireQuantum(menu.getItemInSlot(9), "large", LARGE - 184); next("quantum-toggle");
            }
            case "quantum-toggle" -> { expect(12, 18, "RIGHT"); require("TO_QUANTUM".equals(StorageCacheUtils.getData(location(), "quickTransferMode")), "TO mode not persisted"); next("quantum-to"); }
            case "quantum-to" -> {
                expect(13, 18, "LEFT"); require(drawer.getTotalAmountLong() == 0, "Drawer source debit differs");
                requireQuantum(menu.getItemInSlot(9), "large", LARGE + 72);
                // Remove only untagged capacity fillers between scenarios. No tracked
                // item, Quantum state, cursor or Drawer balance is modified here.
                for (int i = 0; i < 36; i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    if (item != null && item.getType() == Material.STONE) {
                        require(owned(item) == 0, "Tagged filler must never be removed");
                        player.getInventory().setItem(i, null);
                    }
                }
                player.updateInventory(); next("big-pickup");
            }
            case "big-pickup" -> { expect(14, 9, "LEFT"); requireQuantum(player.getItemOnCursor(), "large", LARGE + 72); next("big-store"); }
            case "big-store" -> { expect(15, 54, "LEFT"); requireQuantum(player.getInventory().getItem(9), "large", LARGE + 72); next("small-pickup"); }
            case "small-pickup" -> { expect(16, 80, "LEFT"); requireQuantum(player.getItemOnCursor(), "small", 13); next("small-place"); }
            case "small-place" -> { expect(17, 9, "LEFT"); requireQuantum(menu.getItemInSlot(9), "small", 13); next("small-toggle"); }
            case "small-toggle" -> { expect(18, 18, "RIGHT"); require("FROM_QUANTUM".equals(StorageCacheUtils.getData(location(), "quickTransferMode")), "FROM mode not persisted"); next("small-drain"); }
            case "small-drain" -> {
                expect(19, 18, "LEFT"); require(drawer.getTotalAmountLong() == 13, "Complete Quantum debit lost stock");
                requireQuantum(menu.getItemInSlot(9), "small", 0); finish("seed");
            }
            case "verify-withdraw" -> { expect(30, 10, "LEFT"); require(drawer.getTotalAmountLong() == 12 && playerTokens() == 1, "Reloaded GUI withdrawal failed"); next("verify-deposit"); }
            case "verify-deposit" -> { expect(31, 10, "SHIFT_RIGHT"); require(drawer.getTotalAmountLong() == 13 && playerTokens() == 0, "Reloaded GUI deposit failed"); finish("verify"); }
            default -> throw new AssertionError("Unknown Drawer command " + command);
        }
        requireLedger();
    }

    private void next(String signal) { stage++; signal(signal); }
    private void expect(int expectedStage, int raw, String click) {
        require(stage == expectedStage, "Wrong stage " + stage + " expected " + expectedStage);
        require(clickCount == checkedClicks + 1 && lastRaw == raw && click.equals(lastClick),
            "Expected one actual " + click + " at " + raw + "; count=" + clickCount + " raw=" + lastRaw + " click=" + lastClick);
        checkedClicks = clickCount;
    }
    private void requireLedger() {
        long total = drawer.getTotalAmountLong() + playerOwned() + owned(player.getItemOnCursor())
            + owned(menu.getItemInSlot(9)) + owned(menu.getItemInSlot(27));
        for (ItemContainer entry : drawer.copyStoredItems()) {
            require(token(1).equals(entry.getSample()), "Drawer canonical PDC identity changed");
        }
        require(!drawer.hasUnverifiedSourceTransfer(), "Drawer entered an unverified receipt hold");
        require(total == TOTAL, "Exact native Drawer ledger changed from " + TOTAL + " to " + total);
        for (Item dropped : location().getWorld().getEntitiesByClass(Item.class)) {
            require(owned(dropped.getItemStack()) == 0, "Tracked stock was unexpectedly world-dropped");
        }
    }
    private long playerOwned() { long total = 0; for (ItemStack item : player.getInventory().getContents()) total += owned(item); return total; }
    private int playerTokens() { int total = 0; for (ItemStack item : player.getInventory().getStorageContents()) if (isToken(item)) total += item.getAmount(); return total; }
    private static long owned(ItemStack item) {
        if (isToken(item)) return item.getAmount();
        if (item == null || item.getType().isAir()) return 0;
        String container = item.getItemMeta().getPersistentDataContainer().get(CONTAINER, PersistentDataType.STRING);
        if (container == null) return 0;
        require(container.equals("large") || container.equals("small"), "Unknown container identity");
        QuantumCache cache = Keys.getQuantumCache(item.getItemMeta());
        require(cache != null && item.getAmount() == 1 && token(1).equals(cache.getItemStack()), "Quantum template or stack amount changed");
        require(cache.getLimitLong() == LIMIT && cache.isVoidExcess() && cache.supportsCustomMaxAmount(), "Quantum capacity/flags changed");
        return cache.getAmountLong();
    }
    private static void requireQuantum(ItemStack item, String identity, long amount) {
        require(item != null && identity.equals(item.getItemMeta().getPersistentDataContainer().get(CONTAINER, PersistentDataType.STRING)), "Quantum container PDC changed");
        require(owned(item) == amount, "Quantum " + identity + " balance differs from " + amount);
    }
    private ItemStack quantum(String identity, long amount) {
        ItemStack item = ItemStackUtil.getCleanItem(quantumItem.getItem());
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(CONTAINER, PersistentDataType.STRING, identity);
        QuantumCache cache = new QuantumCache(token(1), amount, LIMIT, true, true);
        DataTypeMethods.setCustom(meta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE, cache);
        cache.updateMetaLore(meta); item.setItemMeta(meta); return item;
    }
    private static ItemStack token(int amount) {
        ItemStack item = new ItemStack(Material.DIAMOND, amount);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Native Drawer exact identity"));
            meta.lore(List.of(Component.text("Preserve the original item PDC and lore")));
            meta.getPersistentDataContainer().set(TOKEN, PersistentDataType.STRING, "drawer-owner-A");
        });
        return item;
    }
    private static boolean isToken(ItemStack item) { return plainCount(item) > 0 && token(1).isSimilar(item); }
    private static int plainCount(ItemStack item) { return item == null || item.getType().isAir() ? 0 : item.getAmount(); }
    private void signal(String message) { player.sendMessage("NATIVEDRAWER:" + message); }
    private void finish(String phase) throws Exception {
        requireLedger(); require(plainCount(player.getItemOnCursor()) == 0, "Final cursor is not empty");
        finished = true;
        String report = "PASS: native-drawer-" + phase + " total=" + TOTAL + " drawer=13 large=" + (LARGE + 72)
            + " small=0 chosen=1 exact-pdc=true long-balances=true locked=true void=true actual-clicks=" + clickCount
            + (verify ? " independent-jvm=true" : " partial-and-full-inventory=true cursor-and-bulk-deposit=true quantum-bidirectional=true") + "\n";
        Files.writeString(file("result.txt"), report); getLogger().info(report.trim()); signal("PASS");
        player.closeInventory(); player.saveData();
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 10L);
    }
    private void fail(Throwable error) {
        if (finished) return;
        finished = true; error.printStackTrace();
        try { Files.writeString(file("result.txt"), "FAIL: native-drawer stage=" + stage + " " + error + "\n"); }
        catch (Exception failure) { failure.printStackTrace(); }
        if (player != null) signal("FAIL");
        Bukkit.shutdown();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

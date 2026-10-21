package networksprobe;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.slimefun.network.pusher.AbstractNetworkPusher;
import io.github.sefiraat.networks.slimefun.network.pusher.NetworkBestPusher;
import io.github.sefiraat.networks.slimefun.network.pusher.NetworkMorePusher;
import io.github.sefiraat.networks.slimefun.network.pusher.NetworkPusher;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;

/** Actual client GUI/restart oracle. Fixture data exists only in a new disposable world. */
public final class NativeRecipePusherProbe extends JavaPlugin implements Listener {
    private static final String MODE_KEY = "recipe-aware";
    private static final String BATCH_KEY = "recipe-aware-buffer-batches";
    private static final int FOREIGN_AMOUNT = 13;
    private static final int TOTAL = FOREIGN_AMOUNT * 3;
    private static final Class<?>[] TYPES = {NetworkPusher.class, NetworkMorePusher.class, NetworkBestPusher.class};
    private final AbstractNetworkPusher[] pushers = new AbstractNetworkPusher[3];
    private final BlockMenu[] menus = new BlockMenu[3];
    private final NativeRecipeTransferProbe[] transfers = new NativeRecipeTransferProbe[3];
    private Player player;
    private boolean verify;
    private boolean finished;
    private int tier;
    private int stage;
    private int clickCount;
    private int acceptedClicks;
    private int lastRaw;
    private String lastClick;
    private int breakEvents;
    private boolean lastBreakCancelled;

    private Path file(String name) { return getDataFolder().toPath().getParent().resolve("native-recipe-" + name); }
    private Location location(int index) { return new Location(Bukkit.getWorlds().getFirst(), 176 + 8 * index, 100, 176); }
    private BlockMenu menu() { return menus[tier]; }
    private int slot() { return pushers[tier].getOtherBackgroundSlots()[0]; }

    @Override public void onEnable() {
        if (!"disposable".equals(System.getProperty("networks.native.recipe"))) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        try {
            String phase = Files.readString(file("phase.txt")).trim();
            require(phase.equals("seed") || phase.equals("verify"), "Unknown recipe fixture phase");
            verify = phase.equals("verify");
        } catch (Throwable error) { fail(error); return; }
        Bukkit.getPluginManager().registerEvents(this, this);
        new BukkitRunnable() {
            int attempts;
            boolean created;
            @Override public void run() {
                try {
                    if (!created) {
                        for (int i = 0; i < TYPES.length; i++) {
                            final Class<?> type = TYPES[i];
                            pushers[i] = (AbstractNetworkPusher) Slimefun.getRegistry().getAllSlimefunItems().stream()
                                .filter(item -> item.getClass() == type)
                                .sorted(Comparator.comparing(SlimefunItem::getId)).findFirst().orElseThrow();
                            location(i).getChunk().load();
                            if (!verify) create(location(i), pushers[i]);
                            transfers[i] = new NativeRecipeTransferProbe(location(i), pushers[i]);
                            if (!verify) transfers[i].create();
                        }
                        created = true;
                    }
                    for (int i = 0; i < TYPES.length; i++) {
                        var data = StorageCacheUtils.getBlock(location(i));
                        menus[i] = StorageCacheUtils.getMenu(location(i));
                        if (data == null || !data.isDataLoaded() || menus[i] == null) {
                            if (++attempts > 120) throw new AssertionError("Native pusher menu initialization timed out");
                            return;
                        }
                        require(data.getSfId().equals(pushers[i].getId()), "Wrong physical pusher identity");
                        require(pushers[i].getBlockTicker().isSynchronized(), "Pusher GUI must use synchronized ticker");
                        if (!transfers[i].ready()) {
                            if (++attempts > 120) throw new AssertionError("Native source/controller/pusher topology timed out");
                            return;
                        }
                    }
                    for (int i = 0; i < TYPES.length; i++) {
                        require(!menus[i].hasViewer(), "Initial fixture must have no GUI viewer");
                        if (!verify) {
                            // Represent a real item left in the historically writable slot
                            // before any viewer tick registers the recipe control handler.
                            menus[i].replaceExistingItem(pushers[i].getOtherBackgroundSlots()[0], token(FOREIGN_AMOUNT));
                        } else {
                            require("true".equals(StorageCacheUtils.getData(location(i), MODE_KEY)), "Mode failed clean restart");
                            require("5".equals(StorageCacheUtils.getData(location(i), BATCH_KEY)), "Batch target failed clean restart");
                        }
                    }
                    Files.writeString(file("ready.txt"), "READY: native recipe " + (verify ? "verify" : "seed") + "\n");
                    cancel();
                } catch (Throwable error) { cancel(); fail(error); }
            }
        }.runTaskTimer(this, 40L, 5L);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!finished) fail(new AssertionError("Native recipe deadline tier=" + tier + " stage=" + stage));
        }, 5000L);
    }

    private static void create(Location location, SlimefunItem item) {
        require(Bukkit.isPrimaryThread(), "Create fixture only on server thread");
        require(StorageCacheUtils.getBlock(location) == null, "Fixture must not replace existing records");
        location.getBlock().setType(item.getItem().getType());
        var database = Slimefun.getDatabaseManager().getBlockDataController();
        var data = database.createBlock(location, item.getId());
        if (!data.isDataLoaded()) database.loadBlockData(data);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        if (!"RecipeProbe".equals(joining.getName()) || joining.getAddress() == null
            || !joining.getAddress().getAddress().isLoopbackAddress() || player != null) {
            joining.kickPlayer("Disposable loopback recipe fixture only");
            return;
        }
        player = joining;
        joining.setGameMode(GameMode.SURVIVAL);
        joining.setInvulnerable(true);
        joining.setGravity(false);
        joining.addAttachment(this, "slimefun.inventory.bypass", true);
        joining.teleport(location(0).add(0.5, 1, 1.5));
    }

    @EventHandler(priority = EventPriority.MONITOR) public void onClick(InventoryClickEvent event) {
        if (finished || event.getWhoClicked() != player || menu() == null
            || event.getView().getTopInventory() != menu().toInventory()) return;
        clickCount++;
        lastRaw = event.getRawSlot();
        lastClick = event.getClick().name();
        getLogger().info("Actual recipe click tier=" + tier + " stage=" + stage + " raw=" + lastRaw
            + " click=" + lastClick + " cancelled=" + event.isCancelled());
    }

    @EventHandler(priority = EventPriority.MONITOR) public void onBreak(BlockBreakEvent event) {
        if (finished || event.getPlayer() != player || tier >= TYPES.length
            || !event.getBlock().getLocation().equals(location(tier))) return;
        breakEvents++;
        lastBreakCancelled = event.isCancelled();
        getLogger().info("Actual recipe break tier=" + tier + " cancelled=" + lastBreakCancelled);
    }

    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer() != player || !event.getMessage().startsWith("/recipefixture ")) return;
        event.setCancelled(true);
        String command = event.getMessage().substring("/recipefixture ".length()).trim();
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { check(command); } catch (Throwable error) { fail(error); }
        }, 5L);
    }

    private void check(String command) throws Exception {
        require(!finished && player != null && menu() != null, "Recipe fixture must be active");
        switch (command) {
            case "start" -> {
                require(stage == 0 && tier == 0, "Unexpected start");
                requireLedger();
                if (verify) {
                    require(playerTokens() == TOTAL && count(player.getItemOnCursor()) == 0, "Recovered items failed player restart");
                    menu().open(player); tick(); stage = 20; signal("verify-0");
                } else {
                    require(playerTokens() == 0 && count(player.getItemOnCursor()) == 0, "Fresh fixture player already has test items");
                    beginTier();
                }
            }
            case "pickup" -> {
                require(stage == 1 && !verify, "Unexpected pickup"); expectClick(slot(), "LEFT");
                require(token(FOREIGN_AMOUNT).equals(player.getItemOnCursor()), "Initial foreign PDC stack was locked or altered");
                require(!isToken(menu().getItemInSlot(slot())), "Foreign item duplicated in control slot");
                requireLedger(); tick(); stage = 2; signal("store-" + tier);
            }
            case "store" -> {
                require(stage == 2, "Unexpected inventory store"); expectClick(45 + tier, "LEFT");
                require(count(player.getItemOnCursor()) == 0 && playerTokens() == (tier + 1) * FOREIGN_AMOUNT,
                    "Ordinary pickup must preserve all recovered items");
                requireLedger(); tick(); checkControl(false, 8); stage = 3; signal("toggle-" + tier);
            }
            case "toggle" -> {
                require(stage == 3, "Unexpected toggle"); expectClick(slot(), "LEFT"); checkControl(true, 8);
                requireLedger(); stage = 4; signal("plus-" + tier);
            }
            case "plus" -> {
                require(stage == 4, "Unexpected increment"); expectClick(slot(), "SHIFT_RIGHT"); checkControl(true, 9);
                requireLedger(); stage = 5; signal("minus-" + tier);
            }
            case "minus" -> {
                require(stage == 5, "Unexpected decrement"); expectClick(slot(), "SHIFT_LEFT"); checkControl(true, 8);
                requireLedger(); stage = 6; signal("exact-" + tier);
            }
            case "exact" -> {
                require(stage == 6, "Unexpected exact batch input"); expectClick(slot(), "RIGHT"); checkControl(true, 5);
                requireLedger(); stage = 7; signal("off-" + tier);
            }
            case "off" -> {
                require(stage == 7, "Unexpected OFF click"); expectClick(slot(), "LEFT"); checkControl(false, 5);
                requireLedger(); stage = 8; signal("on-" + tier);
            }
            case "on" -> {
                require(stage == 8, "Unexpected ON click"); expectClick(slot(), "LEFT"); checkControl(true, 5);
                requireLedger(); stage = 9; signal("close-" + tier);
            }
            case "reopen" -> {
                require(stage == 9, "Unexpected reopen");
                menu().open(player); tick(); checkControl(true, 5); requireLedger();
                getLogger().info("PASS: native recipe tier=" + TYPES[tier].getSimpleName()
                    + " foreign-pickup=13 toggle=true shifts=true exact-batches=5 close-reopen=true");
                getLogger().info(transfers[tier].run());
                if (++tier < TYPES.length) beginTier(); else finish("seed");
            }
            case "verify" -> {
                require(verify && stage == 20, "Unexpected restart verification");
                tick(); checkControl(true, 5); requireLedger();
                transfers[tier].verifyRestart();
                if (++tier < TYPES.length) {
                    menu().open(player); tick(); signal("verify-" + tier);
                } else finish("verify");
            }
            default -> throw new AssertionError("Unknown recipe command " + command);
        }
    }

    private void beginTier() {
        stage = 1;
        require(token(FOREIGN_AMOUNT).equals(menu().getItemInSlot(slot())), "Foreign stack changed before its first viewer");
        require(count(player.getItemOnCursor()) == 0, "Cursor must be empty before next tier");
        player.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
        int beforeBreakEvents = breakEvents;
        Material beforeType = location(tier).getBlock().getType();
        boolean broken = player.breakBlock(location(tier).getBlock());
        require(breakEvents == beforeBreakEvents + 1, "Real player break must reach the native event pipeline");
        require(!broken && lastBreakCancelled, "Breaking a pusher with a foreign control stack must be cancelled");
        require(StorageCacheUtils.getMenu(location(tier)) == menu()
                && StorageCacheUtils.isBlock(location(tier), pushers[tier].getId())
                && location(tier).getBlock().getType() == beforeType,
            "Foreign control stack break must preserve the actual block, data and canonical inventory");
        requireLedger();
        menu().open(player);
        tick();
        require(token(FOREIGN_AMOUNT).equals(menu().getItemInSlot(slot())), "First viewer tick destroyed foreign stack");
        signal("pickup-" + tier);
    }

    private void tick() {
        require(Bukkit.isPrimaryThread(), "Production pusher tick must be on server thread");
        pushers[tier].getBlockTicker().tick(location(tier).getBlock(), pushers[tier], StorageCacheUtils.getBlock(location(tier)));
    }

    private void checkControl(boolean enabled, int batches) {
        require(enabled == Boolean.parseBoolean(StorageCacheUtils.getData(location(tier), MODE_KEY)), "Stored mode differs from click");
        String stored = StorageCacheUtils.getData(location(tier), BATCH_KEY);
        require(stored == null ? batches == 8 : Integer.toString(batches).equals(stored), "Stored batch target differs from click");
        ItemStack icon = menu().getItemInSlot(slot());
        require(icon != null && icon.getType() == (enabled ? Material.CRAFTING_TABLE : Material.HOPPER)
                && icon.getAmount() == batches, "Control icon type/amount differs from persisted setting");
        var lore = icon.getItemMeta().lore();
        require(lore != null && lore.size() > 4 && PlainTextComponentSerializer.plainText()
            .serialize(lore.get(4)).equals("Batch target: " + batches), "Control lore differs from actual batch count");
    }

    private void expectClick(int raw, String type) {
        require(clickCount == acceptedClicks + 1 && lastRaw == raw && type.equals(lastClick),
            "Missing/extra native click tier=" + tier + " stage=" + stage + " raw=" + lastRaw + " type=" + lastClick);
        acceptedClicks = clickCount;
    }

    private void requireLedger() {
        int total = playerTokens() + (isToken(player.getItemOnCursor()) ? player.getItemOnCursor().getAmount() : 0);
        for (int i = 0; i < menus.length; i++) {
            for (int slot = 0; slot < menus[i].getSize(); slot++) {
                ItemStack item = menus[i].getItemInSlot(slot);
                if (isToken(item)) total += item.getAmount();
            }
        }
        require(total == TOTAL, "Exact native recipe ledger changed from 39 to " + total);
    }

    private int playerTokens() {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) if (isToken(item)) count += item.getAmount();
        return count;
    }
    private static ItemStack token(int amount) {
        var item = new ItemStack(Material.DIAMOND, amount);
        item.editMeta(meta -> meta.getPersistentDataContainer().set(new NamespacedKey("networkstest", "recipe_owner"),
            PersistentDataType.STRING, "preserve-initial-foreign-slot"));
        return item;
    }
    private static boolean isToken(ItemStack item) { return count(item) > 0 && token(1).isSimilar(item); }
    private static int count(ItemStack item) { return item == null || item.getType().isAir() ? 0 : item.getAmount(); }
    private void signal(String text) { player.sendMessage("NATIVERECIPE:" + text); }
    private void finish(String phase) throws Exception {
        require(playerTokens() == TOTAL && count(player.getItemOnCursor()) == 0, "Final recovered inventory must contain exactly 39 items");
        finished = true;
        String report = "PASS: native-recipe-" + phase + " tiers=Classic,More,Best total=39"
            + " foreign-pdc-preserved=true mode=true batches=5"
            + " real-smeltery-inputs=true exact-ingredient-ledgers=true off-parity=true"
            + (verify ? " independent-jvm=true" : " actual-clicks=" + clickCount
                + " foreign-breaks-blocked=" + breakEvents + " exact-chat=true close-reopen=true") + "\n";
        Files.writeString(file("result.txt"), report);
        getLogger().info(report.trim()); signal("PASS");
        player.closeInventory(); player.saveData();
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 10L);
    }
    private void fail(Throwable error) {
        if (finished) return;
        finished = true; error.printStackTrace();
        try { Files.writeString(file("result.txt"), "FAIL: native-recipe tier=" + tier + " stage=" + stage + " " + error + "\n"); }
        catch (Exception writeFailure) { writeFailure.printStackTrace(); }
        if (player != null) signal("FAIL");
        Bukkit.shutdown();
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

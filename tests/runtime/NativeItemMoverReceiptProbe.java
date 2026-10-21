package itemmoverprobe;

import com.jeff_media.morepersistentdatatypes.DataType;
import com.ytdd9527.networksexpansion.implementation.ExpansionItemStacks;
import com.ytdd9527.networksexpansion.implementation.tools.ItemMover;
import com.ytdd9527.networksexpansion.implementation.tools.ItemMoverStorage;
import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.DataScope;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.FieldKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordKey;
import com.xzavier0722.mc.plugin.slimefun4.storage.common.RecordSet;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.ADataController;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.thebusybiscuit.slimefun4.api.events.PlayerRightClickEvent;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import io.github.sefiraat.networks.utils.Keys;

/** Native production-method/PDC and clean-restart proof; callbacks are explicit fault injection. */
public final class NativeItemMoverReceiptProbe extends JavaPlugin implements Listener {
    private static final NamespacedKey OWNER = new NamespacedKey("item-mover-probe", "owner");
    private String phase;
    private Player player;
    private String expectedGesture = "take-main";
    private int mainClicks;
    private int offClicks;
    private boolean staleInjected;
    private Path file(String suffix) { return getDataFolder().toPath().getParent().resolve("native-item-mover-" + suffix); }

    @Override public void onEnable() {
        if (!"disposable".equals(System.getProperty("networks.item-mover.probe"))) {
            Bukkit.getPluginManager().disablePlugin(this); return;
        }
        try {
            phase = Files.readString(file("phase.txt")).trim();
            if (phase.equals("verify") && Boolean.getBoolean("networks.fixture.persistenceAudit")) {
                auditPersistence("enable-before-chunk-load");
            }
        }
        catch (Exception failure) { finish("FAIL: setup " + failure); return; }
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { if (phase.equals("seed")) seed(); else verify(); }
            catch (Throwable failure) { failure.printStackTrace(); finish("FAIL: " + phase + " " + failure); }
        }, 40L);
    }

    private Chest chest(boolean create) {
        Location location = new Location(Bukkit.getWorlds().getFirst(), 160, 100, 160);
        location.getChunk().load();
        if (create) {
            require(location.getBlock().getType().isAir(), "Fresh disposable location required");
            location.clone().add(0, -1, 0).getBlock().setType(Material.STONE);
            location.getBlock().setType(Material.CHEST);
        }
        require(location.getBlock().getState() instanceof Chest, "Saved fixture chest must exist");
        return (Chest) location.getBlock().getState();
    }

    private void seed() {
        Chest chest = chest(true);
        ItemStack normal = mover(0);
        ItemStack incoming = token(17);
        ItemMover.depositItem(normal, incoming);
        require(incoming.getAmount() == 0 && ItemMoverStorage.inspect(normal).amount() == 17,
            "Production deposit must preserve exactly17 items");
        require(token(1).equals(ItemMoverStorage.inspect(normal).template()), "Native deposited metadata must match");

        ItemStack rebuilding = new RebuildingStack(mover(37));
        rebuilding.editMeta(meta -> {
            var model = meta.getCustomModelDataComponent();
            model.setFloats(List.of(731f, 732f)); model.setStrings(List.of("retained-model"));
            model.setFlags(List.of(true, false)); model.setColors(List.of(Color.fromRGB(17, 31, 47)));
            meta.setCustomModelDataComponent(model);
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "wrapper-owner");
        });
        require(ItemMoverStorage.transferTo(rebuilding, 5, stack -> stack.setAmount(0)) == 5
            && ItemMoverStorage.inspect(rebuilding).amount() == 32, "Rebuilding wrapper must retain exact balance");
        var retainedModel = rebuilding.getItemMeta().getCustomModelDataComponent();
        require(retainedModel.getFloats().equals(List.of(731f, 732f))
            && retainedModel.getStrings().equals(List.of("retained-model"))
            && retainedModel.getFlags().equals(List.of(true, false))
            && retainedModel.getColors().equals(List.of(Color.fromRGB(17, 31, 47)))
            && "wrapper-owner".equals(rebuilding.getItemMeta().getPersistentDataContainer().get(OWNER, PersistentDataType.STRING)),
            "Native modern model components and original owner survive wrapper snapshots");

        ItemStack empty = mover(0);
        empty.editMeta(meta -> {
            meta.getPersistentDataContainer().set(Keys.ITEM_MOVER_ITEM, DataType.ITEM_STACK, token(1));
            meta.getPersistentDataContainer().set(Keys.ITEM_MOVER_AMOUNT_LONG, PersistentDataType.LONG, 0L);
        });
        ItemMover.updateLore(empty);
        require(!empty.getItemMeta().getPersistentDataContainer().has(Keys.ITEM_MOVER_ITEM)
            && !empty.getItemMeta().getPersistentDataContainer().has(Keys.ITEM_MOVER_AMOUNT_LONG),
            "Actual lore method cannot resurrect zero-balance PDC");

        ItemStack unknown = mover(0);
        unknown.editMeta(meta -> meta.getPersistentDataContainer().set(Keys.ITEM_MOVER_AMOUNT_LONG, PersistentDataType.LONG, 13L));
        ItemStack unknownBefore = unknown.clone();
        ItemMover.updateLore(unknown);
        require(unknownBefore.equals(unknown), "Lore must preserve unknown positive contents exactly");
        boolean rejectedUnknown = false;
        ItemStack refused = token(3);
        try { ItemMover.depositItem(unknown, refused); }
        catch (IllegalStateException expected) { rejectedUnknown = true; }
        require(rejectedUnknown && unknownBefore.equals(unknown) && token(3).equals(refused), "Never guess unknown identity");

        LiveStack rejected = new LiveStack(mover(13));
        rejected.rejectMeta = true;
        ItemStack rejectedInput = token(7);
        ItemMover.depositItem(rejected, rejectedInput);
        require(rejected.writes == 1 && ItemMoverStorage.inspect(rejected).amount() == 13
            && token(7).equals(rejectedInput), "Refused native metadata must not consume source");

        LiveStack reentrant = new LiveStack(mover(13));
        AtomicInteger calls = new AtomicInteger();
        reentrant.callback = () -> {
            calls.incrementAndGet();
            require(ItemMoverStorage.transferTo(reentrant, 13, stack -> { throw new AssertionError("Reentrant withdrawal"); }) == 0,
                "Native source must remain owned during metadata setter");
        };
        ItemMover.depositItem(reentrant, token(7));
        require(calls.get() == 1 && ItemMoverStorage.inspect(reentrant).amount() == 20, "Reentry cannot replay source");

        ItemStack partial = mover(37);
        require(ItemMoverStorage.transferTo(partial, 17, stack -> stack.setAmount(10)) == 7
            && ItemMoverStorage.inspect(partial).amount() == 30, "Exact normal partial remainder must conserve37");

        ItemStack held = mover(37);
        boolean failureReached = false;
        try {
            ItemMoverStorage.transferTo(held, 17, stack -> {
                stack.setAmount(10);
                throw new IllegalStateException("Injected partial destination failure");
            });
        } catch (IllegalStateException expected) { failureReached = true; }
        require(failureReached && ItemMoverStorage.hasPendingTransfer(held), "Unverified destination must retain durable receipt");
        ItemStack heldBeforeLore = held.clone();
        ItemMover.updateLore(held);
        require(heldBeforeLore.equals(held), "Lore cannot clear a pending transfer");
        verifyHeld(held);

        ItemStack high = mover(Long.MAX_VALUE - 3);
        ItemStack highInput = token(17);
        ItemMover.depositItem(high, highInput);
        require(ItemMoverStorage.inspect(high).amount() == Long.MAX_VALUE && token(14).equals(highInput),
            "Native long boundary accepts exactly3, preserving14 outside");

        chest.getInventory().setItem(0, normal);
        chest.getInventory().setItem(1, held);
        chest.getInventory().setItem(2, high);
        chest.getInventory().setItem(3, token(7)); // Explicit injected destination outcome, retained for reconciliation.
        // These are live inventory writes. Updating the old Chest block-state
        // snapshot here would overwrite them, so verify the authoritative slots.
        var saved = chest(false).getInventory();
        require(ItemMoverStorage.snapshot(normal).equals(saved.getItem(0))
            && ItemMoverStorage.snapshot(held).equals(saved.getItem(1))
            && ItemMoverStorage.snapshot(high).equals(saved.getItem(2))
            && token(7).equals(saved.getItem(3)), "Native chest must acknowledge every exact fixture item");
        preparePlayerStorage();
    }

    private Location storageLocation() {
        return new Location(Bukkit.getWorlds().getFirst(), 160, 100, 166);
    }

    private void preparePlayerStorage() {
        NetworkQuantumStorage unit = Slimefun.getRegistry().getAllSlimefunItems().stream()
            .filter(item -> item instanceof NetworkQuantumStorage storage && storage.getMaxAmount() >= 256)
            .map(item -> (NetworkQuantumStorage) item)
            .sorted(Comparator.comparingLong(NetworkQuantumStorage::getMaxAmount).thenComparing(SlimefunItem::getId))
            .findFirst().orElseThrow();
        Location location = storageLocation();
        location.getChunk().load();
        require(location.getBlock().getType().isAir() && StorageCacheUtils.getBlock(location) == null,
            "Player fixture storage must be new");
        for (int x = -2; x <= 2; x++) for (int z = -1; z <= 4; z++) {
            location.clone().add(x, -1, z).getBlock().setType(Material.STONE);
        }
        location.getBlock().setType(unit.getItem().getType());
        var database = Slimefun.getDatabaseManager().getBlockDataController();
        var data = database.createBlock(location, unit.getId());
        if (!data.isDataLoaded()) database.loadBlockData(data);
        new BukkitRunnable() {
            int attempts;
            boolean initialized;
            @Override public void run() {
                try {
                    var menu = StorageCacheUtils.getMenu(location);
                    var cache = NetworkQuantumStorage.getCaches().get(location);
                    if (menu != null && cache != null) {
                        if (!initialized) {
                            // Explicit fixture seed; the real storage ticker fills the output.
                            NetworkQuantumStorage.setItem(menu, token(1), 101L);
                            initialized = true;
                        }
                        ItemStack output = menu.getItemInSlot(NetworkQuantumStorage.OUTPUT_SLOT);
                        if (cache.getAmountLong() == 37 && token(64).equals(output)) {
                            verifyStorage(101);
                            Files.writeString(file("ready.txt"), "READY: actual client hand fixture\n");
                            cancel();
                            return;
                        }
                    }
                    if (++attempts > 100) throw new AssertionError("Native Quantum menu/cache/output did not become ready");
                } catch (Throwable failure) { cancel(); fail(failure); }
            }
        }.runTaskTimer(this, 1L, 5L);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player joining = event.getPlayer();
        if (!phase.equals("seed") || player != null || !"ItemMoverProbe".equals(joining.getName())
            || joining.getAddress() == null || !joining.getAddress().getAddress().isLoopbackAddress()) {
            joining.kickPlayer("Disposable loopback fixture only"); return;
        }
        player = joining;
        player.setOp(true);
        player.setGameMode(GameMode.SURVIVAL);
        player.setInvulnerable(true);
        player.getInventory().clear();
        player.getInventory().setItem(0, mover(0));
        player.getInventory().setHeldItemSlot(0);
        player.teleport(storageLocation().clone().add(0.5, 0, 2.5));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                require(SlimefunItem.getByItem(player.getInventory().getItemInMainHand()) instanceof ItemMover,
                    "Actual hand must resolve to registered ItemMover");
                require(SlimefunItem.getByItem(player.getInventory().getItemInMainHand()).canUse(player, true),
                    "Native player must have permission to use the real ItemMover");
                player.sendMessage("NATIVEMOVER:take-main-ready");
            } catch (Throwable failure) { fail(failure); }
        }, 10L);
    }

    @EventHandler(priority = EventPriority.LOWEST) public void onInteract(PlayerInteractEvent event) {
        if (event.getPlayer() == player && event.getAction() == Action.RIGHT_CLICK_BLOCK
            && event.getClickedBlock() != null && event.getClickedBlock().getLocation().equals(storageLocation())) {
            if (event.getHand() == EquipmentSlot.HAND) mainClicks++;
            if (event.getHand() == EquipmentSlot.OFF_HAND) offClicks++;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST) public void onSlimefunInteract(PlayerRightClickEvent event) {
        if (event.getPlayer() != player || !expectedGesture.equals("stale-main") || staleInjected
            || event.getHand() != EquipmentSlot.HAND || event.getClickedBlock().isEmpty()
            || !event.getClickedBlock().get().getLocation().equals(storageLocation())) return;
        // Explicit native event fault injection: replace the authoritative hand after
        // the original event captured it, before Slimefun invokes the production handler.
        chest(false).getInventory().setItem(4, player.getInventory().getItemInMainHand().clone());
        player.getInventory().setItemInMainHand(new ItemStack(Material.EMERALD));
        staleInjected = true;
    }

    @EventHandler public void onCommand(PlayerCommandPreprocessEvent event) {
        if (event.getPlayer() != player || !event.getMessage().startsWith("/moverfixture ")) return;
        event.setCancelled(true);
        try {
            String gesture = event.getMessage().substring("/moverfixture ".length());
            require(gesture.equals(expectedGesture), "Unexpected client gesture: " + gesture);
            switch (gesture) {
                case "take-main" -> {
                    verifyHand(EquipmentSlot.HAND, 37); verifyStorage(64);
                    require(mainClicks == 1 && offClicks == 0, "Require one actual main-hand click");
                    expectedGesture = "return-main";
                }
                case "return-main" -> {
                    verifyHand(EquipmentSlot.HAND, 0); verifyStorage(101);
                    require(mainClicks == 2 && offClicks == 0, "Require main-hand return click");
                    player.getInventory().setItemInOffHand(player.getInventory().getItemInMainHand().clone());
                    player.getInventory().setItemInMainHand(null);
                    expectedGesture = "take-off";
                }
                case "take-off" -> {
                    verifyHand(EquipmentSlot.OFF_HAND, 37); verifyStorage(64);
                    require(mainClicks == 2 && offClicks == 1, "Require actual off-hand click");
                    expectedGesture = "return-off";
                }
                case "return-off" -> {
                    verifyHand(EquipmentSlot.OFF_HAND, 0); verifyStorage(101);
                    require(mainClicks == 2 && offClicks == 2, "Require off-hand return click");
                    player.getInventory().setItemInMainHand(player.getInventory().getItemInOffHand().clone());
                    player.getInventory().setItemInOffHand(null);
                    expectedGesture = "stale-main";
                }
                case "stale-main" -> {
                    require(staleInjected && mainClicks == 3 && offClicks == 2, "Require actual stale-hand event");
                    require(new ItemStack(Material.EMERALD).equals(player.getInventory().getItemInMainHand()),
                        "Stale event must preserve the replacement hand exactly");
                    verifyStorage(101);
                    require(ItemMoverStorage.inspect(chest(false).getInventory().getItem(4)).amount() == 0,
                        "Detached original mover must not receive storage items");
                    var data = StorageCacheUtils.getBlock(storageLocation());
                    var cache = NetworkQuantumStorage.getCaches().get(storageLocation());
                    require(data != null && data.isDataLoaded() && !data.isPendingRemove()
                        && String.valueOf(cache.getAmountLong()).equals(data.getData(NetworkQuantumStorage.BS_AMOUNT)),
                        "Native Quantum persistent state must match before shutdown");
                    getLogger().info("Quantum shutdown ledger: reserve=" + cache.getAmountLong()
                        + " loaded=" + data.isDataLoaded() + " pending=" + data.isPendingRemove()
                        + " menu-dirty=" + StorageCacheUtils.getMenu(storageLocation()).isDirty());
                    finish("PASS: seed native-production-methods=true exact-ledgers=true rejected-write=true same-item-reentry=true"
                        + " pending-original-bytes=true zero-lore=true unknown-identity-refused=true long-boundary=true"
                        + " rebuilding-wrapper-components=true actual-client-main-hand=true actual-client-off-hand=true"
                        + " stale-event-refused=true native-clicks=5");
                    return;
                }
                default -> throw new AssertionError("Unknown gesture");
            }
            player.updateInventory();
            player.sendMessage("NATIVEMOVER:" + expectedGesture + "-ready");
        } catch (Throwable failure) { fail(failure); }
    }

    private void verifyHand(EquipmentSlot hand, long amount) {
        ItemStack held = player.getInventory().getItem(hand);
        require(held != null && SlimefunItem.getByItem(held) instanceof ItemMover, "Actual hand identity must remain ItemMover");
        var state = ItemMoverStorage.inspect(held);
        require(state.amount() == amount && (amount == 0 || token(1).equals(state.template())),
            "Actual hand must hold exact amount and identity: expected " + amount + " got " + state.amount());
    }

    private void verifyStorage(long expected) {
        var cache = NetworkQuantumStorage.getCaches().get(storageLocation());
        var menu = StorageCacheUtils.getMenu(storageLocation());
        require(cache != null && menu != null && token(1).equals(cache.getItemStack()), "Actual Quantum identity must remain exact");
        long total = cache.getAmountLong();
        for (int slot : new int[]{NetworkQuantumStorage.INPUT_SLOT, NetworkQuantumStorage.OUTPUT_SLOT}) {
            ItemStack physical = menu.getItemInSlot(slot);
            if (physical != null && !physical.getType().isAir() && physical.getAmount() > 0) {
                require(token(1).isSimilar(physical), "Quantum physical item identity must remain exact");
                total += physical.getAmount();
            }
        }
        require(total == expected, "Actual Quantum total: expected " + expected + " got " + total);
    }

    private void verify() throws Exception {
        if (Boolean.getBoolean("networks.fixture.persistenceAudit")) auditPersistence("verify-before-chunk-load");
        Chest chest = chest(false);
        ItemStack normal = chest.getInventory().getItem(0);
        ItemStack held = chest.getInventory().getItem(1);
        ItemStack high = chest.getInventory().getItem(2);
        require(normal != null && ItemMoverStorage.inspect(normal).amount() == 17
            && token(1).equals(ItemMoverStorage.inspect(normal).template()), "Healthy native balance/identity must reload exactly");
        require(high != null && ItemMoverStorage.inspect(high).amount() == Long.MAX_VALUE
            && token(1).equals(ItemMoverStorage.inspect(high).template()), "Long maximum must survive native persistence");
        verifyHeld(held);
        require(token(7).equals(chest.getInventory().getItem(3)), "Known external outcome remains separate");
        require(ItemMoverStorage.inspect(chest.getInventory().getItem(4)).amount() == 0,
            "Stale-event mover stays unchanged after restart");
        storageLocation().getChunk().load();
        new BukkitRunnable() {
            int attempts;
            @Override public void run() {
                try {
                    if (NetworkQuantumStorage.getCaches().get(storageLocation()) != null
                        && StorageCacheUtils.getMenu(storageLocation()) != null) {
                        if (Boolean.getBoolean("networks.fixture.persistenceAudit")) {
                            auditPersistence("verify-after-chunk-load");
                        }
                        verifyStorage(101);
                        cancel();
                        finish("PASS: verify independent-JVM healthy17=true long-maximum=true pending-receipt-exact=true"
                            + " replay-blocked=true actual-hand-storage101=true stale-original0=true");
                    } else if (++attempts > 100) throw new AssertionError("Native Quantum cache did not reload");
                } catch (Throwable failure) { cancel(); fail(failure); }
            }
        }.runTaskTimer(this, 1L, 5L);
    }

    @SuppressWarnings("unchecked") private void auditPersistence(String label) throws Exception {
        // Read-only diagnostics distinguish persisted source state from cache
        // initialization. Never repair, reseed, delete, or overwrite database rows.
        var controller = Slimefun.getDatabaseManager().getBlockDataController();
        var read = ADataController.class.getDeclaredMethod("getData", RecordKey.class);
        read.setAccessible(true);
        var dataKey = new RecordKey(DataScope.BLOCK_DATA);
        dataKey.addCondition(FieldKey.LOCATION, "fixture;160:100:166");
        var data = (List<RecordSet>) read.invoke(controller, dataKey);
        var inventoryKey = new RecordKey(DataScope.BLOCK_INVENTORY);
        inventoryKey.addCondition(FieldKey.LOCATION, "fixture;160:100:166");
        var inventory = (List<RecordSet>) read.invoke(controller, inventoryKey);
        getLogger().info("Persistence audit " + label + ": data="
            + data.stream().map(row -> row.getString(FieldKey.DATA_KEY) + "=" + row.getString(FieldKey.DATA_VALUE)).toList()
            + " inventory-slots=" + inventory.stream().map(row -> row.getInt(FieldKey.INVENTORY_SLOT)).toList());
    }

    private static void verifyHeld(ItemStack held) {
        require(held != null && ItemMoverStorage.hasPendingTransfer(held), "Pending receipt must survive");
        ItemMoverStorage.PendingReceipt receipt = ItemMoverStorage.pendingReceipt(held);
        require(receipt != null && "external-output".equals(receipt.direction()), "Exact receipt direction required");
        require(new ItemStack(mover(37)).equals(receipt.original()) && new ItemStack(mover(20)).equals(receipt.prepared())
            && token(17).equals(receipt.offered()), "Native original/prepared/offered snapshots must retain full metadata");
        boolean blocked = false;
        try { ItemMoverStorage.transferTo(held, 1, stack -> { throw new AssertionError("Pending transfer replayed"); }); }
        catch (IllegalStateException expected) { blocked = true; }
        require(blocked, "Reloaded pending transfer cannot replay");
    }

    private static ItemStack mover(long amount) {
        ItemStack mover = ExpansionItemStacks.ITEM_MOVER.clone();
        if (amount > 0) return ItemMoverStorage.prepareCredit(mover, token(1), amount);
        return mover;
    }
    private static ItemStack token(int amount) {
        ItemStack token = new ItemStack(Material.DIAMOND, amount);
        token.editMeta(meta -> {
            meta.setDisplayName("Native ItemMover ledger");
            meta.setLore(List.of("all original components remain", "owner-A"));
            meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "owner-A");
        });
        return token;
    }
    private void finish(String result) {
        try { Files.writeString(file("result.txt"), result + "\n"); }
        catch (Exception failure) { failure.printStackTrace(); }
        getLogger().info(result);
        if (player != null && player.isOnline()) player.sendMessage(result.startsWith("PASS:") ? "NATIVEMOVER:PASS" : "NATIVEMOVER:FAIL");
        Bukkit.getScheduler().runTaskLater(this, Bukkit::shutdown, 10L);
    }
    private void fail(Throwable failure) { failure.printStackTrace(); finish("FAIL: " + phase + " " + failure); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

    private static final class LiveStack extends ItemStack {
        private boolean rejectMeta;
        private int writes;
        private Runnable callback;
        private LiveStack(ItemStack source) { super(source); }
        @Override public boolean setItemMeta(ItemMeta meta) {
            writes++;
            Runnable current = callback; callback = null;
            if (current != null) current.run();
            return !rejectMeta && super.setItemMeta(meta);
        }
    }

    private static final class RebuildingStack extends ItemStack {
        private RebuildingStack(ItemStack source) { super(source); }
        @Override public ItemStack clone() {
            ItemStack changed = super.clone();
            changed.editMeta(meta -> {
                meta.setCustomModelData(999);
                meta.getPersistentDataContainer().set(OWNER, PersistentDataType.STRING, "rebuilt-owner");
            });
            return changed;
        }
    }
}

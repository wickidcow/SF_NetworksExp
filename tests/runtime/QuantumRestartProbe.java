package networksprobe;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.slimefun.NetworkSlimefunItems;
import io.github.sefiraat.networks.slimefun.network.NetworkQuantumStorage;
import io.github.sefiraat.networks.slimefun.network.QuantumCacheLifecycle;
import io.github.sefiraat.networks.utils.Keys;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * A two-process Slimefun storage persistence test. This plugin is included ONLY
 * in a disposable GitHub Actions test server, never in the Networks release.
 *
 * "seed": create a real Quantum block record and inventory, save and STOP.
 * "verify": load that exact world's data in a DIFFERENT Java process, verify
 * both saved data and the inventory, then break the recovered Quantum Storage.
 */
public final class QuantumRestartProbe extends JavaPlugin {

    private static final int X = 112;
    private static final int Y = 100;
    private static final int Z = 112;
    private static final int COUNT = 22;
    private static final int OUTPUT = 64;
    private static final String OWNER = "persisted-quantum-owner";
    private static final String OWNER_KEY = "physical_owner";

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try {
                final String phase = Files.readString(phaseFile(), StandardCharsets.UTF_8).trim();
                if ("seed".equals(phase)) {
                    seed();
                } else if ("verify".equals(phase)) {
                    verifyAfterRestart();
                } else {
                    finish("FAIL: unknown restart phase " + phase);
                }
            } catch (Throwable failure) {
                fail("startup phase", failure);
            }
        }, 20L);
    }

    private Path phaseFile() {
        return getDataFolder().toPath().getParent().resolve("quantum-restart-phase.txt");
    }

    private Path reportFile() {
        return getDataFolder().toPath().getParent().resolve("quantum-restart-result.txt");
    }

    private Location location() {
        return new Location(Bukkit.getWorlds().getFirst(), X, Y, Z);
    }

    private NamespacedKey ownerKey() {
        return new NamespacedKey("networkstest", OWNER_KEY);
    }

    private ItemStack sample() {
        final ItemStack item = new ItemStack(Material.DIAMOND);
        item.editMeta(meta -> {
            meta.displayName(Component.text("Persisted owner diamond"));
            meta.getPersistentDataContainer().set(ownerKey(), PersistentDataType.STRING, OWNER);
        });
        return item;
    }

    private void seed() {
        final Location location = location();
        final Block block = location.getBlock();
        location.getWorld().getChunkAt(location).load();
        final NetworkQuantumStorage storage = NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_0;
        require(storage != null, "Registered Quantum Storage is missing");
        require(storage.getItem().getType().isBlock(), "Registered storage item must be placeable");
        block.setType(storage.getItem().getType());

        final var controller = Slimefun.getDatabaseManager().getBlockDataController();
        final var data = controller.createBlock(location, storage.getId());
        final BlockMenu menu = StorageCacheUtils.getMenu(location);
        require(menu != null, "Slimefun did not create the registered Quantum block menu");

        final ItemStack template = sample();
        final QuantumCache cache = new QuantumCache(template, COUNT, 64L, false, false);
        NetworkQuantumStorage.getCaches().put(location, cache);
        NetworkQuantumStorage.updateDisplayItem(menu, cache);
        NetworkQuantumStorage.syncBlock(location, cache);
        menu.replaceExistingItem(NetworkQuantumStorage.OUTPUT_SLOT, new ItemStack(Material.EMERALD, OUTPUT));
        menu.markDirty();

        require(Long.toString(COUNT).equals(data.getData(NetworkQuantumStorage.BS_AMOUNT)),
            "Slimefun block KV cache did not record the stored count");
        require(menu.getItemInSlot(NetworkQuantumStorage.ITEM_SLOT) != null,
            "Stored template display was missing before save");

        // The acknowledgement-aware save future finishes only after its inventory
        // write batch has crossed the database queue boundary. Block KV writes and
        // world chunks are flushed by Slimefun/server's normal shutdown below.
        controller.saveBlockInventoryAsync(data).whenComplete((ignored, error) -> {
            try {
                Bukkit.getScheduler().runTask(this, () -> {
                    if (error != null) {
                        fail("persisting block inventory", error);
                        return;
                    }
                    try {
                        location.getWorld().save();
                        finish("PASS: seed stored=" + COUNT + " emeralds=" + OUTPUT
                            + " sfid=" + storage.getId());
                    } catch (Throwable failure) {
                        fail("saving world before clean shutdown", failure);
                    }
                });
            } catch (Throwable failure) {
                fail("completing inventory save", failure);
            }
        });
    }

    private void verifyAfterRestart() {
        final Location location = location();
        location.getWorld().getChunkAt(location).load();
        final var controller = Slimefun.getDatabaseManager().getBlockDataController();

        controller.getChunkDataAsync(location.getChunk()).whenComplete((chunkData, error) -> {
            if (error != null) {
                Bukkit.getScheduler().runTask(this, () -> fail("loading persisted chunk", error));
                return;
            }
            Bukkit.getScheduler().runTask(this, () -> new BukkitRunnable() {
                int checks;

                @Override
                public void run() {
                    try {
                        final var data = StorageCacheUtils.getBlock(location);
                        final BlockMenu menu = StorageCacheUtils.getMenu(location);
                        if (data != null && data.isDataLoaded() && menu != null) {
                            cancel();
                            verifyPhysicalStoredBlock(location, menu);
                        } else if (++checks >= 160) {
                            cancel();
                            finish("FAIL: persisted block or inventory did not load within 40 seconds");
                        }
                    } catch (Throwable failure) {
                        cancel();
                        fail("reading saved Quantum storage", failure);
                    }
                }
            }.runTaskTimer(this, 1L, 5L));
        });
    }

    @SuppressWarnings("deprecation")
    private void verifyPhysicalStoredBlock(Location location, BlockMenu menu) throws Exception {
        final var controller = Slimefun.getDatabaseManager().getBlockDataController();
        final var data = StorageCacheUtils.getBlock(location);
        final NetworkQuantumStorage storage = NetworkSlimefunItems.NETWORK_QUANTUM_STORAGE_0;
        require(data != null, "Slimefun block record vanished across process restart");
        require(storage.getId().equals(data.getSfId()), "The saved storage item ID changed");
        require(location.getBlock().getType() == storage.getItem().getType(),
            "The physical block did not survive the process restart");
        require(Long.toString(COUNT).equals(data.getData(NetworkQuantumStorage.BS_AMOUNT)),
            "Saved Quantum count must be 22 after a separate JVM restart");

        final ItemStack displayed = menu.getItemInSlot(NetworkQuantumStorage.ITEM_SLOT);
        require(displayed != null && displayed.getType() == Material.DIAMOND,
            "The saved Quantum inventory lost its item template");
        require(OWNER.equals(displayed.getItemMeta().getPersistentDataContainer()
                .get(ownerKey(), PersistentDataType.STRING)),
            "Persisted PDC owner tag did not survive restart");
        final ItemStack output = menu.getItemInSlot(NetworkQuantumStorage.OUTPUT_SLOT);
        require(output != null && output.getType() == Material.EMERALD && output.getAmount() == OUTPUT,
            "Persisted output inventory must still contain exactly 64 emeralds");

        // Ensure the break rehydrates from the independently persisted Slimefun
        // block KV and inventory, not an in-memory cache from startup.
        NetworkQuantumStorage.getCaches().remove(location);
        final Method onBreak = NetworkQuantumStorage.class.getDeclaredMethod(
            "onBreak", BlockBreakEvent.class);
        onBreak.setAccessible(true);
        final BlockBreakEvent event = new BlockBreakEvent(location.getBlock(), null);
        final List<Item> drops = new ArrayList<>();
        try {
            onBreak.invoke(storage, event);
            require(!event.isDropItems(), "One filled storage must suppress normal duplicate block drops");
            require(!NetworkQuantumStorage.getCaches().containsKey(location),
                "Break must detach the rehydrated Quantum cache");

            for (var entity : location.getWorld().getNearbyEntities(location, 3, 3, 3)) {
                if (entity instanceof Item drop) drops.add(drop);
            }
            int carrying = 0;
            int emeralds = 0;
            for (Item drop : drops) {
                final ItemStack item = drop.getItemStack();
                final QuantumCache saved = Keys.getQuantumCache(item.getItemMeta());
                if (saved != null) {
                    carrying++;
                    require(saved.getAmountLong() == COUNT,
                        "The broken storage did not preserve exactly 22 diamonds");
                    require(saved.getItemStack() != null
                            && OWNER.equals(saved.getItemStack().getItemMeta()
                                .getPersistentDataContainer().get(ownerKey(), PersistentDataType.STRING)),
                        "The dropped storage item lost the custom PDC owner tag");
                } else if (item.getType() == Material.EMERALD) {
                    emeralds += item.getAmount();
                }
            }
            final ItemStack slotAfterBreak = menu.getItemInSlot(NetworkQuantumStorage.OUTPUT_SLOT);
            final List<String> dropDetails = drops.stream()
                .map(entity -> entity.getItemStack().getType() + " x"
                    + entity.getItemStack().getAmount() + " at " + entity.getLocation())
                .toList();
            getLogger().info("RESTART OUTPUT DIAGNOSTIC before=EMERALD x" + OUTPUT
                + " droppedEmeralds=" + emeralds
                + " entities=" + dropDetails
                + " outputSlotAfterBreak=" + (slotAfterBreak == null ? "null"
                    : slotAfterBreak.getType() + " x" + slotAfterBreak.getAmount()));
            require(carrying == 1, "Expected exactly one storage item carrying saved contents");
            require(emeralds == OUTPUT, "Expected exactly " + OUTPUT
                + " emeralds; observed " + emeralds + " entities=" + dropDetails
                + " outputSlotAfterBreak=" + slotAfterBreak);
        } finally {
            // Finish all main-thread block/DB operations *before* shutdown begins.
            // Calling Bukkit.shutdown first would close Slimefun's storage controller.
            for (Item drop : drops) drop.remove();
            NetworkQuantumStorage.getCaches().remove(location);
            QuantumCacheLifecycle.clearBreakMarker(location);
            controller.removeBlock(location);
            location.getBlock().setType(Material.AIR);
        }
        finish("PASS: verify stored=" + COUNT + " emeralds=" + OUTPUT
            + " new-process=true cold-cache=true");
    }

    private void fail(String where, Throwable exception) {
        getLogger().severe("RESTART PROBE FAILED at " + where + ": " + exception);
        exception.printStackTrace();
        finish("FAIL: " + where + ": " + exception);
    }

    private void finish(String result) {
        getLogger().info(result);
        try {
            Files.writeString(reportFile(), result + "\n", StandardCharsets.UTF_8);
        } catch (Exception failure) {
            getLogger().severe("Could not save restart probe result: " + failure);
        } finally {
            Bukkit.shutdown();
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

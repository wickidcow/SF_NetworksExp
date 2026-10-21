package networksprobe;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.slimefun.network.NetworkCell;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.slimefun.network.pusher.AbstractNetworkPusher;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.implementation.items.electric.machines.ElectricSmeltery;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import me.mrCookieSlime.Slimefun.Objects.SlimefunItem.abstractItems.MachineRecipe;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;

/** Native Cell -> controller -> pusher -> registered Electric Smeltery transfer oracle. */
final class NativeRecipeTransferProbe {
    private static final int STOCK = 256;
    private final Location pusherLocation;
    private final Location cellLocation;
    private final Location controllerLocation;
    private final Location targetLocation;
    private final AbstractNetworkPusher pusher;
    private final SlimefunItem cell;
    private final SlimefunItem controller;
    private final ElectricSmeltery machine;
    private final List<ItemStack> ingredients;
    private BlockMenu source;
    private BlockMenu target;
    private BlockMenu configuration;

    NativeRecipeTransferProbe(Location pusherLocation, AbstractNetworkPusher pusher) {
        require("disposable".equals(System.getProperty("networks.native.recipe")), "Disposable recipe fixture required");
        this.pusherLocation = pusherLocation.clone();
        this.pusher = pusher;
        cellLocation = pusherLocation.clone().add(-2, 0, 0);
        controllerLocation = pusherLocation.clone().add(-1, 0, 0);
        targetLocation = pusherLocation.clone().add(1, 0, 0);
        cell = registered(NetworkCell.class);
        controller = registered(NetworkController.class);
        machine = (ElectricSmeltery) registered(ElectricSmeltery.class);
        ingredients = machine.getMachineRecipes().stream()
            .map(NativeRecipeTransferProbe::ingredients)
            .filter(items -> items.size() >= 2 && items.size() <= 3
                && items.stream().allMatch(item -> item.getAmount() > 0 && item.getAmount() <= 4 && item.getMaxStackSize() == 64))
            .findFirst().orElseThrow(() -> new AssertionError("No registered bounded multi-ingredient smeltery recipe"));
    }

    void create() {
        create(cellLocation, cell);
        create(controllerLocation, controller);
        create(targetLocation, machine);
    }

    boolean ready() {
        source = StorageCacheUtils.getMenu(cellLocation);
        target = StorageCacheUtils.getMenu(targetLocation);
        configuration = StorageCacheUtils.getMenu(pusherLocation);
        if (source == null || target == null || configuration == null) return false;
        for (Location location : List.of(cellLocation, controllerLocation, targetLocation, pusherLocation)) {
            var data = StorageCacheUtils.getBlock(location);
            if (data == null || !data.isDataLoaded()) return false;
        }
        tick(cell, cellLocation);
        tick(pusher, pusherLocation);
        if (NetworkStorage.getValidatedNode(cellLocation) == null
            || NetworkStorage.getValidatedNode(pusherLocation) == null) return false;
        tick(controller, controllerLocation);
        NetworkRoot root = NetworkController.getNetworks().get(controllerLocation);
        if (root == null || root.getNodeCount() != 3) return false;
        require(root.getClass() == NetworkRoot.class, "Native production controller root required");
        for (Location location : List.of(cellLocation, controllerLocation, pusherLocation)) {
            var definition = NetworkStorage.getValidatedNode(location);
            if (definition == null || definition.getNode() == null || definition.getNode().getRoot() != root) return false;
        }
        require(root.getCells().contains(cellLocation), "Real source cell must be discovered by adjacency");
        return true;
    }

    String run() {
        require(Bukkit.isPrimaryThread(), "Native pusher scenario must run on server thread");
        require(ready(), "Native transfer topology must already be ready");
        require("true".equals(StorageCacheUtils.getData(pusherLocation, "recipe-aware")), "Actual GUI must enable mode first");
        require("5".equals(StorageCacheUtils.getData(pusherLocation, "recipe-aware-buffer-batches")), "Actual GUI must set five batches first");
        for (int i = 0; i < source.getSize(); i++) require(empty(source.getItemInSlot(i)), "Source cell must start empty");
        for (int slot : machine.getInputSlots()) require(empty(target.getItemInSlot(slot)), "Smeltery input must start empty");
        // Full output is an ordinary machine state. It prevents the actual
        // smeltery from consuming a recipe while input buffering is measured.
        for (int slot : machine.getOutputSlots()) {
            require(empty(target.getItemInSlot(slot)), "Smeltery output must start empty");
            target.replaceExistingItem(slot, new ItemStack(Material.BARRIER, 64));
        }
        int[] templates = pusher.getItemSlots();
        require(templates.length >= ingredients.size(), "Recipe must fit the actual template layout");
        for (int slot : templates) require(empty(configuration.getItemInSlot(slot)), "Template slot must start empty");
        for (int i = 0; i < ingredients.size(); i++) {
            ItemStack ingredient = ingredients.get(i);
            configuration.replaceExistingItem(templates[i], ingredient.clone());
            for (int stack = 0; stack < STOCK / 64; stack++) {
                ItemStack item = ingredient.clone(); item.setAmount(64);
                source.replaceExistingItem(i * (STOCK / 64) + stack, item);
            }
        }
        ItemStack[] protectedTarget = snapshot(target);
        pusher.setDirection(configuration, BlockFace.EAST);
        for (int round = 0; round < 8; round++) {
            tick(pusher, pusherLocation);
            ledger(protectedTarget);
        }
        for (ItemStack ingredient : ingredients) {
            require(count(target, ingredient, machine.getInputSlots()) == ingredient.getAmount() * 5,
                "Recipe-aware native smeltery buffer expected five batches of " + label(ingredient)
                    + "; observed=" + count(target, ingredient, machine.getInputSlots()));
        }
        ItemStack[] sourceAtTarget = snapshot(source);
        ItemStack[] targetAtTarget = snapshot(target);
        for (int round = 0; round < 4; round++) tick(pusher, pusherLocation);
        require(Arrays.equals(sourceAtTarget, snapshot(source)) && Arrays.equals(targetAtTarget, snapshot(target)),
            "Satisfied recipe buffer must not repeatedly debit or refill");

        // Use the same persisted setting exercised by actual ON/OFF GUI clicks.
        // With mode OFF, each template returns to its ordinary stack allowance.
        StorageCacheUtils.setData(pusherLocation, "recipe-aware", "false");
        tick(pusher, pusherLocation);
        ledger(protectedTarget);
        for (ItemStack ingredient : ingredients) {
            require(count(target, ingredient, machine.getInputSlots()) == 64,
                "OFF must fill the actual matching input stack to its native capacity: " + label(ingredient));
        }
        StorageCacheUtils.setData(pusherLocation, "recipe-aware", "true");
        ItemStack[] sourceAfterOff = snapshot(source);
        ItemStack[] targetAfterOff = snapshot(target);
        for (int round = 0; round < 4; round++) tick(pusher, pusherLocation);
        ledger(protectedTarget);
        require(Arrays.equals(sourceAfterOff, snapshot(source)) && Arrays.equals(targetAfterOff, snapshot(target)),
            "Re-enabling recipe mode must preserve the larger existing OFF buffer without consuming stock");
        return "PASS: native recipe transfer tier=" + pusher.getClass().getSimpleName()
            + " machine=" + machine.getId() + " ingredients=" + ingredients.stream().map(NativeRecipeTransferProbe::label).toList()
            + " batches=5 off-capacity=64 exact-stock=" + STOCK + " topology=cell+controller+pusher";
    }

    void verifyRestart() {
        require(ready(), "Reloaded recipe transfer topology must be discovered");
        for (ItemStack ingredient : ingredients) {
            require(count(target, ingredient, machine.getInputSlots()) == 64, "Native target input failed restart");
            require(count(source, ingredient, allSlots(source)) == STOCK - 64, "Native source stock failed restart");
            require(count(configuration, ingredient, pusher.getItemSlots()) == ingredient.getAmount(), "Recipe template failed restart");
        }
        for (int slot : machine.getOutputSlots()) {
            require(new ItemStack(Material.BARRIER, 64).equals(target.getItemInSlot(slot)), "Output sentinel changed after restart");
        }
    }

    private void ledger(ItemStack[] protectedTarget) {
        for (ItemStack ingredient : ingredients) {
            require(count(source, ingredient, allSlots(source)) + count(target, ingredient, machine.getInputSlots()) == STOCK,
                "Native ingredient quantity not conserved for " + label(ingredient));
            require(count(configuration, ingredient, pusher.getItemSlots()) == ingredient.getAmount(), "Pusher template was consumed");
        }
        for (int slot = 0; slot < target.getSize(); slot++) {
            boolean input = false;
            for (int actual : machine.getInputSlots()) if (slot == actual) input = true;
            if (!input) require(same(protectedTarget[slot], target.getItemInSlot(slot)), "Transfer changed non-input slot " + slot);
        }
        for (int slot : machine.getInputSlots()) {
            ItemStack item = target.getItemInSlot(slot);
            require(empty(item) || ingredients.stream().anyMatch(template -> template.isSimilar(item)), "Wrong ingredient entered native machine");
        }
    }

    private static SlimefunItem registered(Class<?> type) {
        return Slimefun.getRegistry().getAllSlimefunItems().stream().filter(type::isInstance)
            .sorted(Comparator.comparing(SlimefunItem::getId)).findFirst().orElseThrow();
    }
    private static List<ItemStack> ingredients(MachineRecipe recipe) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : recipe.getInput()) {
            if (empty(item)) continue;
            ItemStack match = result.stream().filter(existing -> existing.isSimilar(item)).findFirst().orElse(null);
            if (match == null) result.add(item.clone()); else match.setAmount(match.getAmount() + item.getAmount());
        }
        return result;
    }
    private static String label(ItemStack item) {
        SlimefunItem registered = SlimefunItem.getByItem(item);
        return (registered == null ? item.getType().name() : registered.getId()) + "x" + item.getAmount();
    }
    private static void create(Location location, SlimefunItem item) {
        require(StorageCacheUtils.getBlock(location) == null, "Cannot overwrite existing fixture block");
        location.getChunk().load(); location.getBlock().setType(item.getItem().getType());
        var database = Slimefun.getDatabaseManager().getBlockDataController();
        var data = database.createBlock(location, item.getId());
        if (!data.isDataLoaded()) database.loadBlockData(data);
    }
    private static void tick(SlimefunItem item, Location location) {
        require(Bukkit.isPrimaryThread() && item.getBlockTicker().isSynchronized(), "Native network ticker must remain on main thread");
        item.getBlockTicker().tick(location.getBlock(), item, StorageCacheUtils.getBlock(location));
    }
    private static int[] allSlots(BlockMenu menu) { return java.util.stream.IntStream.range(0, menu.getSize()).toArray(); }
    private static int count(BlockMenu menu, ItemStack ingredient, int[] slots) {
        int count = 0;
        for (int slot : slots) {
            ItemStack item = menu.getItemInSlot(slot);
            if (!empty(item) && ingredient.isSimilar(item)) count += item.getAmount();
        }
        return count;
    }
    private static ItemStack[] snapshot(BlockMenu menu) {
        ItemStack[] result = new ItemStack[menu.getSize()];
        for (int slot = 0; slot < result.length; slot++) {
            ItemStack item = menu.getItemInSlot(slot); result[slot] = empty(item) ? null : item.clone();
        }
        return result;
    }
    private static boolean empty(ItemStack item) { return item == null || item.getType().isAir() || item.getAmount() <= 0; }
    private static boolean same(ItemStack left, ItemStack right) { return empty(left) ? empty(right) : left.equals(right); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}

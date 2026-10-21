package networksprobe;

import com.balugaq.netex.api.enums.TransportMode;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.balugaq.netex.utils.LineOperationUtil;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable-world only. Real IE2 production and menu, real Networks line caller, controlled sink. */
public final class InfinityDustTransferProbe extends JavaPlugin {
    private int cases;

    @Override
    public void onEnable() {
        Bukkit.getScheduler().runTask(this, () -> {
            String result;
            try {
                verify();
                result = "PASS: " + cases + " real IE2 dust-production/Networks transfer cases; controlled sink";
            } catch (Throwable failure) {
                result = "FAIL: " + failure;
                failure.printStackTrace();
            }
            getLogger().info(result);
            try {
                Files.writeString(getDataFolder().toPath().resolveSibling("infinity-dust-result.txt"), result + "\n");
            } catch (Exception failure) {
                throw new RuntimeException(failure);
            } finally {
                Bukkit.shutdown();
            }
        });
    }

    private void verify() throws Exception {
        require(Bukkit.getIp().equals("127.0.0.1"), "Only run on a loopback disposable server");
        require(Bukkit.getWorlds().getFirst().getName().equals("fixture"), "Disposable fixture world required");
        var plugin = Bukkit.getPluginManager().getPlugin("InfinityExpansion2");
        require(plugin != null && plugin.isEnabled(), "Real InfinityExpansion2 must be enabled");
        var machines = Slimefun.getRegistry().getAllSlimefunItems().stream()
            .filter(item -> item.getClass().getName().endsWith(".DustExtractor"))
            .sorted(java.util.Comparator.comparing(SlimefunItem::getId)).toList();
        require(!machines.isEmpty(), "No registered InfinityExpansion2 dust extractors");
        getLogger().info("EXTRACTORS: " + machines.stream().map(SlimefunItem::getId).toList());
        int index = 0;
        for (var machine : machines) {
            var ticker = machine.getBlockTicker();
            require(ticker != null, "Dust Extractor has no registered BlockTicker: " + machine.getId());
            require(ticker.isSynchronized(), "DUST_EXTRACTOR_TICKER_ASYNC: " + machine.getId());
            getLogger().info("TICKER SYNC PASS: " + machine.getId());

            var location = new Location(Bukkit.getWorlds().getFirst(), index++ * 2, 100, 0);
            location.getWorld().getChunkAt(location).load();
            location.getBlock().setType(Material.HOPPER);
            var preset = BlockMenuPreset.getPreset(machine.getId());
            require(preset != null, "Real extractor menu preset missing: " + machine.getId());
            var menu = new BlockMenu(preset, location);
            int[] inputs = BlockMenuUtil.getSafeTransportSlots(menu, ItemTransportFlow.INSERT,
                ItemStack.of(Material.COBBLESTONE, 64));
            int[] outputs = BlockMenuUtil.getSafeTransportSlots(menu, ItemTransportFlow.WITHDRAW);
            getLogger().info("LAYOUT " + machine.getId() + " input=" + Arrays.toString(inputs)
                + " output=" + Arrays.toString(outputs));
            require(inputs.length > 0 && outputs.length > 1, "Expected extractor transport layout");
            Method process = findProcess(machine.getClass());
            var fixture = new Fixture(machine, menu, inputs, outputs, process);
            // Retain the published regression against destructive cleanup of changed identities.
            for (String mutation : List.of("metadata", "replacement", "empty")) {
                changedSource(fixture, mutation);
            }
            for (TransportMode mode : List.of(TransportMode.NONE, TransportMode.VOID)) {
                producerGrowth(fixture, mode, false);
                producerGrowth(fixture, mode, true);
            }
            clean(fixture, TransportMode.NONE, false, false, false);
            clean(fixture, TransportMode.NONE, true, true, false);
            clean(fixture, TransportMode.NONE, true, false, false);
            clean(fixture, TransportMode.VOID, false, false, true);
            clean(fixture, TransportMode.VOID, true, true, false);
            clean(fixture, TransportMode.VOID, true, false, false);
            throwingOffer(fixture);
            for (int cycle = 0; cycle < 24; cycle++) {
                clean(fixture, TransportMode.NONE, cycle % 2 == 0, cycle % 3 == 0, false);
            }
        }
        getLogger().info("SCOPE: checks the actual registered extractor BlockTicker synchronization, invokes the "
            + "actual extractor process method and actual transport slots, and exercises the real Networks line "
            + "caller with a controlled sink; a physical destination network is not simulated");
    }

    private static Method findProcess(Class<?> type) throws Exception {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Method result = current.getDeclaredMethod("process", Block.class, BlockMenu.class);
                require(result.trySetAccessible(), "Cannot access actual extractor process method");
                return result;
            } catch (NoSuchMethodException ignored) {
                // The implementation is inherited from RandomHopperMachine.
            }
        }
        throw new NoSuchMethodException("Extractor process method");
    }

    private record Fixture(SlimefunItem machine, BlockMenu menu, int[] inputs, int[] outputs, Method process) { }
    private record Produced(int slot, ItemStack original, int inputLeft) { }

    private Produced produce(Fixture fixture, boolean decorate) throws Exception {
        for (int slot : fixture.inputs()) fixture.menu().replaceExistingItem(slot, null);
        for (int slot : fixture.outputs()) fixture.menu().replaceExistingItem(slot, null);
        fixture.menu().replaceExistingItem(fixture.inputs()[0], ItemStack.of(Material.COBBLESTONE, 64));
        require(Boolean.TRUE.equals(fixture.process().invoke(fixture.machine(), fixture.menu().getLocation().getBlock(),
            fixture.menu())), "Actual dust production failed: " + fixture.machine().getId());
        ItemStack input = fixture.menu().getItemInSlot(fixture.inputs()[0]);
        int inputLeft = amount(input);
        require(inputLeft < 64, "Extractor did not consume its real recipe input");
        int slot = Arrays.stream(fixture.outputs()).filter(s -> amount(fixture.menu().getItemInSlot(s)) > 0)
            .findFirst().orElseThrow(() -> new AssertionError("Extractor produced no dust"));
        ItemStack source = fixture.menu().getItemInSlot(slot);
        var identified = SlimefunItem.getByItem(source);
        require(identified != null && identified.getId().endsWith("_DUST"), "Output is not identified Slimefun dust");
        if (decorate) {
            source.editMeta(meta -> {
                var model = meta.getCustomModelDataComponent();
                model.setFloats(List.of(9001.5F));
                model.setStrings(List.of("existing-extractor-dust"));
                model.setFlags(List.of(true, false));
                model.setColors(List.of(org.bukkit.Color.fromRGB(123, 45, 67)));
                meta.setCustomModelDataComponent(model);
                meta.getPersistentDataContainer().set(new NamespacedKey("dustprobe", "opaque"),
                    PersistentDataType.BYTE_ARRAY, new byte[]{4, 1, 74});
                meta.getPersistentDataContainer().set(new NamespacedKey("dustprobe", "owner"),
                    PersistentDataType.STRING, "original");
            });
            fixture.menu().replaceExistingItem(slot, source);
        }
        ItemStack actual = fixture.menu().getItemInSlot(slot);
        getLogger().info("PRODUCED " + fixture.machine().getId() + " " + identified.getId() + " x" + actual.getAmount()
            + " class=" + actual.getClass().getName() + " decorated=" + decorate
            + " cloneEqual=" + actual.equals(actual.clone()));
        return new Produced(slot, exact(actual), inputLeft);
    }

    private void clean(Fixture fixture, TransportMode mode, boolean decorate, boolean partial, boolean zero)
        throws Exception {
        Produced produced = produce(fixture, decorate);
        int original = produced.original().getAmount();
        int capacity = zero ? 0 : partial ? original / 2 : original;
        var root = new Sink(fixture.menu().getLocation(), produced.original(), capacity);
        LineOperationUtil.grabItem(fixture.menu().getLocation(), root, fixture.menu(), mode, 64);
        int expectedRemaining = mode == TransportMode.VOID ? 0 : original - capacity;
        checkStack(produced.original(), fixture.menu().getItemInSlot(produced.slot()), expectedRemaining,
            "Clean transfer remainder");
        require(root.accepted == capacity, "Clean transfer accepted count");
        require(root.compensated == 0, "Unchanged extractor source falsely rejected");
        require(amount(fixture.menu().getItemInSlot(fixture.inputs()[0])) == produced.inputLeft(),
            "Transport touched extractor recipe input");
        pass(fixture, "clean " + mode + " accepted=" + capacity + " source=" + expectedRemaining);
    }

    private void producerGrowth(Fixture fixture, TransportMode mode, boolean decorate) throws Exception {
        Produced produced = produce(fixture, decorate);
        int original = produced.original().getAmount();
        int accepted = Math.min(4, original);
        ItemStack grown = exact(produced.original());
        grown.setAmount(original + 1);
        var root = new Sink(fixture.menu().getLocation(), produced.original(), accepted);
        root.callback = () -> fixture.menu().replaceExistingItem(produced.slot(), grown);
        LineOperationUtil.grabItem(fixture.menu().getLocation(), root, fixture.menu(), mode, 64);
        int remaining = mode == TransportMode.VOID ? 1 : original + 1 - accepted;
        checkStack(produced.original(), fixture.menu().getItemInSlot(produced.slot()), remaining,
            "New producer stock must survive the original transfer and its VOID budget");
        require(root.offers == 1 && root.accepted == accepted && root.compensated == 0,
            "Growth with the same exact identity must commit only the original accepted quantity");
        int intentionallyVoided = mode == TransportMode.VOID ? original - accepted : 0;
        require(root.accepted + remaining + intentionallyVoided == original + 1,
            "Producer-growth ledger must account for every accepted, retained and intentionally voided item");
        require(amount(fixture.menu().getItemInSlot(fixture.inputs()[0])) == produced.inputLeft(),
            "Transport touched extractor recipe input");
        pass(fixture, "producer growth " + mode + " decorated=" + decorate
            + " accepted=" + accepted + " retained=" + remaining + " intentionalVoid=" + intentionallyVoided);
    }

    private void changedSource(Fixture fixture, String mutation) throws Exception {
        Produced produced = produce(fixture, false);
        int next = Arrays.stream(fixture.outputs()).filter(s -> s != produced.slot()).findFirst().orElseThrow();
        ItemStack sentinel = ItemStack.of(Material.NETHER_STAR, 3);
        fixture.menu().replaceExistingItem(next, sentinel);
        ItemStack changed = exact(produced.original());
        if (mutation.equals("metadata")) changed.editMeta(meta -> meta.getPersistentDataContainer().set(
            new NamespacedKey("dustprobe", "owner"), PersistentDataType.STRING, "external-replacement"));
        if (mutation.equals("replacement")) changed = ItemStack.of(Material.EMERALD, 5);
        if (mutation.equals("empty")) changed = null;
        final ItemStack external = changed;
        final ItemStack expected = external == null ? null : exact(external);
        var root = new Sink(fixture.menu().getLocation(), produced.original(), Math.min(4, produced.original().getAmount()));
        root.callback = () -> fixture.menu().replaceExistingItem(produced.slot(), external);
        try {
            LineOperationUtil.grabItem(fixture.menu().getLocation(), root, fixture.menu(), TransportMode.VOID, 64);
        } catch (AssertionError downstreamFailure) {
            // The old caller clears the source and then visits the sentinel, which can fail
            // the sink's identity check before we return. Inspect the original slot first,
            // retaining every failure rather than letting that later assertion mask deletion.
            try {
                checkStack(expected, fixture.menu().getItemInSlot(produced.slot()), amount(expected),
                    "SOURCE_CHANGED_WAS_CLEARED: " + mutation);
            } catch (AssertionError sourceFailure) {
                sourceFailure.addSuppressed(downstreamFailure);
                throw sourceFailure;
            }
            throw downstreamFailure;
        }
        checkStack(expected, fixture.menu().getItemInSlot(produced.slot()), amount(expected),
            "SOURCE_CHANGED_WAS_CLEARED: " + mutation);
        checkStack(sentinel, fixture.menu().getItemInSlot(next), 3, "Failure must stop before the next output slot");
        require(root.offers == 1, "Failure did not stop the actual line caller");
        require(root.accepted == 0 && root.compensated == root.totalAccepted, "Accepted dust not fully compensated");
        pass(fixture, "changed source " + mutation + " compensated=" + root.compensated);
    }

    private void throwingOffer(Fixture fixture) throws Exception {
        Produced produced = produce(fixture, true);
        int capacity = produced.original().getAmount() / 2;
        var root = new Sink(fixture.menu().getLocation(), produced.original(), capacity);
        root.callback = () -> { throw new IllegalStateException("Injected partial-deposit failure"); };
        LineOperationUtil.grabItem(fixture.menu().getLocation(), root, fixture.menu(), TransportMode.VOID, 64);
        checkStack(produced.original(), fixture.menu().getItemInSlot(produced.slot()),
            produced.original().getAmount() - capacity, "Throwing offer must not void the remainder");
        require(root.accepted + amount(fixture.menu().getItemInSlot(produced.slot())) == produced.original().getAmount(),
            "Throwing offer conservation");
        pass(fixture, "partial throwing offer");
    }

    private void pass(Fixture fixture, String label) {
        cases++;
        getLogger().info("CASE PASS: " + fixture.machine().getId() + " " + label);
    }

    private static ItemStack exact(ItemStack source) {
        ItemStack result = ItemStack.of(source.getType(), source.getAmount());
        require(result.setItemMeta(source.getItemMeta()) && result.equals(source), "Fixture snapshot lost item data");
        return result;
    }

    private static void checkStack(ItemStack template, ItemStack actual, int count, String label) {
        require(amount(actual) == count, label + " count expected=" + count + " actual=" + amount(actual));
        if (count > 0) require(template.isSimilar(actual), label + " metadata");
    }

    private static int amount(ItemStack item) {
        return item == null || item.getType().isAir() ? 0 : item.getAmount();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    /** Explicit controlled destination boundary: acceptance and callback faults, not physical network discovery. */
    private static final class Sink extends NetworkRoot {
        private final ItemStack expected;
        private final int capacity;
        private int accepted;
        private int totalAccepted;
        private int compensated;
        private int offers;
        private Runnable callback = () -> { };

        Sink(Location source, ItemStack expected, int capacity) {
            super(source.clone().add(0, 0, 2), NodeType.CONTROLLER, 16);
            this.expected = exact(expected);
            this.capacity = capacity;
        }

        @Override public boolean allowAccessInput(Location accessor) { return true; }
        @Override public void uncontrolAccessOutput(Location accessor) { }
        @Override public void addItemStack0(Location accessor, ItemStack offered) {
            offers++;
            require(expected.isSimilar(offered), "Offered dust identity or metadata changed");
            int taken = Math.min(Math.max(0, capacity - accepted), offered.getAmount());
            accepted += taken;
            totalAccepted += taken;
            offered.setAmount(offered.getAmount() - taken);
            callback.run();
        }
        @Override public ItemStack getItemStack0(Location accessor, ItemRequest request) {
            int recovered = Math.min(accepted, request.getAmount());
            if (recovered == 0) return null;
            accepted -= recovered;
            compensated += recovered;
            ItemStack result = exact(expected);
            result.setAmount(recovered);
            return result;
        }
    }
}


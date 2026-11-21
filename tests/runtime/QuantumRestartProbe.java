package networksprobe;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import com.ytdd9527.networksexpansion.implementation.machines.manual.ItemDifferenter;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
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
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionException;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.tag.DamageTypeTags;

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
                verifyNativeFireResistance(phase);
                verifyNativeNegativeDamageResistance(phase);
                verifyNativeMapTint(phase);
                verifyCrossVersionMapTint(phase);
                verifyNativeVanillaCustomModels(phase);
                verifyNativeHistoricalNameIdentity(phase);
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

    /**
     * Real Paper/Purpur ItemStack metadata test, not a MockBukkit approximation.
     * Keep the legacy IS_FIRE predicate exactly as used by ItemDifferenter;
     * the modern API must not replace it until equivalent saved-item behavior
     * is demonstrated on both supported server families.
     *
     * The seed JVM writes Bukkit's binary ItemStack representation. The verify
     * JVM reads those exact bytes after a clean server restart. A positive PDC
     * sentinel must survive each step, while the server's observed post-copy
     * fire-resistance state is recorded and checked for restart parity.
     */
    @SuppressWarnings("deprecation")
    private void verifyNativeFireResistance(String phase) throws Exception {
        final Path fixture = getDataFolder().toPath().getParent()
            .resolve("native-fire-resistance-item.bin");
        final ItemStack original = new ItemStack(Material.NETHERITE_SWORD);
        final ItemMeta meta = original.getItemMeta();
        require(meta != null, "Native sword must expose ItemMeta");
        meta.setDamageResistant(DamageTypeTags.IS_FIRE);
        meta.getPersistentDataContainer().set(ownerKey(), PersistentDataType.STRING, "native-fire-metadata");
        require(hasFireResistance(meta), "Explicit native fire tag was not retained by ItemMeta");
        require(productionIsFireResistant(meta),
            "Production ItemDifferenter must classify the original explicit native fire tag");
        require(original.setItemMeta(meta), "Native ItemStack refused fire-resistant metadata");
        // Paper/Purpur may discard this tag when copying ItemMeta into ItemStack.
        // Record that server behavior rather than asserting a positive tag survives.
        // Never interpret this fixture as proof of positive-tag persistence.
        requireNativeSentinel(original, "metadata copy");
        final boolean retained = hasFireResistance(original.getItemMeta());
        require(productionIsFireResistant(original.getItemMeta()) == retained,
            "Production fire classifier disagrees with native post-copy metadata");
        final String copiedState = describeFireResistance(original.getItemMeta());
        final byte[] bytes = original.serializeAsBytes();
        requireNativeSentinel(original.clone(), "clone");
        requireNativeSentinel(ItemStack.deserializeBytes(bytes), "binary roundtrip");
        require(hasFireResistance(original.clone().getItemMeta()) == retained,
            "Native clone changed the server's post-copy resistance state");
        require(hasFireResistance(ItemStack.deserializeBytes(bytes).getItemMeta()) == retained,
            "Native binary roundtrip changed the server's post-copy resistance state");
        final Path stateFile = getDataFolder().toPath().getParent()
            .resolve("native-fire-resistance-retained.txt");
        if ("seed".equals(phase)) {
            Files.write(fixture, bytes);
            Files.writeString(stateFile, Boolean.toString(retained), StandardCharsets.UTF_8);
        } else if ("verify".equals(phase)) {
            require(Files.isRegularFile(fixture) && Files.isRegularFile(stateFile),
                "Native resistance fixture or expected server state missing after JVM restart");
            final boolean expected = Boolean.parseBoolean(
                Files.readString(stateFile, StandardCharsets.UTF_8).trim());
            require(expected == retained,
                "Server post-copy fire resistance changed across JVM restart");
            requireNativeSentinel(ItemStack.deserializeBytes(Files.readAllBytes(fixture)), "separate JVM restart");
            require(hasFireResistance(ItemStack.deserializeBytes(Files.readAllBytes(fixture)).getItemMeta()) == expected,
                "Native fire resistance state changed across persisted bytes and JVM restart");
        } else {
            throw new AssertionError("Unknown native fire tag phase: " + phase);
        }
        getLogger().info("NATIVE FIRE TAG OBSERVED phase=" + phase
            + " retainedAfterCopy=" + retained + " state=" + copiedState
            + " cloneBinaryRestartParity=true");
    }

    /**
     * A native negative control for issue #122. The old ItemDifferenter
     * predicate must distinguish explicit IS_FIRE from IS_EXPLOSION and from
     * absent damage resistance, including when native ItemStack metadata
     * copying discards a tag. Compare the ORIGINAL production predicate, not
     * a substitute accessor that could accidentally broaden item matching.
     *
     * These fixtures and their typed PDC sentinels persist into the second
     * clean server JVM. No live inventories or player worlds are involved.
     */
    @SuppressWarnings("deprecation")
    private void verifyNativeNegativeDamageResistance(String phase) throws Exception {
        final Path directory = getDataFolder().toPath().getParent();
        final Path nonFireFixture = directory.resolve("native-nonfire-resistance-item.bin");
        final Path absentFixture = directory.resolve("native-absent-resistance-item.bin");
        final Path stateFile = directory.resolve("native-negative-resistance-observed.txt");

        final ItemStack nonFire = new ItemStack(Material.NETHERITE_SWORD);
        final ItemMeta nonFireMeta = nonFire.getItemMeta();
        require(nonFireMeta != null, "Native non-fire control must expose ItemMeta");
        nonFireMeta.setDamageResistant(DamageTypeTags.IS_EXPLOSION);
        nonFireMeta.getPersistentDataContainer().set(
            ownerKey(), PersistentDataType.STRING, "native-negative-resistance");
        require(nonFireMeta.hasDamageResistant(),
            "Explicit native explosion tag must exist on source ItemMeta");
        require(!productionIsFireResistant(nonFireMeta),
            "ItemDifferenter incorrectly classified source explosion resistance as fire");
        require(nonFire.setItemMeta(nonFireMeta), "Native non-fire ItemMeta copy was rejected");

        final ItemStack absent = new ItemStack(Material.NETHERITE_SWORD);
        final ItemMeta absentMeta = absent.getItemMeta();
        require(absentMeta != null, "Native absent control must expose ItemMeta");
        absentMeta.getPersistentDataContainer().set(
            ownerKey(), PersistentDataType.STRING, "native-negative-resistance");
        require(!absentMeta.hasDamageResistant(), "Unmodified source metadata has unexpected resistance");
        require(absent.setItemMeta(absentMeta), "Native absent ItemMeta copy was rejected");

        final byte[] nonFireBytes = nonFire.serializeAsBytes();
        final byte[] absentBytes = absent.serializeAsBytes();
        requireNegativeDamageControl(nonFire, "non-fire copy");
        requireNegativeDamageControl(absent, "absent copy");
        requireNegativeDamageControl(nonFire.clone(), "non-fire clone");
        requireNegativeDamageControl(absent.clone(), "absent clone");
        requireNegativeDamageControl(ItemStack.deserializeBytes(nonFireBytes), "non-fire binary");
        requireNegativeDamageControl(ItemStack.deserializeBytes(absentBytes), "absent binary");

        // Exact existing comparison semantics: neither non-fire nor absent
        // resistance may be reported as a fire-only mismatch.
        require(!"neq.fireresistant".equals(ItemDifferenter.calc(nonFire, absent)),
            "ItemDifferenter incorrectly distinguished non-fire vs absent as fire");
        require(!"neq.fireresistant".equals(ItemDifferenter.calc(absent, nonFire)),
            "ItemDifferenter negative-control predicate was asymmetric");

        final String observed = describeFireResistance(nonFire.getItemMeta()) + "\n"
            + describeFireResistance(absent.getItemMeta());
        if ("seed".equals(phase)) {
            Files.write(nonFireFixture, nonFireBytes);
            Files.write(absentFixture, absentBytes);
            Files.writeString(stateFile, observed, StandardCharsets.UTF_8);
        } else if ("verify".equals(phase)) {
            require(Files.isRegularFile(nonFireFixture) && Files.isRegularFile(absentFixture)
                    && Files.isRegularFile(stateFile),
                "Native negative-resistance fixtures missing after independent JVM restart");
            require(observed.equals(Files.readString(stateFile, StandardCharsets.UTF_8)),
                "Native negative-resistance metadata changed across separate JVM startup");
            final ItemStack restoredNonFire = ItemStack.deserializeBytes(Files.readAllBytes(nonFireFixture));
            final ItemStack restoredAbsent = ItemStack.deserializeBytes(Files.readAllBytes(absentFixture));
            requireNegativeDamageControl(restoredNonFire, "non-fire restart");
            requireNegativeDamageControl(restoredAbsent, "absent restart");
            require(observed.equals(describeFireResistance(restoredNonFire.getItemMeta())
                    + "\n" + describeFireResistance(restoredAbsent.getItemMeta())),
                "Native negative-resistance bytes changed after independent JVM restart");
        } else {
            throw new AssertionError("Unknown native negative resistance phase: " + phase);
        }
        getLogger().info("NATIVE NEGATIVE FIRE TAG phase=" + phase
            + " nonFireState=" + describeFireResistance(nonFire.getItemMeta())
            + " absentState=" + describeFireResistance(absent.getItemMeta())
            + " nativePredicateAndRestartParity=true");
    }

    private void requireNegativeDamageControl(ItemStack item, String stage) throws Exception {
        final ItemMeta meta = item.getItemMeta();
        require(item.getAmount() == 1 && meta != null, "Negative resistance stack lost amount/meta: " + stage);
        require("native-negative-resistance".equals(meta.getPersistentDataContainer()
                .get(ownerKey(), PersistentDataType.STRING)),
            "Native negative resistance lost typed PDC: " + stage);
        require(!productionIsFireResistant(meta),
            "Actual ItemDifferenter misclassified non-fire/absent resistance: " + stage);
    }

    private static boolean productionIsFireResistant(ItemMeta meta) throws Exception {
        final Method method = ItemDifferenter.class.getDeclaredMethod("isFireResistant", ItemMeta.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(null, meta);
    }

    /**
     * Check the four vanilla CustomModelDataComponent channels using real Paper
     * item metadata. This protects ItemDifferenter.calc's non-Slimefun branch
     * beyond mocked JUnit calls. Each supported runtime uses its own seed and
     * verify JVM pair; this does not claim cross-version item migration.
     */
    private void verifyNativeVanillaCustomModels(String phase) throws Exception {
        final Path fixture = getDataFolder().toPath().getParent()
            .resolve("native-vanilla-custom-model-item.bin");
        final ItemStack original = new ItemStack(Material.DIAMOND);
        final ItemMeta metadata = original.getItemMeta();
        require(metadata != null, "Vanilla diamond must expose ItemMeta");
        final CustomModelDataComponent model = metadata.getCustomModelDataComponent();
        model.setFloats(List.of(1.25f));
        model.setFlags(List.of(true));
        model.setStrings(List.of("networks:vanilla_model"));
        model.setColors(List.of(Color.RED));
        metadata.setCustomModelDataComponent(model);
        metadata.getPersistentDataContainer().set(ownerKey(), PersistentDataType.STRING, "native-vanilla-model");
        require(original.setItemMeta(metadata), "Could not attach vanilla custom model to ItemStack");

        requireNativeVanillaModel(original, "setItemMeta");
        final ItemStack cloned = original.clone();
        final byte[] bytes = original.serializeAsBytes();
        final ItemStack decoded = ItemStack.deserializeBytes(bytes);
        requireNativeVanillaModel(cloned, "clone");
        requireNativeVanillaModel(decoded, "binary roundtrip");
        require(sameNativeCustomModels(original, cloned), "Vanilla model changed during clone");
        require(sameNativeCustomModels(original, decoded), "Vanilla model changed during binary roundtrip");
        require("eq".equals(ItemDifferenter.calc(original, cloned)),
            "Identical cloned vanilla custom model must compare equal");
        require("eq".equals(ItemDifferenter.calc(original, decoded)),
            "Identical serialized vanilla custom model must compare equal");

        for (int channel = 0; channel < 4; channel++) {
            final ItemStack changed = original.clone();
            final ItemMeta changedMeta = changed.getItemMeta();
            require(changedMeta != null, "Model mutation lost ItemMeta");
            final CustomModelDataComponent changedModel = changedMeta.getCustomModelDataComponent();
            switch (channel) {
                case 0 -> changedModel.setFloats(List.of(2.25f));
                case 1 -> changedModel.setFlags(List.of(false));
                case 2 -> changedModel.setStrings(List.of("networks:other_model"));
                case 3 -> changedModel.setColors(List.of(Color.BLUE));
                default -> throw new AssertionError("Unknown vanilla model channel " + channel);
            }
            changedMeta.setCustomModelDataComponent(changedModel);
            require(changed.setItemMeta(changedMeta), "Native custom model mutation was rejected");
            require(!sameNativeCustomModels(original, changed),
                "Native custom model channel " + channel + " did not retain its mutation");
            require("neq.custommodeldata".equals(ItemDifferenter.calc(original, changed)),
                "Item Differenter missed native custom model channel " + channel);
            require("neq.custommodeldata".equals(ItemDifferenter.calc(changed, original)),
                "Item Differenter custom model comparison was asymmetric for channel " + channel);
        }

        if ("seed".equals(phase)) {
            Files.write(fixture, bytes);
        } else if ("verify".equals(phase)) {
            require(Files.isRegularFile(fixture), "Vanilla custom model fixture missing after JVM restart");
            final ItemStack restored = ItemStack.deserializeBytes(Files.readAllBytes(fixture));
            requireNativeVanillaModel(restored, "separate JVM restart");
            require(sameNativeCustomModels(original, restored),
                "Vanilla custom model changed across persisted bytes and a separate JVM");
            require("eq".equals(ItemDifferenter.calc(original, restored)),
                "Restored vanilla custom model must compare equal after separate JVM restart");
        } else {
            throw new AssertionError("Unknown vanilla custom model phase: " + phase);
        }
        getLogger().info("NATIVE VANILLA CUSTOM MODEL phase=" + phase
            + " fourChannels=4 cloneBinaryRestartParity=true itemDifferenterSymmetry=true");
    }

    private void requireNativeVanillaModel(ItemStack item, String stage) {
        final ItemMeta meta = item.getItemMeta();
        require(meta != null, "Native custom model missing ItemMeta during " + stage);
        require("native-vanilla-model".equals(meta.getPersistentDataContainer()
            .get(ownerKey(), PersistentDataType.STRING)),
            "Native custom model PDC sentinel missing during " + stage);
        require(meta.hasCustomModelDataComponent(), "Native custom model disappeared during " + stage);
        final CustomModelDataComponent component = meta.getCustomModelDataComponent();
        require(component.getFloats().equals(List.of(1.25f))
            && component.getFlags().equals(List.of(true))
            && component.getStrings().equals(List.of("networks:vanilla_model"))
            && component.getColors().equals(List.of(Color.RED)),
            "Native vanilla custom model values changed during " + stage);
    }

    private static boolean sameNativeCustomModels(ItemStack first, ItemStack second) {
        final ItemMeta firstMeta = first.getItemMeta();
        final ItemMeta secondMeta = second.getItemMeta();
        if (firstMeta == null || secondMeta == null
                || !firstMeta.hasCustomModelDataComponent()
                || !secondMeta.hasCustomModelDataComponent()) {
            return false;
        }
        final CustomModelDataComponent a = firstMeta.getCustomModelDataComponent();
        final CustomModelDataComponent b = secondMeta.getCustomModelDataComponent();
        return a.getFloats().equals(b.getFloats())
            && a.getFlags().equals(b.getFlags())
            && a.getStrings().equals(b.getStrings())
            && a.getColors().equals(b.getColors());
    }

    /**
     * Native historical-map-color compatibility probe on the actual server API.
     * 1.21.11 exposes a functional map tint; 26.3 documents a no-op accessor.
     * Preserve both observations rather than pretending 26.3 supports tint.
     * The same bytes are loaded by a separate server JVM in the verify phase.
     */
    @SuppressWarnings("deprecation")
    private void verifyNativeMapTint(String phase) throws Exception {
        final Path directory = getDataFolder().toPath().getParent();
        final Path fixture = directory.resolve("native-map-tint-item.bin");
        final Path stateFile = directory.resolve("native-map-tint-observed.txt");
        final ItemStack original = new ItemStack(Material.FILLED_MAP);
        final ItemMeta base = original.getItemMeta();
        require(base instanceof MapMeta, "Native filled map must expose MapMeta");
        final MapMeta map = (MapMeta) base;
        map.setColor(Color.RED);
        map.getPersistentDataContainer().set(ownerKey(), PersistentDataType.STRING, "native-map-tint");
        map.getPersistentDataContainer().set(new NamespacedKey("networkstest", "map_ledger"),
            PersistentDataType.LONG, 7311123456L);
        map.getPersistentDataContainer().set(new NamespacedKey("networkstest", "map_payload"),
            PersistentDataType.BYTE_ARRAY, new byte[]{-1, 0, 42});
        final boolean sourceHasTint = Bukkit.getMinecraftVersion().equals("1.21.11")
            && map.hasColor() && Color.RED.equals(map.getColor());
        if (Bukkit.getMinecraftVersion().equals("1.21.11")) {
            require(sourceHasTint, "Paper/Purpur 1.21.11 source MapMeta lost explicit red tint");
        }
        require(original.setItemMeta(map), "Native ItemStack refused map metadata");
        requireNativeMapSentinel(original, "metadata copy");
        final String observed = nativeMapTint(original);
        final ItemStack cloned = original.clone();
        final byte[] bytes = original.serializeAsBytes();
        final ItemStack decoded = ItemStack.deserializeBytes(bytes);
        requireNativeMapSentinel(cloned, "clone");
        requireNativeMapSentinel(decoded, "binary roundtrip");
        require(observed.equals(nativeMapTint(cloned)), "Map tint changed during native clone");
        require(observed.equals(nativeMapTint(decoded)), "Map tint changed during binary roundtrip");
        if ("seed".equals(phase)) {
            Files.write(fixture, bytes);
            Files.writeString(stateFile, observed, StandardCharsets.UTF_8);
        } else if ("verify".equals(phase)) {
            require(Files.isRegularFile(fixture) && Files.isRegularFile(stateFile),
                "Native map tint fixture or expected state missing after JVM restart");
            final String expected = Files.readString(stateFile, StandardCharsets.UTF_8).trim();
            require(expected.equals(observed), "Native map tint copy state changed across JVM restart");
            final ItemStack restored = ItemStack.deserializeBytes(Files.readAllBytes(fixture));
            requireNativeMapSentinel(restored, "separate JVM restart");
            require(expected.equals(nativeMapTint(restored)),
                "Native map tint changed across persisted bytes and separate JVM restart");
        } else {
            throw new AssertionError("Unknown native map tint phase: " + phase);
        }
        getLogger().info("NATIVE MAP TINT OBSERVED phase=" + phase
            + " runtime=" + Bukkit.getMinecraftVersion()
            + " sourceHasRedTint=" + sourceHasTint
            + " retainedAfterCopy=" + observed
            + " cloneBinaryRestartParity=true");
    }

    /**
     * Native cross-version diagnostic: load the REAL Purpur 1.21.11 source
     * ItemStack bytes in a separate Paper 26.3 JVM. Never migrate the old
     * source file or assume the removed map-tint API can preserve red color.
     * Typed owner/ledger/payload and physical quantity MUST remain intact.
     */
    private void verifyCrossVersionMapTint(String phase) throws Exception {
        if (!"26.3".equals(Bukkit.getMinecraftVersion())) return;
        final Path directory = getDataFolder().toPath().getParent();
        final Path historicalFile = directory.resolve("native-map-tint-from-1.21.11.bin");
        require(Files.isRegularFile(historicalFile),
            "Pinned Purpur 1.21.11 historical map bytes missing from native Paper 26.3 test");
        final byte[] sourceBytes = Files.readAllBytes(historicalFile);
        require(sourceBytes.length > 0 && sourceBytes.length <= 1_048_576,
            "Historical map fixture is empty or unexpectedly large");

        final ItemStack historical = ItemStack.deserializeBytes(sourceBytes);
        require(historical.getType() == Material.FILLED_MAP && historical.getAmount() == 1,
            "Paper 26.3 cross-version deserialize changed the saved map item type or quantity");
        requireNativeMapSentinel(historical, "Purpur 1.21.11 -> Paper 26.3 deserialize");
        final ItemStack copy = historical.clone();
        requireNativeMapSentinel(copy, "cross-version native clone");
        require(copy.isSimilar(historical), "Cross-version map no longer matches its own clone");
        final byte[] upgradedSnapshot = historical.serializeAsBytes();
        final String color = nativeMapTint(historical);
        final String first = ItemDifferenter.calc(historical, copy);
        final String second = ItemDifferenter.calc(copy, historical);
        require(Objects.equals(first, second),
            "Cross-version native Item Differenter is asymmetric");
        require(Arrays.equals(sourceBytes, Files.readAllBytes(historicalFile))
                    && Arrays.equals(upgradedSnapshot, historical.serializeAsBytes()),
            "Read-only cross-version diagnostics changed saved bytes or decoded item");
        final Path observation = directory.resolve("native-cross-version-map-tint-observed.txt");
        if ("seed".equals(phase)) {
            Files.writeString(observation, color, StandardCharsets.UTF_8);
        } else if ("verify".equals(phase)) {
            require(Files.isRegularFile(observation), "Cross-version tint seed receipt missing");
            require(color.equals(Files.readString(observation, StandardCharsets.UTF_8).trim()),
                "Cross-version map tint interpretation changed across Paper 26.3 JVM restart");
        } else {
            throw new AssertionError("Unknown cross-version map diagnostic phase " + phase);
        }
        getLogger().info("NATIVE CROSS-VERSION MAP TINT phase=" + phase
            + " source=Purpur-1.21.11 destination=Paper-26.3"
            + " observedTint=" + color
            + " itemQuantity=1 typedOwnerLedgerPayloadPreserved=true"
            + " sourceBytesUnchanged=true cloneAndSeparateJvmParity=true"
            + " noHistoricalItemRewrite=true");
    }

    @SuppressWarnings("deprecation")
    private static String nativeMapTint(ItemStack item) {
        final ItemMeta meta = item.getItemMeta();
        // Paper 26.3 can throw when querying map color without an associated MapView.
        // The deprecated tint accessor is not a cross-version compatibility oracle.
        if ("26.3".equals(Bukkit.getMinecraftVersion())) return "unsupported-26.3";
        if (!(meta instanceof MapMeta map) || !map.hasColor() || map.getColor() == null) {
            return "absent";
        }
        return Integer.toString(map.getColor().asRGB());
    }

    private void requireNativeMapSentinel(ItemStack item, String stage) {
        final ItemMeta meta = item.getItemMeta();
        require(meta != null && "native-map-tint".equals(meta.getPersistentDataContainer()
                .get(ownerKey(), PersistentDataType.STRING)),
            "Native map PDC sentinel lost during " + stage);
        require(Long.valueOf(7311123456L).equals(meta.getPersistentDataContainer()
                .get(new NamespacedKey("networkstest", "map_ledger"), PersistentDataType.LONG)),
            "Native map typed LONG ledger changed during " + stage);
        require(Arrays.equals(new byte[]{-1, 0, 42}, meta.getPersistentDataContainer()
                .get(new NamespacedKey("networkstest", "map_payload"), PersistentDataType.BYTE_ARRAY)),
            "Native map typed BYTE_ARRAY payload changed during " + stage);
    }

    private void requireNativeSentinel(ItemStack item, String stage) {
        final ItemMeta meta = item.getItemMeta();
        require(meta != null && "native-fire-metadata".equals(meta.getPersistentDataContainer()
                .get(ownerKey(), PersistentDataType.STRING)),
            "Native positive PDC sentinel lost during " + stage);
    }

    @SuppressWarnings("deprecation")
    private static boolean hasFireResistance(ItemMeta meta) {
        return meta != null && meta.hasDamageResistant()
            && Objects.equals(meta.getDamageResistant(), DamageTypeTags.IS_FIRE);
    }

    @SuppressWarnings("deprecation")
    private static String describeFireResistance(ItemMeta meta) {
        if (meta == null) {
            return "meta=null";
        }
        if (!meta.hasDamageResistant()) {
            return "hasDamageResistant=false";
        }
        final var actual = meta.getDamageResistant();
        return "hasDamageResistant=true, actualKey="
            + (actual == null ? "null" : actual.getKey())
            + ", expectedKey=" + DamageTypeTags.IS_FIRE.getKey()
            + ", legacyEquals=" + Objects.equals(actual, DamageTypeTags.IS_FIRE);
    }

    /**
     * Real native Paper identity control: same trusted Slimefun ID, same owner
     * and ledger PDC, but an older nested Adventure display-name shape.
     * It must not be reported as equal merely because the ID matches. No
     * inventory is rewritten; the bytes are persisted as a disposable probe
     * fixture and reconstructed after a separate JVM boot.
     */
    private void verifyNativeHistoricalNameIdentity(String phase) throws Exception {
        final SlimefunItem uranium = SlimefunItem.getById("SMALL_URANIUM");
        require(uranium != null, "Native historical identity requires registered SMALL_URANIUM");
        final NamespacedKey ledgerKey = new NamespacedKey("networkstest", "historical_ledger");
        final NamespacedKey byteKey = new NamespacedKey("networkstest", "historical_bytes");
        final ItemStack registered = uranium.getItem().clone();
        final byte[] registeredBefore = registered.serializeAsBytes();
        final ItemStack fresh = registered.clone();
        fresh.setAmount(7);
        final ItemMeta freshMeta = fresh.getItemMeta();
        require(freshMeta != null, "Native uranium source has no item metadata");
        freshMeta.displayName(Component.text("Small Chunk of Uranium", NamedTextColor.RED)
            .decoration(TextDecoration.BOLD, TextDecoration.State.TRUE));
        freshMeta.getPersistentDataContainer().set(
            ownerKey(), PersistentDataType.STRING, "unchanged-historical-owner");
        freshMeta.getPersistentDataContainer().set(
            ledgerKey, PersistentDataType.LONG, 9876543210123L);
        freshMeta.getPersistentDataContainer().set(
            byteKey, PersistentDataType.BYTE_ARRAY, new byte[]{-1, 0, 42});
        require(fresh.setItemMeta(freshMeta), "Could not add native historical identity fixture metadata");
        final ItemStack old = fresh.clone();
        final ItemMeta oldMeta = old.getItemMeta();
        require(oldMeta != null, "Historical clone lost item metadata");
        oldMeta.displayName(Component.text("").append(
            Component.text("Small Chunk of Uranium", NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, TextDecoration.State.FALSE)));
        require(old.setItemMeta(oldMeta), "Native historical nested name rejected");

        require("SMALL_URANIUM".equals(Slimefun.getItemDataService()
                .getItemData(fresh.getItemMeta()).orElse(null)),
            "Fresh native identity lost registered Slimefun ID");
        require("SMALL_URANIUM".equals(Slimefun.getItemDataService()
                .getItemData(old.getItemMeta()).orElse(null)),
            "Old native identity lost registered Slimefun ID");
        require(!fresh.isSimilar(old),
            "Distinct nested historical Adventure display names must not stack");
        final byte[] newBytes = fresh.serializeAsBytes();
        final byte[] oldBytes = old.serializeAsBytes();
        require("neq.displayname".equals(ItemDifferenter.calc(fresh, old)),
            "Real native Item Differenter concealed a historical Adventure name difference");
        require("neq.displayname".equals(ItemDifferenter.calc(old, fresh)),
            "Real native historical name diagnosis is asymmetric");
        require(Arrays.equals(newBytes, fresh.serializeAsBytes())
                && Arrays.equals(oldBytes, old.serializeAsBytes())
                && Arrays.equals(registeredBefore, registered.serializeAsBytes()),
            "Native comparison changed registered, old or new item serialized bytes");
        require(fresh.getAmount() == 7 && old.getAmount() == 7,
            "Native historical diagnostic changed output quantity");
        require("unchanged-historical-owner".equals(old.getItemMeta()
                .getPersistentDataContainer().get(ownerKey(), PersistentDataType.STRING))
                && Long.valueOf(9876543210123L).equals(old.getItemMeta()
                    .getPersistentDataContainer().get(ledgerKey, PersistentDataType.LONG))
                && Arrays.equals(new byte[]{-1, 0, 42}, old.getItemMeta()
                    .getPersistentDataContainer().get(byteKey, PersistentDataType.BYTE_ARRAY)),
            "Native historical diagnostic changed typed owner, ledger or byte PDC");

        final ItemStack namedOne = fresh.clone();
        namedOne.editMeta(meta -> meta.itemName(Component.text("First item-name", NamedTextColor.GOLD)));
        final ItemStack namedTwo = fresh.clone();
        namedTwo.editMeta(meta -> meta.itemName(Component.text("Second item-name", NamedTextColor.GOLD)));
        require(!namedOne.isSimilar(namedTwo)
                && "neq.itemname".equals(ItemDifferenter.calc(namedOne, namedTwo))
                && "neq.itemname".equals(ItemDifferenter.calc(namedTwo, namedOne)),
            "Native Item Differenter ignored a distinct item-name Adventure component");
        final ItemStack otherOwner = fresh.clone();
        otherOwner.editMeta(meta -> meta.getPersistentDataContainer().set(
            ownerKey(), PersistentDataType.STRING, "another-owner"));
        require(!fresh.isSimilar(otherOwner)
                && "neq.pdc".equals(ItemDifferenter.calc(fresh, otherOwner)),
            "Native Item Differenter must reject distinct typed PDC owners");

        final Path persisted = getDataFolder().toPath().getParent()
            .resolve("native-historical-name-item.bin");
        if ("seed".equals(phase)) {
            Files.write(persisted, oldBytes);
        } else if ("verify".equals(phase)) {
            require(Files.isRegularFile(persisted),
                "Native historical item fixture missing after process restart");
            final ItemStack restored = ItemStack.deserializeBytes(Files.readAllBytes(persisted));
            require(restored.getAmount() == 7 && restored.isSimilar(old)
                    && !restored.isSimilar(fresh),
                "Native historical item lost identity or amount after separate-JVM byte deserialize");
            require("neq.displayname".equals(ItemDifferenter.calc(fresh, restored)),
                "Item Differenter lost historical distinction after process restart");
            require("unchanged-historical-owner".equals(restored.getItemMeta()
                    .getPersistentDataContainer().get(ownerKey(), PersistentDataType.STRING))
                    && Long.valueOf(9876543210123L).equals(restored.getItemMeta()
                        .getPersistentDataContainer().get(ledgerKey, PersistentDataType.LONG))
                    && Arrays.equals(new byte[]{-1, 0, 42}, restored.getItemMeta()
                        .getPersistentDataContainer().get(byteKey, PersistentDataType.BYTE_ARRAY)),
                "Restart deserialization lost typed owner, ledger or byte PDC");
        } else {
            throw new AssertionError("Unknown native historical name phase " + phase);
        }
        getLogger().info("NATIVE HISTORICAL ITEM IDENTITY phase=" + phase
            + " originalBytesUntouched=true oldNewDisplayDiffer=true itemNameDiffer=true"
            + " ownerAndLedgerPdcUntouched=true quantityConserved=true");
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

        // Force rehydration of the saved Slimefun KV/inventory in the actual
        // break handler. A new JVM has no persisted live QuantumCache objects.
        NetworkQuantumStorage.getCaches().remove(location);
        final Method onBreak = NetworkQuantumStorage.class.getDeclaredMethod(
            "onBreak", BlockBreakEvent.class);
        onBreak.setAccessible(true);
        final BlockBreakEvent event = new BlockBreakEvent(location.getBlock(), null);
        onBreak.invoke(storage, event);
        require(!event.isDropItems(), "One filled storage must suppress duplicate block drops");
        require(!NetworkQuantumStorage.getCaches().containsKey(location),
            "Break must detach the independently restored Quantum cache");
        require(menu.getItemInSlot(NetworkQuantumStorage.OUTPUT_SLOT) == null,
            "Break must remove output items from the original inventory");

        /*
         * Paper can expose the direct storage drop before inventory-slot drops become visible in
         * the world's entity index. A fixed two-tick delay proved flaky on busy CI runners even
         * though the persisted output slot was verified immediately before this break.
         *
         * Poll only the disposable world's entity-registration boundary. The deadline remains
         * strict: if the 64 emeralds never become visible, the existing verification still fails.
         */
        waitForDropsAfterBreak(location, menu);
    }

    private void waitForDropsAfterBreak(Location location, BlockMenu menu) {
        new BukkitRunnable() {
            int checks;

            @Override
            public void run() {
                try {
                    checks++;
                    if (expectedDropsVisible(location) || checks >= 40) {
                        cancel();
                        verifyDropsAfterWorldTick(location, menu, checks);
                    }
                } catch (Throwable failure) {
                    cancel();
                    fail("waiting for post-break item entity registration", failure);
                }
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    private boolean expectedDropsVisible(Location location) {
        int carrying = 0;
        int emeralds = 0;
        for (var entity : location.getWorld().getNearbyEntities(location, 3, 3, 3)) {
            if (!(entity instanceof Item drop)) {
                continue;
            }
            final ItemStack item = drop.getItemStack();
            if (Keys.getQuantumCache(item.getItemMeta()) != null) {
                carrying++;
            } else if (item.getType() == Material.EMERALD) {
                emeralds += item.getAmount();
            }
        }
        return carrying >= 1 && emeralds >= OUTPUT;
    }

    private void verifyDropsAfterWorldTick(Location location, BlockMenu menu, int ticksWaited) {
        final List<Item> drops = new ArrayList<>();
        Throwable problem = null;
        try {
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
            final ItemStack slot = menu.getItemInSlot(NetworkQuantumStorage.OUTPUT_SLOT);
            final List<String> details = drops.stream()
                .map(entity -> entity.getItemStack().getType() + " x"
                    + entity.getItemStack().getAmount() + " at " + entity.getLocation())
                .toList();
            getLogger().info("RESTART OUTPUT POST-TICK expected=64 observed=" + emeralds
                + " waitedTicks=" + ticksWaited + " entities=" + details + " outputSlot=" + slot);
            require(carrying == 1, "Expected exactly one stored-contents carrying item");
            require(emeralds == OUTPUT, "Expected 64 emeralds, found " + emeralds
                + " within " + ticksWaited + " server ticks; entities=" + details);
        } catch (Throwable failure) {
            problem = failure;
        } finally {
            // Complete all block/database cleanup before triggering server stop.
            try {
                for (Item drop : drops) drop.remove();
                NetworkQuantumStorage.getCaches().remove(location);
                QuantumCacheLifecycle.clearBreakMarker(location);
                Slimefun.getDatabaseManager().getBlockDataController().removeBlock(location);
                location.getBlock().setType(Material.AIR);
            } catch (Throwable failure) {
                if (problem == null) problem = failure;
                else problem.addSuppressed(failure);
            }
        }
        if (problem != null) {
            fail("checking post-tick Quantum output drops", problem);
            return;
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

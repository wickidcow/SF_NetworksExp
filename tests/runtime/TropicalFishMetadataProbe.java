package networksprobe;

import com.ytdd9527.networksexpansion.implementation.machines.manual.ItemDifferenter;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.TropicalFish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.TropicalFishBucketMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** Disposable-server test only. Never install this fixture on a player server. */
public final class TropicalFishMetadataProbe extends JavaPlugin implements Listener {
    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onServerLoad(ServerLoadEvent event) {
        // Run after plugins initialize and before the existing display fixture stops this disposable server.
        String result;
        try {
            int comparisons = verify();
            result = "PASS: native tropical-fish metadata comparisons=" + comparisons
                + "; server=" + Bukkit.getVersion() + "; source items unchanged";
        } catch (Throwable failure) {
            result = "FAIL: native tropical-fish metadata: " + failure;
            failure.printStackTrace();
        }
        getLogger().info(result);
        try {
            Files.writeString(getDataFolder().toPath().resolveSibling("tropical-fish-metadata-result.txt"), result + "\n");
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private int verify() throws Exception {
        boolean independent = independentComponents();
        int[] masks = independent ? new int[]{0, 1, 2, 3, 4, 5, 6, 7} : new int[]{0, 7};
        int checked = 0;
        for (int first : masks) {
            for (int second : masks) {
                compare(bucket(first, 0), bucket(second, 0),
                    first == second ? null : "neq.tropical.variant.has");
                checked++;
            }
            for (int changed = 0; changed < 8; changed++) {
                int effective = first & changed;
                String expected = (effective & 1) != 0 ? "neq.tropical.pattern.get"
                    : (effective & 2) != 0 ? "neq.tropical.bodycolor.get"
                    : (effective & 4) != 0 ? "neq.tropical.patterncolor.get" : null;
                compare(bucket(first, 0), bucket(first, changed), expected);
                checked++;
            }
            ItemStack source = bucket(first, 0);
            ItemStack loaded = ItemStack.deserializeBytes(source.serializeAsBytes());
            require(source.equals(loaded), "Same-runtime item serialization must retain complete metadata");
            compare(source, loaded, null);
            checked++;
        }
        getLogger().info("COMPONENT API: " + (independent ? "independent" : "packed") + "; checked=" + checked);
        return checked;
    }

    private boolean independentComponents() throws Exception {
        try {
            Method pattern = TropicalFishBucketMeta.class.getMethod("hasPattern");
            Method body = TropicalFishBucketMeta.class.getMethod("hasBodyColor");
            Method color = TropicalFishBucketMeta.class.getMethod("hasPatternColor");
            require(pattern.getReturnType() == boolean.class && body.getReturnType() == boolean.class
                && color.getReturnType() == boolean.class, "Unexpected presence API");
            return true;
        } catch (NoSuchMethodException oldApi) {
            require(Bukkit.getMinecraftVersion().equals("1.21.11"),
                "Only the 1.21.11 fixture may lack independent component methods");
            return false;
        }
    }

    private ItemStack bucket(int mask, int changed) {
        ItemStack item = new ItemStack(Material.TROPICAL_FISH_BUCKET);
        TropicalFishBucketMeta meta = (TropicalFishBucketMeta) item.getItemMeta();
        if ((mask & 1) != 0) meta.setPattern((changed & 1) == 0 ? TropicalFish.Pattern.KOB : TropicalFish.Pattern.SUNSTREAK);
        if ((mask & 2) != 0) meta.setBodyColor((changed & 2) == 0 ? DyeColor.ORANGE : DyeColor.BLACK);
        if ((mask & 4) != 0) meta.setPatternColor((changed & 4) == 0 ? DyeColor.WHITE : DyeColor.BLACK);
        meta.displayName(Component.text("Existing test bucket"));
        meta.lore(List.of(Component.text("Keep this test lore exactly")));
        meta.getPersistentDataContainer().set(new NamespacedKey("oldaddon", "opaque_test_data"),
            PersistentDataType.BYTE_ARRAY, new byte[]{1, 7, 42});
        item.setItemMeta(meta);
        return item;
    }

    private void compare(ItemStack first, ItemStack second, String expected) {
        var firstMeta = (TropicalFishBucketMeta) first.getItemMeta();
        var secondMeta = (TropicalFishBucketMeta) second.getItemMeta();
        var beforeFirst = firstMeta.clone();
        var beforeSecond = secondMeta.clone();
        byte[] firstBytes = first.serializeAsBytes();
        byte[] secondBytes = second.serializeAsBytes();
        require(Objects.equals(expected, ItemDifferenter.canQuickEscapeMetaVariant(firstMeta, secondMeta)),
            "Wrong native variant diagnostic; expected=" + expected);
        require(beforeFirst.equals(firstMeta) && beforeSecond.equals(secondMeta), "Comparison changed detached metadata");
        require(Objects.equals(expected == null ? "eq" : expected, ItemDifferenter.calc(first, second)),
            "Full native item comparison changed diagnostic; expected=" + expected);
        require(Arrays.equals(firstBytes, first.serializeAsBytes()) && Arrays.equals(secondBytes, second.serializeAsBytes()),
            "Comparison changed source item bytes");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}

package io.github.sefiraat.networks.utils;

import com.jeff_media.morepersistentdatatypes.DataType;
import io.github.mooy1.infinityexpansion.InfinityExpansion;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.managers.SupportedPluginManager;
import io.github.sefiraat.networks.network.stackcaches.BlueprintInstance;
import io.github.sefiraat.networks.network.stackcaches.QuantumCache;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.sefiraat.networks.utils.datatypes.PersistentCraftingBlueprintType;
import io.github.sefiraat.networks.utils.datatypes.PersistentQuantumStorageType;
import io.github.thebusybiscuit.slimefun4.libraries.dough.data.persistent.PersistentDataAPI;
import lombok.Data;
import lombok.experimental.UtilityClass;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

@Data
@UtilityClass
@NullMarked
public class Keys {
    public static final String NETWORKS_ID = "networks"; // Official version / Chinese localized version
    public static final String NETWORKS_CHANGED_ID = "networks-changed"; // Xinzi version

    public static final NamespacedKey ON_COOLDOWN = newKey("cooldown");
    public static final NamespacedKey ON_COOLDOWN2 = customNewKey(NETWORKS_ID, "cooldown");
    public static final NamespacedKey ON_COOLDOWN3 = customNewKey(NETWORKS_CHANGED_ID, "cooldown");

    public static long getCooldown(ItemMeta itemMeta) {
        long cooldownUntil = PersistentDataAPI.getLong(itemMeta, Keys.ON_COOLDOWN, -1);
        if (cooldownUntil == -1) {
            cooldownUntil = PersistentDataAPI.getLong(itemMeta, Keys.ON_COOLDOWN2, -1);
        }
        if (cooldownUntil == -1) {
            cooldownUntil = PersistentDataAPI.getLong(itemMeta, Keys.ON_COOLDOWN3, 0);
        }
        return cooldownUntil;
    }

    public static final NamespacedKey QUANTUM_STORAGE_INSTANCE = newKey("quantum_storage");
    public static final NamespacedKey QUANTUM_STORAGE_INSTANCE2 = customNewKey(NETWORKS_ID, "quantum_storage");
    public static final NamespacedKey QUANTUM_STORAGE_INSTANCE3 = customNewKey(NETWORKS_CHANGED_ID, "quantum_storage");

    @Nullable
    public static QuantumCache getQuantumCache(ItemMeta meta) {
        QuantumCache quantumCache = DataTypeMethods.getCustom(
            meta, Keys.QUANTUM_STORAGE_INSTANCE, PersistentQuantumStorageType.TYPE);

        if (quantumCache == null) {
            quantumCache = DataTypeMethods.getCustom(
                meta, Keys.QUANTUM_STORAGE_INSTANCE2, PersistentQuantumStorageType.TYPE);
        }

        if (quantumCache == null) {
            quantumCache = DataTypeMethods.getCustom(
                meta, Keys.QUANTUM_STORAGE_INSTANCE3, PersistentQuantumStorageType.TYPE);
        }

        return quantumCache;
    }

    public static final NamespacedKey BLUEPRINT_INSTANCE = newKey("ntw_blueprint");
    public static final NamespacedKey BLUEPRINT_INSTANCE2 = customNewKey(NETWORKS_ID, "ntw_blueprint");
    public static final NamespacedKey BLUEPRINT_INSTANCE3 = customNewKey(NETWORKS_CHANGED_ID, "ntw_blueprint");

    @Nullable
    public static BlueprintInstance getBlueprintInstance(ItemMeta meta) {
        BlueprintInstance instance = DataTypeMethods.getCustomSafely(
            meta, Keys.BLUEPRINT_INSTANCE, PersistentCraftingBlueprintType.TYPE);

        if (instance == null) {
            instance = DataTypeMethods.getCustomSafely(
                meta, Keys.BLUEPRINT_INSTANCE2, PersistentCraftingBlueprintType.TYPE);
        }

        if (instance == null) {
            instance = DataTypeMethods.getCustomSafely(
                meta, Keys.BLUEPRINT_INSTANCE3, PersistentCraftingBlueprintType.TYPE);
        }

        return instance == BlueprintInstance.INVALID ? null : instance;
    }

    public static final NamespacedKey FACE = newKey("face");
    public static final NamespacedKey FACE2 = customNewKey(NETWORKS_ID, "face");
    public static final NamespacedKey FACE3 = customNewKey(NETWORKS_CHANGED_ID, "face");

    @Nullable
    public static String getFace(ItemMeta itemMeta) {
        String string = DataTypeMethods.getCustom(itemMeta, Keys.FACE, DataType.STRING);
        if (string == null) {
            string = DataTypeMethods.getCustom(itemMeta, Keys.FACE2, DataType.STRING);
        }

        if (string == null) {
            string = DataTypeMethods.getCustom(itemMeta, Keys.FACE3, DataType.STRING);
        }

        return string;
    }

    public static final NamespacedKey ITEM = newKey("item");
    public static final NamespacedKey ITEM2 = customNewKey(NETWORKS_ID, "item");
    public static final NamespacedKey ITEM3 = customNewKey(NETWORKS_CHANGED_ID, "item");

    public static ItemStack @Nullable [] getItems(ItemMeta itemMeta) {
        ItemStack[] templateStacks = DataTypeMethods.getCustom(itemMeta, Keys.ITEM, DataType.ITEM_STACK_ARRAY);
        if (templateStacks == null) {
            templateStacks = DataTypeMethods.getCustom(itemMeta, Keys.ITEM2, DataType.ITEM_STACK_ARRAY);
        }

        if (templateStacks == null) {
            templateStacks = DataTypeMethods.getCustom(itemMeta, Keys.ITEM3, DataType.ITEM_STACK_ARRAY);
        }

        return templateStacks;
    }

    public static final NamespacedKey TARGET_LOCATION = newKey("target-location");
    public static final NamespacedKey TARGET_LOCATION2 = customNewKey(NETWORKS_ID, "target-location");
    public static final NamespacedKey TARGET_LOCATION3 = customNewKey(NETWORKS_CHANGED_ID, "target-location");

    public static @Nullable Location getTargetLocation(ItemMeta itemMeta) {
        Location location = PersistentDataAPI.get(itemMeta, Keys.TARGET_LOCATION, DataType.LOCATION);
        if (location == null) {
            location = PersistentDataAPI.get(itemMeta, Keys.TARGET_LOCATION2, DataType.LOCATION);
        }

        if (location == null) {
            location = PersistentDataAPI.get(itemMeta, Keys.TARGET_LOCATION3, DataType.LOCATION);
        }

        return location;
    }

    public static final NamespacedKey AMOUNT = newKey("amount");
    public static final NamespacedKey AMOUNT2 = customNewKey(NETWORKS_ID, "amount");
    public static final NamespacedKey AMOUNT3 = customNewKey(NETWORKS_CHANGED_ID, "amount");

    public static long getAmountLong(PersistentDataContainer primitive) {
        Long amount;
        try {
            amount = primitive.get(Keys.AMOUNT, DataType.LONG);
            if (amount == null) {
                amount = primitive.get(Keys.AMOUNT2, DataType.LONG);
            }
            if (amount == null) {
                amount = primitive.getOrDefault(Keys.AMOUNT3, DataType.LONG, 0L);
            }
        } catch (Throwable ignored) {
            Integer amountI;
            amountI = primitive.get(Keys.AMOUNT, DataType.INTEGER);
            if (amountI == null) {
                amountI = primitive.get(Keys.AMOUNT2, DataType.INTEGER);
            }
            if (amountI == null) {
                amountI = primitive.getOrDefault(Keys.AMOUNT3, DataType.INTEGER, 0);
            }
            amount = amountI.longValue();
        }

        return amount;
    }

    public static final NamespacedKey RECIPE = newKey("recipe");
    public static final NamespacedKey RECIPE2 = customNewKey(NETWORKS_ID, "recipe");
    public static final NamespacedKey RECIPE3 = customNewKey(NETWORKS_CHANGED_ID, "recipe");

    public static @Nullable ItemStack @Nullable [] getRecipe(PersistentDataContainer primitive) {
        @Nullable ItemStack[] recipe = primitive.get(Keys.RECIPE, DataType.ITEM_STACK_ARRAY);
        if (recipe == null) {
            recipe = primitive.get(Keys.RECIPE2, DataType.ITEM_STACK_ARRAY);
        }
        if (recipe == null) {
            recipe = primitive.get(Keys.RECIPE3, DataType.ITEM_STACK_ARRAY);
        }
        return recipe;
    }

    public static final NamespacedKey OUTPUT = newKey("output");
    public static final NamespacedKey OUTPUT2 = customNewKey(NETWORKS_ID, "output");
    public static final NamespacedKey OUTPUT3 = customNewKey(NETWORKS_CHANGED_ID, "output");

    public static @Nullable ItemStack getOutput(PersistentDataContainer primitive) {
        @Nullable ItemStack output = primitive.get(Keys.OUTPUT, DataType.ITEM_STACK);
        if (output == null) {
            output = primitive.get(Keys.OUTPUT2, DataType.ITEM_STACK);
        }
        if (output == null) {
            output = primitive.get(Keys.OUTPUT3, DataType.ITEM_STACK);
        }
        return output;
    }

    public static final NamespacedKey MAX_AMOUNT = newKey("max_amount");
    public static final NamespacedKey MAX_AMOUNT2 = customNewKey(NETWORKS_ID, "max_amount");
    public static final NamespacedKey MAX_AMOUNT3 = customNewKey(NETWORKS_CHANGED_ID, "max_amount");

    public static long getMaxAmount(PersistentDataContainer primitive) {
        Long limit;
        try {
            limit = primitive.get(Keys.MAX_AMOUNT, DataType.LONG);
            if (limit == null) {
                limit = primitive.get(Keys.MAX_AMOUNT2, DataType.LONG);
            }
            if (limit == null) {
                limit = primitive.getOrDefault(Keys.MAX_AMOUNT3, DataType.LONG, 64L);
            }
        } catch (Throwable ignored) {
            Integer limitI;
            limitI = primitive.get(Keys.MAX_AMOUNT, DataType.INTEGER);
            if (limitI == null) {
                limitI = primitive.get(Keys.MAX_AMOUNT2, DataType.INTEGER);
            }
            if (limitI == null) {
                limitI = primitive.getOrDefault(Keys.MAX_AMOUNT3, DataType.INTEGER, 64);
            }
            limit = limitI.longValue();
        }

        return limit;
    }

    public static final NamespacedKey VOID = newKey("void");
    public static final NamespacedKey VOID2 = customNewKey(NETWORKS_ID, "void");
    public static final NamespacedKey VOID3 = customNewKey(NETWORKS_CHANGED_ID, "void");

    public static boolean getVoidExcess(PersistentDataContainer primitive) {
        Boolean voidExcess = primitive.get(Keys.VOID, DataType.BOOLEAN);
        if (voidExcess == null) {
            voidExcess = primitive.get(Keys.VOID2, DataType.BOOLEAN);
        }
        if (voidExcess == null) {
            voidExcess = primitive.getOrDefault(Keys.VOID3, DataType.BOOLEAN, false);
        }
        return voidExcess;
    }

    public static final NamespacedKey SUPPORTS_CUSTOM_MAX_AMOUNT = Keys.newKey("supports_custom_max_amount");
    public static final NamespacedKey TRANSFER_MODE = newKey("transfer_mode");
    public static final NamespacedKey STORAGE_UNIT_UPGRADE_TABLE = newKey("storage_upgrade_table");
    public static final NamespacedKey STORAGE_UNIT_UPGRADE_TABLE_MODEL = newKey("storage_upgrade_table_model");
    public static final NamespacedKey ITEM_MOVER_ITEM = newKey("item_mover_item");
    public static final NamespacedKey ITEM_MOVER_AMOUNT = newKey("item_mover_amount");
    public static final NamespacedKey ITEM_MOVER_AMOUNT_LONG = newKey("item_mover_amount_long");
    public static final NamespacedKey EXPANSION_WORKBENCH = newKey("expansion_workbench");

    public static final NamespacedKey FACING_PRESET = newKey("facing_preset");

    public static final NamespacedKey INFINITY_DISPLAY;

    static {
        if (SupportedPluginManager.getInstance().isInfinityExpansion()) {
            INFINITY_DISPLAY = InfinityExpansion.createKey("display");
        } else {
            INFINITY_DISPLAY = Keys.customNewKey("infinityexpansion", "display");
        }
    }

    public static NamespacedKey newKey(String key) {
        return new NamespacedKey(Networks.getInstance(), key);
    }

    public static NamespacedKey customNewKey(String namespace, String key) {
        return new NamespacedKey(namespace, key);
    }

    public static NamespacedKey customNewKey(Plugin plugin, String key) {
        return new NamespacedKey(plugin, key);
    }
}

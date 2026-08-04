package io.github.sefiraat.networks.utils.datatypes;

import com.jeff_media.morepersistentdatatypes.DataType;
import com.ytdd9527.networksexpansion.implementation.ExpansionItems;
import io.github.sefiraat.networks.network.stackcaches.BlueprintInstance;
import io.github.sefiraat.networks.network.stackcaches.CardInstance;
import io.github.sefiraat.networks.utils.Keys;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataAdapterContext;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

/**
 * A version-tolerant {@link PersistentDataType} for crafting blueprints.
 *
 * <p>The current format stores simple vanilla and Slimefun entries as compact strings and falls
 * back to Paper's ItemStack serializer only for complex items. Deserialization also understands
 * the three historical array keys used by official Networks and older forks. A malformed legacy
 * payload returns {@link BlueprintInstance#INVALID} instead of throwing while an inventory is
 * opening or an auto-crafter is ticking.</p>
 *
 * @author Sfiguz7
 * @author Walshy
 * @author balugaq
 */
@NullMarked
public class PersistentCraftingBlueprintType implements PersistentDataType<PersistentDataContainer, BlueprintInstance> {

    private static final String RECIPE_PREFIX = "recipe_";
    private static final int DEFAULT_RECIPE_SIZE = 9;
    private static final int MAX_RECIPE_SIZE = 54;

    public static final PersistentDataType<PersistentDataContainer, BlueprintInstance> TYPE =
        new PersistentCraftingBlueprintType();

    @Override
    public Class<PersistentDataContainer> getPrimitiveType() {
        return PersistentDataContainer.class;
    }

    @Override
    public Class<BlueprintInstance> getComplexType() {
        return BlueprintInstance.class;
    }

    public static void setItemStack(PersistentDataContainer container, String key, @Nullable ItemStack itemStack) {
        setItemStack(container, Keys.newKey(key), itemStack);
    }

    public static void setItemStack(
        PersistentDataContainer container, NamespacedKey key, @Nullable ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir() || itemStack.getAmount() <= 0) {
            return;
        }

        ItemStack snapshot = itemStack.clone();
        if (snapshot.isSimilar(new ItemStack(snapshot.getType()))) {
            container.set(
                key,
                DataType.STRING,
                "mc;" + snapshot.getType().name() + ';' + snapshot.getAmount());
            return;
        }

        SlimefunItem slimefunItem = SlimefunItem.getByItem(snapshot);
        if (slimefunItem != null && snapshot.isSimilar(slimefunItem.getItem())) {
            container.set(
                key,
                DataType.STRING,
                "sf;" + slimefunItem.getId() + ';' + snapshot.getAmount());
            return;
        }

        container.set(key, DataType.ITEM_STACK, snapshot);
    }

    @Nullable
    public static ItemStack getItemStack(PersistentDataContainer primitive, NamespacedKey key) {
        ItemStack compact = readCompactItem(primitive, key);
        if (compact != null) {
            return compact;
        }

        try {
            ItemStack item = primitive.get(key, DataType.ITEM_STACK);
            return item == null ? null : item.clone();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static ItemStack readCompactItem(PersistentDataContainer primitive, NamespacedKey key) {
        final String encoded;
        try {
            encoded = primitive.get(key, DataType.STRING);
        } catch (RuntimeException ignored) {
            return null;
        }

        if (encoded == null || encoded.isBlank()) {
            return null;
        }

        String[] parts = encoded.split(";", 3);
        if (parts.length != 3) {
            return null;
        }

        final int amount;
        try {
            amount = Integer.parseInt(parts[2]);
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (amount <= 0) {
            return null;
        }

        try {
            if ("mc".equals(parts[0])) {
                Material material = Material.matchMaterial(parts[1]);
                return material == null || material.isAir() ? null : new ItemStack(material, amount);
            }

            if ("sf".equals(parts[0])) {
                SlimefunItem slimefunItem = SlimefunItem.getById(parts[1]);
                if (slimefunItem == null) {
                    slimefunItem = ExpansionItems.PLACEHOLDER_ITEM;
                }
                if (slimefunItem == null) {
                    return null;
                }

                ItemStack item = slimefunItem.getItem().clone();
                item.setAmount(amount);
                return item;
            }
        } catch (RuntimeException ignored) {
            return null;
        }

        return null;
    }

    @Override
    public PersistentDataContainer toPrimitive(
        BlueprintInstance complex, PersistentDataAdapterContext context) {
        PersistentDataContainer container = context.newPersistentDataContainer();

        ItemStack[] recipeItems = complex.getRecipeItems();
        for (int i = 0; i < recipeItems.length; i++) {
            setItemStack(container, RECIPE_PREFIX + i, recipeItems[i]);
        }

        setItemStack(container, Keys.OUTPUT, complex.getItemStack());
        return container;
    }

    @Override
    public BlueprintInstance fromPrimitive(
        PersistentDataContainer primitive, PersistentDataAdapterContext context) {
        ItemStack[] recipe = readLegacyRecipe(primitive);
        if (recipe == null) {
            recipe = readCurrentRecipe(primitive);
        }

        ItemStack output = readLegacyOutput(primitive);
        if (output == null) {
            output = getItemStack(primitive, Keys.OUTPUT);
        }

        if (recipe == null || output == null || output.getType().isAir()) {
            return BlueprintInstance.INVALID;
        }
        return new BlueprintInstance(cloneRecipe(recipe), output.clone());
    }

    @Nullable
    private static ItemStack[] readLegacyRecipe(PersistentDataContainer primitive) {
        try {
            ItemStack[] recipe = Keys.getRecipe(primitive);
            return recipe == null ? null : cloneRecipe(recipe);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Nullable
    private static ItemStack[] readCurrentRecipe(PersistentDataContainer primitive) {
        List<RecipeEntry> entries = new ArrayList<>();
        int highestSlot = -1;

        for (NamespacedKey key : primitive.getKeys()) {
            String rawKey = key.getKey();
            if (!rawKey.startsWith(RECIPE_PREFIX)) {
                continue;
            }

            int slot;
            try {
                slot = Integer.parseInt(rawKey.substring(RECIPE_PREFIX.length()));
            } catch (NumberFormatException ignored) {
                continue;
            }

            if (slot < 0 || slot >= MAX_RECIPE_SIZE) {
                continue;
            }

            ItemStack item = getItemStack(primitive, key);
            if (item != null && !item.getType().isAir()) {
                entries.add(new RecipeEntry(slot, item));
                highestSlot = Math.max(highestSlot, slot);
            }
        }

        if (entries.isEmpty()) {
            return null;
        }

        ItemStack[] recipe = new ItemStack[Math.max(DEFAULT_RECIPE_SIZE, highestSlot + 1)];
        for (RecipeEntry entry : entries) {
            recipe[entry.slot()] = entry.item().clone();
        }
        return recipe;
    }

    @Nullable
    private static ItemStack readLegacyOutput(PersistentDataContainer primitive) {
        try {
            ItemStack output = Keys.getOutput(primitive);
            return output == null ? null : output.clone();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static ItemStack[] cloneRecipe(ItemStack[] source) {
        ItemStack[] copy = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i] == null ? null : source[i].clone();
        }
        return copy;
    }

    private record RecipeEntry(int slot, ItemStack item) {
    }
}

package com.balugaq.netex.api.data;

import io.github.sefiraat.networks.utils.StackUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.jetbrains.annotations.NotNull;

/**
 * A custom exact recipe choice that preserves Networks' Slimefun-aware item matching.
 *
 * <p>Paper 26.x makes {@link RecipeChoice.ExactChoice} final, so this class uses composition
 * instead of inheritance while retaining the same constructors and {@link RecipeChoice}
 * contract used by older Networks releases.</p>
 */
public final class SimpleRecipeChoice implements RecipeChoice {

    private final List<ItemStack> choices;

    public SimpleRecipeChoice(@NotNull ItemStack choice) {
        this(List.of(choice));
    }

    public SimpleRecipeChoice(@NotNull ItemStack... choices) {
        this(List.of(choices));
    }

    public SimpleRecipeChoice(@NotNull List<ItemStack> choices) {
        if (choices.isEmpty()) {
            throw new IllegalArgumentException("Recipe choices must not be empty");
        }

        List<ItemStack> copiedChoices = new ArrayList<>(choices.size());
        for (ItemStack choice : choices) {
            Objects.requireNonNull(choice, "Recipe choices must not contain null items");
            if (choice.getType() == Material.AIR) {
                throw new IllegalArgumentException("Recipe choices must not contain air");
            }
            copiedChoices.add(choice.clone());
        }
        this.choices = Collections.unmodifiableList(copiedChoices);
    }

    public @NotNull List<ItemStack> getChoices() {
        List<ItemStack> copies = new ArrayList<>(choices.size());
        for (ItemStack choice : choices) {
            copies.add(choice.clone());
        }
        return Collections.unmodifiableList(copies);
    }

    @Override
    public boolean test(@NotNull ItemStack other) {
        for (ItemStack choice : choices) {
            if (StackUtils.itemsMatch(choice, other, true, false)) {
                return true;
            }
        }
        return false;
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull ItemStack getItemStack() {
        return choices.getFirst().clone();
    }

    @Override
    public @NotNull SimpleRecipeChoice clone() {
        return new SimpleRecipeChoice(choices);
    }

    @Override
    public boolean equals(Object object) {
        return this == object
            || object instanceof SimpleRecipeChoice other && choices.equals(other.choices);
    }

    @Override
    public int hashCode() {
        return choices.hashCode();
    }

    @Override
    public String toString() {
        return "SimpleRecipeChoice" + choices;
    }
}

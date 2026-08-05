package io.github.sefiraat.networks.network.stackcaches;

import io.github.sefiraat.networks.network.barrel.BarrelCore;
import io.github.sefiraat.networks.network.barrel.BarrelType;
import io.github.sefiraat.networks.utils.StackUtils;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

@Getter
@Setter
public abstract class BarrelIdentity extends ItemStackCache implements BarrelCore {

    private Location location;
    private long amount;
    private long limit;
    private BarrelType type;

    @ParametersAreNonnullByDefault
    protected BarrelIdentity(Location location, @Nullable ItemStack itemStack, long amount, long limit, BarrelType type) {
        super(itemStack);
        this.location = location;
        this.amount = amount;
        this.limit = limit;
        this.type = type;
    }

    /** Returns whether this storage identity can currently accept the supplied item. */
    public boolean canAccept(@NotNull ItemStack incoming) {
        return StackUtils.itemsMatch(this, incoming);
    }
}

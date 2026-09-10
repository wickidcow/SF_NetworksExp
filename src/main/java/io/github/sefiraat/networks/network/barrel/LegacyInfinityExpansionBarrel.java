package io.github.sefiraat.networks.network.barrel;

import com.xzavier0722.mc.plugin.slimefun4.storage.util.StorageCacheUtils;
import io.github.sefiraat.networks.Networks;
import io.github.sefiraat.networks.network.stackcaches.BarrelIdentity;
import io.github.sefiraat.networks.network.stackcaches.ItemRequest;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/** Networks view of an original Infinity Expansion storage unit without linking its API classes. */
public final class LegacyInfinityExpansionBarrel extends BarrelIdentity {

    private static final int INPUT_SLOT = 10;
    private static final int OUTPUT_SLOT = 16;

    private final Object cache;
    private final Method depositAll;

    public LegacyInfinityExpansionBarrel(
        @NotNull Location location,
        @Nullable ItemStack itemStack,
        long amount,
        long limit,
        @NotNull Object cache,
        @NotNull Method depositAll
    ) {
        super(location, itemStack, amount, limit, BarrelType.INFINITY);
        this.cache = cache;
        this.depositAll = depositAll;
    }

    @Override
    public @Nullable ItemStack requestItem(@NotNull ItemRequest itemRequest) {
        try {
            final BlockMenu menu = StorageCacheUtils.getMenu(getLocation());
            return menu == null ? null : menu.getItemInSlot(OUTPUT_SLOT);
        } catch (RuntimeException | LinkageError exception) {
            Networks.getSupportedPluginManager().disableOptionalIntegration("InfinityExpansion", exception);
            return null;
        }
    }

    @Override
    public void depositItemStack(ItemStack @NotNull [] itemsToDeposit) {
        try {
            // Cast the array to Object so Method.invoke does not expand ItemStack[] as varargs.
            depositAll.invoke(cache, (Object) itemsToDeposit, true);
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            Networks.getSupportedPluginManager().disableOptionalIntegration("InfinityExpansion", exception);
        } catch (InvocationTargetException exception) {
            final Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            Networks.getSupportedPluginManager().disableOptionalIntegration("InfinityExpansion", cause);
        } catch (RuntimeException | LinkageError exception) {
            Networks.getSupportedPluginManager().disableOptionalIntegration("InfinityExpansion", exception);
        }
    }

    @Override
    public int @NotNull [] getInputSlot() {
        return new int[]{INPUT_SLOT};
    }

    @Override
    public int @NotNull [] getOutputSlot() {
        return new int[]{OUTPUT_SLOT};
    }
}

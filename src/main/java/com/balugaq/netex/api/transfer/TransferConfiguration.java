package com.balugaq.netex.api.transfer;

import com.balugaq.netex.api.enums.TransportMode;
import com.balugaq.netex.api.interfaces.GrabTickOnly;
import com.balugaq.netex.api.interfaces.PushTickOnly;
import com.balugaq.netex.utils.Lang;
import io.github.thebusybiscuit.slimefun4.libraries.dough.items.CustomItemStack;
import lombok.Data;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("ClassCanBeRecord")
@Data
public class TransferConfiguration {
    // The distance when place block
    public final int defaultDistance;
    // The interval ticks when pushing items
    public final int defaultPushTick;
    // The interval ticks when grabbing items
    public final int defaultGrabTick;
    // THe transport mode when place block
    public final @NotNull TransportMode defaultTransportMode;
    // The transport limit when place block
    public final int defaultTransportLimit;

    // The required power per action (push / grab) when ticking block.
    public final int defaultRequiredPower;
    // Maximum line targets processed per push/grab pass. 0 keeps the historical unlimited behavior.
    public final int maxTargetsPerTick;
    // The max distance
    public final int maxDistance;
    // The max transport limit
    public final int maxTransportLimit;

    // GUI-related
    public final int @NotNull [] backgroundSlots;
    public final int @NotNull [] templateBackgroundSlots;
    public final int @NotNull [] templateSlots;
    public final int northSlot;
    public final int southSlot;
    public final int eastSlot;
    public final int westSlot;
    public final int upSlot;
    public final int downSlot;
    public final int minusSlot;
    public final int cargoNumberSlot;
    public final int addSlot;
    public final int transportModeSlot;

    @Range(from = 0, to = Integer.MAX_VALUE)
    public int dd() {
        return defaultDistance;
    }

    @Range(from = 0, to = Integer.MAX_VALUE)
    public int dpt() {
        return defaultPushTick;
    }

    @Range(from = 0, to = Integer.MAX_VALUE)
    public int dgt() {
        return defaultGrabTick;
    }

    @NotNull
    public TransportMode dtm() {
        return defaultTransportMode;
    }

    @Range(from = 0, to = Integer.MAX_VALUE)
    public int dtl() {
        return defaultTransportLimit;
    }

    @Range(from = 0, to = Integer.MAX_VALUE)
    public int drp() {
        return defaultRequiredPower;
    }

    @Range(from = 0, to = Integer.MAX_VALUE)
    public int mtpt() {
        return maxTargetsPerTick;
    }

    @Range(from = 0, to = Integer.MAX_VALUE)
    public int md() {
        return maxDistance;
    }

    @Range(from = 0, to = Integer.MAX_VALUE)
    public int mtl() {
        return maxTransportLimit;
    }

    public int @NotNull [] bs() {
        return backgroundSlots;
    }

    public int @NotNull [] tbs() {
        return templateBackgroundSlots;
    }

    public int @NotNull [] ts() {
        return templateSlots;
    }

    @Range(from = 0, to = 53)
    public int ns() {
        return northSlot;
    }

    @Range(from = 0, to = 53)
    public int ss() {
        return southSlot;
    }

    @Range(from = 0, to = 53)
    public int es() {
        return eastSlot;
    }

    @Range(from = 0, to = 53)
    public int ws() {
        return westSlot;
    }

    @Range(from = 0, to = 53)
    public int us() {
        return upSlot;
    }

    @Range(from = 0, to = 53)
    public int ds() {
        return downSlot;
    }

    @Range(from = 0, to = 53)
    public int ms() {
        return minusSlot;
    }

    @Range(from = 0, to = 53)
    public int cns() {
        return cargoNumberSlot;
    }

    @Range(from = 0, to = 53)
    public int as() {
        return addSlot;
    }

    @Range(from = 0, to = 53)
    public int tms() {
        return transportModeSlot;
    }

    public final @NotNull ItemStack getInformationIcon() {
        List<String> lore = new ArrayList<>();
        lore.add("");
        if (maxDistance != 1) {
            lore.add(String.format(Lang.getString("icons.mechanism.transfers.max_distance"), maxDistance));
        }
        if (!(this instanceof PushTickOnly)) {
            lore.add(String.format(Lang.getString("icons.mechanism.transfers.push_item_tick"), defaultPushTick));
        }
        if (!(this instanceof GrabTickOnly)) {
            lore.add(String.format(Lang.getString("icons.mechanism.transfers.grab_item_tick"), defaultGrabTick));
        }
        if (defaultRequiredPower != 0) {
            lore.add(String.format(Lang.getString("icons.mechanism.transfers.required_power"), defaultRequiredPower));
        }

        return new CustomItemStack(Material.BOOK, Lang.getString("icons.mechanism.transfers.data_title"), lore);
    }
}

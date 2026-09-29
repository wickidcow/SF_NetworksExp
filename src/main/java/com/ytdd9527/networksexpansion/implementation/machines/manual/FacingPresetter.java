package com.ytdd9527.networksexpansion.implementation.machines.manual;

import com.balugaq.netex.api.enums.FacingPreset;
import com.balugaq.netex.api.helpers.Icon;
import com.balugaq.netex.utils.Lang;
import com.balugaq.netex.utils.NetworksVersionedEnchantment;
import com.ytdd9527.networksexpansion.utils.TextUtil;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.slimefun.network.NetworkDirectional;
import io.github.sefiraat.networks.utils.Keys;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.bakedlibs.dough.items.CustomItemStack;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.List;

@NullMarked
public class FacingPresetter extends NetworkDirectional {
    private static final int[] ITEM_SLOTS = new int[] {
        0,  1,  2,  3,  4,  5,  6,  7,  8,
        9,  10, 11, 12, 13, 14, 15, 16, 17,
        18, 19, 20, 21, 22, 23, 24, 25, 26
    };
    private static final int[] BACKGROUND_SLOTS = new int[]{
        27,     29, 30,     32,     34, 35,
            37,     39, 40, 41,     43, 44,
        45,     47, 48,     50, 51, 52, 53
    };

    private static final int HELP_ICON = 33;
    private static final int AUTO_SLOT = 42;
    private static final int NORTH_SLOT = 28;
    private static final int SOUTH_SLOT = 46;
    private static final int EAST_SLOT = 38;
    private static final int WEST_SLOT = 36;
    private static final int UP_SLOT = 31;
    private static final int DOWN_SLOT = 49;
    public FacingPresetter(final ItemGroup itemGroup, final SlimefunItemStack item, final RecipeType recipeType, final ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.FACING_PRESETTER);
        for (int slot : ITEM_SLOTS) {
            getSlotsToDrop().add(slot);
        }
    }

    protected int[] getBackgroundSlots() {
        return BACKGROUND_SLOTS;
    }

    public int getNorthSlot() {
        return NORTH_SLOT;
    }

    public int getSouthSlot() {
        return SOUTH_SLOT;
    }

    public int getEastSlot() {
        return EAST_SLOT;
    }

    public int getWestSlot() {
        return WEST_SLOT;
    }

    public int getUpSlot() {
        return UP_SLOT;
    }

    public int getDownSlot() {
        return DOWN_SLOT;
    }

    @Override
    public void postRegister() {
        new BlockMenuPreset(this.getId(), this.getItemName()) {

            @Override
            public void init() {
                drawBackground(getBackgroundSlots());

                addItem(
                    HELP_ICON,
                    Icon.FACING_PRESETTER_HELP_ICON,
                    (player, i, itemStack, clickAction) -> false);
                addItem(
                    getNorthSlot(),
                    getDirectionalSlotPane(FacingPreset.NORTH),
                    (player, i, itemStack, clickAction) -> false);
                addItem(
                    getSouthSlot(),
                    getDirectionalSlotPane(FacingPreset.SOUTH),
                    (player, i, itemStack, clickAction) -> false);
                addItem(
                    getEastSlot(),
                    getDirectionalSlotPane(FacingPreset.EAST),
                    (player, i, itemStack, clickAction) -> false);
                addItem(
                    getWestSlot(),
                    getDirectionalSlotPane(FacingPreset.WEST),
                    (player, i, itemStack, clickAction) -> false);
                addItem(
                    getUpSlot(),
                    getDirectionalSlotPane(FacingPreset.UP),
                    (player, i, itemStack, clickAction) -> false);
                addItem(
                    getDownSlot(),
                    getDirectionalSlotPane(FacingPreset.DOWN),
                    (player, i, itemStack, clickAction) -> false);
                addItem(
                    AUTO_SLOT,
                    getDirectionalSlotPane(FacingPreset.AUTO),
                    (player, i, itemStack, clickAction) -> false);
            }

            @Override
            public void newInstance(@NotNull BlockMenu blockMenu, @NotNull Block b) {
                blockMenu.addMenuClickHandler(
                    getNorthSlot(),
                    (player, i, itemStack, clickAction) ->
                        setFacingPreset(player, blockMenu, FacingPreset.NORTH));
                blockMenu.addMenuClickHandler(
                    getSouthSlot(),
                    (player, i, itemStack, clickAction) ->
                        setFacingPreset(player, blockMenu, FacingPreset.SOUTH));
                blockMenu.addMenuClickHandler(
                    getEastSlot(),
                    (player, i, itemStack, clickAction) ->
                        setFacingPreset(player, blockMenu, FacingPreset.EAST));
                blockMenu.addMenuClickHandler(
                    getWestSlot(),
                    (player, i, itemStack, clickAction) ->
                        setFacingPreset(player, blockMenu, FacingPreset.WEST));
                blockMenu.addMenuClickHandler(
                    getUpSlot(),
                    (player, i, itemStack, clickAction) ->
                        setFacingPreset(player, blockMenu, FacingPreset.UP));
                blockMenu.addMenuClickHandler(
                    getDownSlot(),
                    (player, i, itemStack, clickAction) ->
                        setFacingPreset(player, blockMenu, FacingPreset.DOWN));
                blockMenu.addMenuClickHandler(
                    AUTO_SLOT,
                    (player, i, itemStack, clickAction) ->
                        setFacingPreset(player, blockMenu, FacingPreset.AUTO));
            }

            @Override
            public boolean canOpen(@NotNull Block block, @NotNull Player player) {
                return player.hasPermission("slimefun.inventory.bypass")
                    || (this.getSlimefunItem().canUse(player, false)
                    && Slimefun.getProtectionManager()
                    .hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK));
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }
        };
    }

    public static boolean setFacingPreset(Player player, BlockMenu blockMenu, FacingPreset facingPreset) {
        if (!(SlimefunItem.getById(blockMenu.getPreset().getID()) instanceof FacingPresetter)) {
            return false;
        }

        boolean success = false;
        for (int slot : ITEM_SLOTS) {
            ItemStack item = blockMenu.getItemInSlot(slot);
            if (SlimefunItem.getByItem(item) instanceof NetworkDirectional) {
                ItemMeta meta = item.getItemMeta();
                var pdc = meta.getPersistentDataContainer();
                List<Component> existingLore = meta.lore();
                List<Component> lore = existingLore == null ? new ArrayList<>() : new ArrayList<>(existingLore);
                if (!pdc.has(Keys.FACING_PRESET)) {
                    pdc.set(Keys.FACING_PRESET, PersistentDataType.STRING, facingPreset.name());

                    lore.add(TextUtil.component(String.format(
                        Lang.getString("messages.completed-operation.directional.presetted_facing"),
                        facingPreset.name()
                    )));
                    meta.lore(lore);
                    item.setItemMeta(meta);
                    blockMenu.replaceExistingItem(slot, item);
                    success = true;
                } else if (!pdc.getOrDefault(Keys.FACING_PRESET, PersistentDataType.STRING, "").equals(facingPreset.name())) {
                    // remove last lore line and set new pdc
                    if (!lore.isEmpty()) {
                        lore.remove(lore.size() - 1);
                    }
                    lore.add(TextUtil.component(String.format(
                        Lang.getString("messages.completed-operation.directional.presetted_facing"),
                        facingPreset.name()
                    )));
                    meta.lore(lore);
                    pdc.set(Keys.FACING_PRESET, PersistentDataType.STRING, facingPreset.name());
                    item.setItemMeta(meta);
                    blockMenu.replaceExistingItem(slot, item);
                    success = true;
                }
            }
        }
        if (success) {
            player.sendMessage(String.format(Lang.getString("messages.completed-operation.directional.presetted"), facingPreset.name()));
        } else {
            player.sendMessage(Lang.getString("messages.completed-operation.directional.presetted_failed"));
        }
        return false;
    }

    @NotNull
    public static ItemStack getDirectionalSlotPane(FacingPreset preset) {
        final ItemStack displayStack = new CustomItemStack(
            Material.GREEN_STAINED_GLASS_PANE,
            String.format(
                Lang.getString("messages.normal-operation.directional.preset_display_name"),
                preset.name()
            ));

        final ItemMeta itemMeta = displayStack.getItemMeta();
        itemMeta.addEnchant(NetworksVersionedEnchantment.LUCK_OF_THE_SEA, 1, true);
        itemMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

        displayStack.setItemMeta(itemMeta);
        return displayStack;
    }

    @Override
    public void updateGui(@Nullable BlockMenu blockMenu) {
    }
}

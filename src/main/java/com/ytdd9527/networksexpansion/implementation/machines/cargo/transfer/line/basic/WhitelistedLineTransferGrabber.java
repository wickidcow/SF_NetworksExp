package com.ytdd9527.networksexpansion.implementation.machines.cargo.transfer.line.basic;

import com.balugaq.netex.api.atrributes.WhitelistedGrabber;
import com.balugaq.netex.api.enums.FeedbackType;
import com.balugaq.netex.api.enums.TransferType;
import com.balugaq.netex.api.factories.TransferConfigFactory;
import com.balugaq.netex.api.helpers.Icon;
import com.balugaq.netex.api.interfaces.SoftCellBannable;
import com.balugaq.netex.api.transfer.TransferConfiguration;
import com.balugaq.netex.utils.Lang;
import com.balugaq.netex.utils.LineOperationUtil;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.slimefun.network.NetworkDirectional;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.core.attributes.RecipeDisplayItem;
import io.github.bakedlibs.dough.items.CustomItemStack;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@SuppressWarnings("DuplicatedCode")
public class WhitelistedLineTransferGrabber extends NetworkDirectional implements RecipeDisplayItem, SoftCellBannable, WhitelistedGrabber {
    private static final TransferConfiguration config =
        TransferConfigFactory.getTransferConfiguration(TransferType.WHITELISTED_LINE_TRANSFER_GRABBER);
    private static final int maxDistance = config.maxDistance;
    private static final int grabItemTick = config.defaultGrabTick;
    private final HashMap<Location, Integer> TICKER_MAP = new HashMap<>();

    public WhitelistedLineTransferGrabber(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack @NotNull [] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.LINE_TRANSFER_VANILLA_GRABBER);
    }

    @Override
    protected void onTick(@Nullable BlockMenu blockMenu, @NotNull Block block) {
        super.onTick(blockMenu, block);

        if (blockMenu == null) {
            sendFeedback(block.getLocation(), FeedbackType.INVALID_BLOCK);
            return;
        }
        final Location location = blockMenu.getLocation();
        if (grabItemTick != 1) {
            int tickCounter = getTickCounter(location);
            tickCounter = (tickCounter + 1) % grabItemTick;

            if (tickCounter == 0) {
                tryGrabItem(blockMenu);
            }

            updateTickCounter(location, tickCounter);
        } else {
            tryGrabItem(blockMenu);
        }
    }

    private int getTickCounter(Location location) {
        final Integer ticker = TICKER_MAP.get(location);
        if (ticker == null) {
            TICKER_MAP.put(location, 0);
            return 0;
        }
        return ticker;
    }

    private void updateTickCounter(Location location, int tickCounter) {
        TICKER_MAP.put(location, tickCounter);
    }

    private void tryGrabItem(@NotNull BlockMenu blockMenu) {
        final NodeDefinition definition = NetworkStorage.getNode(blockMenu.getLocation());

        if (definition == null || definition.getNode() == null) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_NETWORK_FOUND);
            return;
        }

        final BlockFace direction = this.getCurrentDirection(blockMenu);
        if (direction == BlockFace.SELF) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_DIRECTION_SET);
            return;
        }

        final NetworkRoot root = definition.getNode().getRoot();

        if (checkSoftCellBan(blockMenu.getLocation(), root)) {
            return;
        }

        List<ItemStack> templates = getClonedTemplateItems(blockMenu);
        LineOperationUtil.doOperation(
            blockMenu.getLocation(),
            direction,
            config.maxDistance,
            false,
            false,
            (targetMenu) ->
                grabMenu(blockMenu, targetMenu, root, templates));
        sendFeedback(blockMenu.getLocation(), FeedbackType.WORKING);
    }

    @Override
    public int getNorthSlot() {
        return config.getNorthSlot();
    }

    @Override
    public int getSouthSlot() {
        return config.getSouthSlot();
    }

    @Override
    public int getEastSlot() {
        return config.getEastSlot();
    }

    @Override
    public int getWestSlot() {
        return config.getWestSlot();
    }

    @Override
    public int getUpSlot() {
        return config.getUpSlot();
    }

    @Override
    public int getDownSlot() {
        return config.getDownSlot();
    }

    @Nullable
    @Override
    protected ItemStack getOtherBackgroundStack() {
        return Icon.GRABBER_TEMPLATE_BACKGROUND_STACK;
    }

    @Override
    public int @NotNull [] getBackgroundSlots() {
        return config.getBackgroundSlots();
    }

    @Override
    public int @NotNull [] getOtherBackgroundSlots() {
        return config.getTemplateBackgroundSlots();
    }

    @Override
    public int @NotNull [] getItemSlots() {
        return config.getTemplateSlots();
    }

    @Override
    public boolean runSync() {
        return true;
    }

    @Override
    public int[] getTemplateSlots() {
        return config.getTemplateSlots();
    }

    @Override
    protected Particle.@NotNull DustOptions getDustOptions() {
        return new Particle.DustOptions(Color.MAROON, 1);
    }

    public @NotNull List<ItemStack> getDisplayRecipes() {
        List<ItemStack> displayRecipes = new ArrayList<>(6);
        displayRecipes.add(new CustomItemStack(
            Material.BOOK,
            Lang.getString("icons.mechanism.transfers.data_title"),
            "",
            String.format(Lang.getString("icons.mechanism.transfers.max_distance"), maxDistance),
            String.format(Lang.getString("icons.mechanism.transfers.grab_item_tick"), grabItemTick)));
        return displayRecipes;
    }
}

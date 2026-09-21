package io.github.sefiraat.networks.slimefun.network;

import com.balugaq.netex.api.enums.FeedbackType;
import com.xzavier0722.mc.plugin.slimefun4.storage.controller.SlimefunBlockData;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.slimefun.NetworkSlimefunItems;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.ItemSetting;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.items.settings.IntRangeSetting;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import me.mrCookieSlime.Slimefun.Objects.handlers.BlockTicker;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("DuplicatedCode")
public class NetworkImport extends NetworkObject {

    private static final int[] INPUT_SLOTS = new int[]{0, 1, 2, 3, 4, 5, 6, 7, 8};

    private final @NotNull ItemSetting<Integer> tickRate;

    public NetworkImport(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack[] recipe,
        ItemStack output) {
        super(itemGroup, item, recipeType, recipe, output, NodeType.IMPORT);

        this.tickRate = new IntRangeSetting(this, "tick_rate", 1, 1, 10);
        addItemSetting(this.tickRate);

        for (int inputSlot : INPUT_SLOTS) {
            this.getSlotsToDrop().add(inputSlot);
        }

        addItemHandler(new BlockTicker() {

            private int tick = 1;

            @Override
            public boolean isSynchronized() {
                return io.github.sefiraat.networks.Networks.getConfigManager().useSynchronizedMachineTickers();
            }

            @Override
            public void tick(@NotNull Block block, SlimefunItem item, @NotNull SlimefunBlockData data) {
                if (tick <= 1) {
                    final BlockMenu blockMenu = data.getBlockMenu();
                    if (blockMenu == null) {
                        return;
                    }
                    addToRegistry(block);
                    tryAddItem(blockMenu);
                }
            }

            @Override
            public void uniqueTick() {
                tick = tick <= 1 ? tickRate.getValue() : tick - 1;
            }
        });
    }

    private void tryAddItem(@NotNull BlockMenu blockMenu) {
        final NodeDefinition definition = NetworkStorage.getNode(blockMenu.getLocation());

        if (definition == null || definition.getNode() == null) {
            sendFeedback(blockMenu.getLocation(), FeedbackType.NO_NETWORK_FOUND);
            return;
        }

        final NetworkRoot root = definition.getNode().getRoot();
        final var accessor = blockMenu.getLocation();
        boolean checkedInitialAccess = false;

        for (int inputSlot : INPUT_SLOTS) {
            final ItemStack itemStack = blockMenu.getItemInSlot(inputSlot);

            if (itemStack == null || itemStack.getType() == Material.AIR) {
                continue;
            }

            // Preserve the historical no-op behavior for an empty importer, but once there is actual work,
            // skip it immediately while the root's existing transport-miss limiter is active.
            if (!checkedInitialAccess) {
                checkedInitialAccess = true;
                if (!root.allowAccessInput(accessor)) {
                    sendFeedback(accessor, FeedbackType.ROOT_LIMITING_ACCESS_INPUT);
                    return;
                }
            }

            final int moved = NetworkTransferUtils.moveMenuSlotIntoNetwork(
                root, accessor, blockMenu, inputSlot);
            if (moved > 0) {
                sendFeedback(accessor, FeedbackType.WORKING);
            } else if (!root.allowAccessInput(accessor)) {
                // A failed transfer may have crossed the existing miss threshold. Do not scan the rest
                // of this importer's slots when the root has already decided further attempts must wait.
                sendFeedback(accessor, FeedbackType.ROOT_LIMITING_ACCESS_INPUT);
                return;
            }
        }
    }

    @Override
    public void postRegister() {
        new BlockMenuPreset(this.getId(), this.getItemName()) {

            @Override
            public void init() {
                setSize(9);
            }

            @Override
            public boolean canOpen(@NotNull Block block, @NotNull Player player) {
                return player.hasPermission("slimefun.inventory.bypass")
                    || (NetworkSlimefunItems.NETWORK_IMPORT.canUse(player, false)
                    && Slimefun.getProtectionManager()
                    .hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK));
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                if (flow == ItemTransportFlow.INSERT) {
                    return INPUT_SLOTS;
                }
                return new int[0];
            }
        };
    }
}

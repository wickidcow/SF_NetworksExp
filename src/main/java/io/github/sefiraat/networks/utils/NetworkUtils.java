package io.github.sefiraat.networks.utils;

import com.balugaq.netex.utils.Lang;
import com.jeff_media.morepersistentdatatypes.DataType;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.network.NetworkNode;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.slimefun.network.NetworkController;
import io.github.sefiraat.networks.slimefun.network.NetworkDirectional;
import io.github.sefiraat.networks.slimefun.network.pusher.NetworkPusher;
import io.github.sefiraat.networks.slimefun.tools.NetworkConfigurator;
import io.github.sefiraat.networks.utils.datatypes.DataTypeMethods;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import lombok.experimental.UtilityClass;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings("DuplicatedCode")
@UtilityClass
public class NetworkUtils {

    public static void applyConfig(
        @NotNull NetworkDirectional directional, @NotNull BlockMenu blockMenu, @NotNull Player player) {
        ItemStack itemStack = player.getInventory().getItemInOffHand();

        if (SlimefunItem.getByItem(itemStack) instanceof NetworkConfigurator) {
            applyConfig(directional, itemStack, blockMenu, player);
        }
    }

    public static void applyConfig(
        @NotNull NetworkDirectional directional,
        @NotNull ItemStack itemStack,
        @NotNull BlockMenu blockMenu,
        @NotNull Player player) {
        final ItemMeta itemMeta = itemStack.getItemMeta();
        ItemStack[] templateStacks = Keys.getItems(itemMeta);

        String string = Keys.getFace(itemMeta);

        if (string == null) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.configurator.facing_not_found"));
            return;
        }

        directional.setDirection(blockMenu, BlockFace.valueOf(string));
        player.sendMessage(Lang.getString("messages.completed-operation.configurator.pasted_facing", string));

        for (int slot : directional.getItemSlots()) {
            final ItemStack stackToDrop = blockMenu.getItemInSlot(slot);
            if (stackToDrop != null && stackToDrop.getType() != Material.AIR) {
                blockMenu.getLocation().getWorld().dropItem(blockMenu.getLocation(), stackToDrop.clone());
                stackToDrop.setAmount(0);
            }
        }

        if (templateStacks != null && directional.getItemSlots().length > 0) {
            int i = 0;
            for (ItemStack templateStack : templateStacks) {
                if (templateStack != null && templateStack.getType() != Material.AIR) {
                    boolean worked = false;
                    for (ItemStack stack : player.getInventory()) {
                        if (StackUtils.itemsMatch(stack, templateStack)) {
                            final ItemStack stackClone = StackUtils.getAsQuantity(stack, 1);
                            stack.setAmount(stack.getAmount() - 1);
                            blockMenu.replaceExistingItem(directional.getItemSlots()[i], stackClone);
                            player.sendMessage(String.format(
                                Lang.getString("messages.completed-operation.configurator.pasted_item"), i));
                            worked = true;
                            break;
                        }
                    }
                    if (!worked) {
                        player.sendMessage(String.format(
                            Lang.getString("messages.unsupported-operation.configurator.not_enough_items"), i));
                    }
                } else if (directional instanceof NetworkPusher) {
                    player.sendMessage(String.format(
                        Lang.getString("messages.unsupported-operation.configurator.no_item_configured_pusher"),
                        i));
                }
                i++;
            }
        } else {
            player.sendMessage(Lang.getString("messages.unsupported-operation.configurator.no_item_configured"));
        }
    }

    public static void clearNetwork(@NotNull Location location) {
        NodeDefinition definition = NetworkStorage.getNode(location);

        if (definition == null || definition.getNode() == null) {
            return;
        }

        NetworkNode node = definition.getNode();

        if (node.getNodeType() == NodeType.CONTROLLER) {
            NetworkController.wipeNetwork(location);
            NetworkStorage.removeNodeOnly(location);
            return;
        }

        // Keep the remaining loaded node definitions indexed. Only the changed block is removed; its live root is
        // discarded so transfers cannot use stale connectivity while the controller prepares a fresh topology.
        NetworkStorage.detachNode(location);
    }

    public static void clearNearbyNetworks(@NotNull Location location) {
        for (BlockFace face : NetworkNode.VALID_FACES) {
            clearNetwork(location.clone().add(face.getDirection()));
        }
    }
}

package io.github.sefiraat.networks.slimefun.network.grid;

import com.balugaq.netex.api.helpers.Icon;
import com.balugaq.netex.api.helpers.SupportedCraftingTableRecipes;
import com.balugaq.netex.utils.BlockMenuUtil;
import com.balugaq.netex.utils.Lang;
import io.github.sefiraat.networks.NetworkStorage;
import io.github.sefiraat.networks.events.NetworkCraftEvent;
import io.github.sefiraat.networks.network.GridItemRequest;
import io.github.sefiraat.networks.network.NetworkRoot;
import io.github.sefiraat.networks.network.NodeDefinition;
import io.github.sefiraat.networks.slimefun.NetworkSlimefunItems;
import io.github.sefiraat.networks.utils.NetworkTransferUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

@SuppressWarnings("DuplicatedCode")
public class NetworkCraftingGrid extends AbstractGrid {

    private static final int[] BACKGROUND_SLOTS = {
        0, 1, 3, 4, 5, 14, 23, 32, 33, 35, 41, 42, 44, 45, 47, 49, 50, 51, 52, 53
    };

    private static final int[] DISPLAY_SLOTS = {
        9, 10, 11, 12, 13, 18, 19, 20, 21, 22, 27, 28, 29, 30, 31, 36, 37, 38, 39, 40
    };

    private static final int[] CRAFT_ITEMS = {6, 7, 8, 15, 16, 17, 24, 25, 26};

    private static final int INPUT_SLOT = 2;
    private static final int FILTER = 45;
    private static final int PAGE_PREVIOUS = 46;
    private static final int CHANGE_SORT = 47;
    private static final int PAGE_NEXT = 48;

    private static final int CRAFT_BUTTON_SLOT = 34;
    private static final int CRAFT_OUTPUT_SLOT = 43;

    private static final Map<Location, GridCache> CACHE_MAP = new ConcurrentHashMap<>();

    public NetworkCraftingGrid(
        @NotNull ItemGroup itemGroup,
        @NotNull SlimefunItemStack item,
        @NotNull RecipeType recipeType,
        ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe);
        for (int craftItem : CRAFT_ITEMS) {
            this.getSlotsToDrop().add(craftItem);
        }
        this.getSlotsToDrop().add(CRAFT_OUTPUT_SLOT);
    }

    @Override
    public void postRegister() {
        getPreset();
    }

    @NotNull
    @Override
    public BlockMenuPreset getPreset() {
        return new BlockMenuPreset(this.getId(), this.getItemName()) {

            @Override
            public void init() {
                drawBackground(BACKGROUND_SLOTS);
                drawBackground(getDisplaySlots());
                setSize(54);
            }

            @Override
            public boolean canOpen(@NotNull Block block, @NotNull Player player) {
                return player.hasPermission("slimefun.inventory.bypass")
                    || (NetworkSlimefunItems.NETWORK_CRAFTING_GRID.canUse(player, false)
                    && Slimefun.getProtectionManager()
                    .hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK));
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }

            @Override
            public void newInstance(@NotNull BlockMenu menu, @NotNull Block b) {
                CACHE_MAP.put(menu.getLocation(), new GridCache(0, 0, GridCache.SortOrder.ALPHABETICAL));

                menu.replaceExistingItem(getPagePrevious(), getPagePreviousStack());
                menu.addMenuClickHandler(getPagePrevious(), (p, slot, item, action) -> {
                    GridCache gridCache = getCacheMap().get(menu.getLocation());
                    gridCache.setPage(gridCache.getPage() <= 0 ? 0 : gridCache.getPage() - 1);
                    CACHE_MAP.put(menu.getLocation(), gridCache);
                    updateDisplay(menu);
                    return false;
                });

                menu.replaceExistingItem(getPageNext(), getPageNextStack());
                menu.addMenuClickHandler(getPageNext(), (p, slot, item, action) -> {
                    GridCache gridCache = getCacheMap().get(menu.getLocation());
                    gridCache.setPage(
                        gridCache.getPage() >= gridCache.getMaxPages()
                            ? gridCache.getMaxPages()
                            : gridCache.getPage() + 1);
                    getCacheMap().put(menu.getLocation(), gridCache);
                    updateDisplay(menu);
                    return false;
                });

                menu.replaceExistingItem(getChangeSort(), getChangeSortStack());
                menu.addMenuClickHandler(getChangeSort(), (p, slot, item, action) -> {
                    GridCache gridCache = getCacheMap().get(menu.getLocation());
                    AbstractGrid.updateSortOrder(gridCache, action, 2);
                    getCacheMap().put(menu.getLocation(), gridCache);
                    updateDisplay(menu);
                    return false;
                });

                menu.replaceExistingItem(getFilterSlot(), getFilterStack());
                menu.addMenuClickHandler(getFilterSlot(), (p, slot, item, action) -> {
                    GridCache gridCache = getCacheMap().get(menu.getLocation());
                    setFilter(p, menu, gridCache, action);
                    return false;
                });

                for (int displaySlot : getDisplaySlots()) {
                    menu.replaceExistingItem(displaySlot, ChestMenuUtils.getBackground());
                    menu.addMenuClickHandler(displaySlot, (p, slot, item, action) -> false);
                }

                menu.replaceExistingItem(CRAFT_BUTTON_SLOT, Icon.CRAFT_BUTTON);
                menu.addMenuClickHandler(CRAFT_BUTTON_SLOT, (player, slot, item, action) -> {
                    if (action.isShiftClicked()) {
                        tryReturnItems(menu);
                    } else {
                        tryCraft(menu, player);
                    }
                    return false;
                });

                menu.addPlayerInventoryClickHandler((p, s, i, a) -> {
                    if (!a.isShiftClicked() || a.isRightClicked()) {
                        return true;
                    }

                    // Shift+Left-click: commit from the exact player inventory slot.
                    NodeDefinition definition = NetworkStorage.getNode(menu.getLocation());
                    if (definition == null || definition.getNode() == null) {
                        return false;
                    }
                    NetworkTransferUtils.moveInventorySlotIntoNetwork(
                        definition.getNode().getRoot(), menu.getLocation(), p.getInventory(), s);
                    return false;
                });
            }
        };
    }

    @NotNull
    @Override
    protected Map<Location, GridCache> getCacheMap() {
        return CACHE_MAP;
    }

    @Override
    public int[] getBackgroundSlots() {
        return BACKGROUND_SLOTS;
    }

    @Override
    public int[] getDisplaySlots() {
        return DISPLAY_SLOTS;
    }

    @Override
    public int getInputSlot() {
        return INPUT_SLOT;
    }

    @Override
    public int getChangeSort() {
        return CHANGE_SORT;
    }

    @Override
    public int getPagePrevious() {
        return PAGE_PREVIOUS;
    }

    @Override
    public int getPageNext() {
        return PAGE_NEXT;
    }

    @Override
    protected int getFilterSlot() {
        return FILTER;
    }

    private void tryCraft(@NotNull BlockMenu menu, @NotNull Player player) {
        final NodeDefinition definition = NetworkStorage.getNode(menu.getLocation());
        if (definition == null || definition.getNode() == null) {
            return;
        }

        final NetworkRoot root = definition.getNode().getRoot();
        final ItemStack[] displayedRecipe = snapshotRecipe(menu);
        ItemStack[] consumptionRecipe = displayedRecipe;

        SupportedCraftingTableRecipes.RecipeMatch slimefunMatch =
            SupportedCraftingTableRecipes.findRecipe(displayedRecipe);
        ItemStack crafted;
        if (slimefunMatch != null) {
            consumptionRecipe = slimefunMatch.recipe();
            crafted = slimefunMatch.output();
        } else {
            crafted = Bukkit.craftItem(copyStacks(displayedRecipe), player.getWorld(), player);
        }

        if (crafted == null || crafted.getType() == Material.AIR || crafted.getAmount() <= 0) {
            return;
        }

        final SlimefunItem outputItem = SlimefunItem.getByItem(crafted);
        if (outputItem != null && outputItem.isDisabled()) {
            player.sendMessage(Lang.getString("messages.unsupported-operation.encoder.disabled_output"));
            return;
        }

        if (!hasExactIngredients(menu, consumptionRecipe)) {
            return;
        }

        NetworkCraftEvent event =
            new NetworkCraftEvent(player, this, copyStacks(consumptionRecipe), crafted.clone());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }

        ItemStack eventOutput = event.getOutput();
        if (eventOutput == null
            || eventOutput.getType() == Material.AIR
            || eventOutput.getAmount() <= 0
            || !BlockMenuUtil.fits(menu, eventOutput, CRAFT_OUTPUT_SLOT)) {
            return;
        }

        // Consume every exact ingredient before creating the output.
        for (int slotIndex = 0; slotIndex < CRAFT_ITEMS.length; slotIndex++) {
            ItemStack required = slotIndex < consumptionRecipe.length ? consumptionRecipe[slotIndex] : null;
            if (required == null || required.getType() == Material.AIR || required.getAmount() <= 0) {
                continue;
            }
            BlockMenuUtil.consumeItem(menu, CRAFT_ITEMS[slotIndex], Math.max(1, required.getAmount()), true);
        }

        final ItemStack outputRemainder = BlockMenuUtil.pushItem(menu, eventOutput.clone(), CRAFT_OUTPUT_SLOT);
        if (outputRemainder != null
            && outputRemainder.getType() != Material.AIR
            && outputRemainder.getAmount() > 0) {
            NetworkTransferUtils.rollbackNetworkWithdrawal(
                root,
                menu.getLocation(),
                outputRemainder,
                menu.getLocation(),
                "classic crafting-grid output commit");
        }
        menu.markDirty();
        root.refreshRootItems();

        // Refill only empty ingredient slots with the same exact recipe ingredient.
        for (int slotIndex = 0; slotIndex < CRAFT_ITEMS.length; slotIndex++) {
            ItemStack required = slotIndex < consumptionRecipe.length ? consumptionRecipe[slotIndex] : null;
            if (required == null || required.getType() == Material.AIR || required.getAmount() <= 0) {
                continue;
            }

            int menuSlot = CRAFT_ITEMS[slotIndex];
            ItemStack remaining = menu.getItemInSlot(menuSlot);
            if (remaining != null && remaining.getType() != Material.AIR && remaining.getAmount() > 0) {
                continue;
            }

            NetworkTransferUtils.moveNetworkItemIntoMenu(
                root,
                menu.getLocation(),
                menu,
                required,
                Math.max(1, required.getAmount()),
                menuSlot);
        }
    }

    private ItemStack[] snapshotRecipe(BlockMenu menu) {
        ItemStack[] recipe = new ItemStack[CRAFT_ITEMS.length];
        for (int i = 0; i < CRAFT_ITEMS.length; i++) {
            ItemStack stack = menu.getItemInSlot(CRAFT_ITEMS[i]);
            if (stack != null && stack.getType() != Material.AIR && stack.getAmount() > 0) {
                recipe[i] = stack.clone();
                recipe[i].setAmount(1);
            }
        }
        return recipe;
    }

    private boolean hasExactIngredients(BlockMenu menu, ItemStack[] recipe) {
        for (int i = 0; i < CRAFT_ITEMS.length; i++) {
            ItemStack required = i < recipe.length ? recipe[i] : null;
            ItemStack supplied = menu.getItemInSlot(CRAFT_ITEMS[i]);

            boolean requiredEmpty = required == null
                || required.getType() == Material.AIR
                || required.getAmount() <= 0;
            boolean suppliedEmpty = supplied == null
                || supplied.getType() == Material.AIR
                || supplied.getAmount() <= 0;
            if (requiredEmpty || suppliedEmpty) {
                if (requiredEmpty != suppliedEmpty) {
                    return false;
                }
                continue;
            }

            if (supplied.getAmount() < Math.max(1, required.getAmount())
                || !io.github.sefiraat.networks.utils.StackUtils.itemsMatch(supplied, required)) {
                return false;
            }
        }
        return true;
    }

    private ItemStack[] copyStacks(ItemStack[] stacks) {
        ItemStack[] copy = new ItemStack[stacks.length];
        for (int i = 0; i < stacks.length; i++) {
            copy[i] = stacks[i] == null ? null : stacks[i].clone();
        }
        return copy;
    }

    private void tryReturnItems(@NotNull BlockMenu menu) {
        // Get node and, if it doesn't exist - escape
        final NodeDefinition definition = NetworkStorage.getNode(menu.getLocation());

        if (definition == null || definition.getNode() == null) {
            return;
        }

        for (int recipeSlot : CRAFT_ITEMS) {
            final ItemStack stack = menu.getItemInSlot(recipeSlot);

            if (stack == null || stack.getType() == Material.AIR) {
                continue;
            }
            NetworkTransferUtils.moveMenuSlotIntoNetwork(
                definition.getNode().getRoot(), menu.getLocation(), menu, recipeSlot);
        }
    }
}

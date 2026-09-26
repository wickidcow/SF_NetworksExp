package com.balugaq.netex.api.interfaces;

import com.balugaq.jeg.api.recipe_complete.RecipeCompleteSession;
import com.balugaq.jeg.api.recipe_complete.source.RecipeCompleteProvider;
import com.balugaq.jeg.core.listeners.RecipeCompletableListener;
import com.balugaq.jeg.utils.GuideUtil;
import com.balugaq.netex.api.helpers.Icon;
import com.balugaq.netex.utils.Lang;
import io.github.sefiraat.networks.Networks;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

public interface RecipeCompletableWithGuide {
    default void addJEGButton(@NotNull BlockMenu blockMenu, @Range(from = 0, to = 53) int slot) {
        if (Networks.getSupportedPluginManager().isJustEnoughGuide()) {
            blockMenu.replaceExistingItem(slot, Icon.JEG_BUTTON);
            blockMenu.addMenuClickHandler(slot, (player, slot1, item, action) -> {
                try {
                    if (RecipeCompletableListener.isSelectingItemStackToRecipeComplete(player)) {
                        var session = RecipeCompleteSession.getSession(player);
                        if (session == null) return false;
                        if (session.getMenu() != null && session.getMenu().getLocation().equals(blockMenu.getLocation())) {
                            GuideUtil.openGuide(player);
                            return false;
                        } else {
                            session.cancel();
                        }
                    }

                    RecipeCompletableListener.allowSelectingItemStackToRecipeComplete(player);
                    int[] slots = RecipeCompletableListener.getIngredientSlots(getSlimefunItem());
                    boolean unordered = RecipeCompletableListener.isUnordered(getSlimefunItem());
                    var session = RecipeCompleteSession.create(blockMenu, player, action, slots, unordered, 1);
                    if (session == null) return false;
                    RecipeCompleteProvider.openSlimefun(session);
                } catch (Exception ignored) {
                    Lang.getString("messages.unsupported-operation.incompatible-jeg-version");
                }
                return false;
            });
        }
    }

    @NotNull SlimefunItem getSlimefunItem();
}

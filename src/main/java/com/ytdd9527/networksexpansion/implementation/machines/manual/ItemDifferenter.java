package com.ytdd9527.networksexpansion.implementation.machines.manual;

import com.balugaq.netex.api.helpers.Icon;
import com.ytdd9527.networksexpansion.implementation.ExpansionItems;
import io.github.sefiraat.networks.network.NodeType;
import io.github.sefiraat.networks.slimefun.network.NetworkObject;
import io.github.sefiraat.networks.utils.StackUtils;
import io.github.thebusybiscuit.slimefun4.api.items.ItemGroup;
import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItemStack;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import io.github.thebusybiscuit.slimefun4.libraries.dough.protection.Interaction;
import io.github.thebusybiscuit.slimefun4.utils.ChestMenuUtils;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenu;
import me.mrCookieSlime.Slimefun.api.inventory.BlockMenuPreset;
import me.mrCookieSlime.Slimefun.api.item_transport.ItemTransportFlow;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.AxolotlBucketMeta;
import org.bukkit.inventory.meta.BannerMeta;
import org.bukkit.inventory.meta.BlockDataMeta;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.CrossbowMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkEffectMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.KnowledgeBookMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.MapMeta;
import org.bukkit.inventory.meta.MusicInstrumentMeta;
import org.bukkit.inventory.meta.OminousBottleMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.Repairable;
import org.bukkit.inventory.meta.ShieldMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.SuspiciousStewMeta;
import org.bukkit.inventory.meta.TropicalFishBucketMeta;
import org.bukkit.inventory.meta.WritableBookMeta;
import org.jetbrains.annotations.NotNull;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;
import java.util.Optional;

@SuppressWarnings("DuplicatedCode")
public class ItemDifferenter extends NetworkObject {
    private static final int[] BACKGROUND_SLOTS = {
        0,  1,  2,  3,  4,  5,  6,  7,  8,
        9,      11, 12,     14, 15,     17,
        18, 19, 20, 21,     23, 24, 25, 26,
    };
    private static final int ITEM_1_SLOT = 10;
    private static final int DIFF_SLOT = 13;
    private static final int ITEM_2_SLOT = 16;
    private static final int RESULT_SLOT = 22;

    @ParametersAreNonnullByDefault
    public ItemDifferenter(ItemGroup itemGroup, SlimefunItemStack item, RecipeType recipeType, ItemStack[] recipe) {
        super(itemGroup, item, recipeType, recipe, NodeType.ITEM_DIFFERENTER);
        getSlotsToDrop().add(ITEM_1_SLOT);
        getSlotsToDrop().add(ITEM_2_SLOT);
    }

    @Override
    public void postRegister() {
        new BlockMenuPreset(this.getId(), this.getItemName()) {

            @Override
            public void init() {
                setSize(27);
                for (int slot : BACKGROUND_SLOTS) {
                    addItem(slot, Icon.GRAY_BACKGROUND);
                }
                addItem(DIFF_SLOT, Icon.DIFF_BUTTON);
                addItem(RESULT_SLOT, Icon.DIFF_RESULT_ICON);
            }

            @Override
            public boolean canOpen(@NotNull Block block, @NotNull Player player) {
                return player.hasPermission("slimefun.inventory.bypass")
                    || (ExpansionItems.ITEM_DIFFERENTER.canUse(player, false)
                    && Slimefun.getProtectionManager()
                    .hasPermission(player, block.getLocation(), Interaction.INTERACT_BLOCK));
            }

            @Override
            public void newInstance(@NotNull BlockMenu menu, @NotNull Block b) {
                for (int slot : BACKGROUND_SLOTS) {
                    menu.addMenuClickHandler(slot, ChestMenuUtils.getEmptyClickHandler());
                }
                menu.addMenuClickHandler(DIFF_SLOT, (p, s, i, a) -> {
                    ItemStack i1 = menu.getItemInSlot(ITEM_1_SLOT);
                    ItemStack i2 = menu.getItemInSlot(ITEM_2_SLOT);
                    if (i1 == null || i1.getType().isAir() || i2 == null || i2.getType().isAir()) {
                        return setResult(menu, "no-item", false);
                    }
                    return setResult(menu, calc(i1, i2), i1.isSimilar(i2));
                });
                menu.addMenuClickHandler(RESULT_SLOT, ChestMenuUtils.getEmptyClickHandler());
            }

            @Override
            public int[] getSlotsAccessedByItemTransport(ItemTransportFlow flow) {
                return new int[0];
            }
        };
    }

    public static boolean setResult(BlockMenu menu, String result, boolean isSimilar) {
        menu.replaceExistingItem(RESULT_SLOT, getResultIcon(result, isSimilar));
        return false;
    }

    public static ItemStack getResultIcon(String result, boolean isSimilar) {
        ItemStack i = Icon.DIFF_RESULT_ICON.clone();
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName((result.equals("no-item") ? "" : ("isSimilar: " + isSimilar + " | ")) + meta.getDisplayName() + result);
        i.setItemMeta(meta);
        return i;
    }

    public static String calc(ItemStack i1, ItemStack i2) {
        // If types do not match, then the items cannot possibly match
        if (i1.getType() != i2.getType()) {
            return "neq.type";
        }

        // If amounts do not match, then the items cannot possibly match
        if (i1.getAmount() > i2.getAmount()) {
            return "neq.amount";
        }

        if (StackUtils.isBlacklisted(i1) || StackUtils.isBlacklisted(i2)) {
            return "banned.blacklist";
        }

        // If either item does not have a meta then either a mismatch or both without meta = vanilla
        if (!i1.hasItemMeta() || !i2.hasItemMeta()) {
            if (i1.hasItemMeta() == i2.hasItemMeta()) {
                return "eq";
            }
            return "neq.hasmeta";
        }

        // Now we need to compare meta's directly - cache is already out, but let's fetch the 2nd meta also
        final ItemMeta itemMeta = i1.getItemMeta();
        final ItemMeta cachedMeta = i2.getItemMeta();

        if (itemMeta == null || cachedMeta == null) {
            if (itemMeta == cachedMeta) {
                return "eq";
            }
            return "neq.hasmeta2";
        }

        // ItemMetas are different types and cannot match
        if (!itemMeta.getClass().equals(cachedMeta.getClass())) {
            return "neq.metaclass";
        }

        // Quick meta-extension escapes
        var r = canQuickEscapeMetaVariant(itemMeta, cachedMeta);
        if (r != null) {
            return r;
        }

        // Has a display name (checking the name occurs later)
        if (itemMeta.hasDisplayName() != cachedMeta.hasDisplayName()) {
            return "neq.hasdisplayname";
        }

        // PDCs don't match
        if (!itemMeta.getPersistentDataContainer().equals(cachedMeta.getPersistentDataContainer())) {
            return "neq.pdc";
        }

        // Make sure enchantments match
        if (!itemMeta.getEnchants().equals(cachedMeta.getEnchants())) {
            return "neq.enchant";
        }

        // Check item flags
        if (!itemMeta.getItemFlags().equals(cachedMeta.getItemFlags())) {
            return "neq.itemflag";
        }

        // Check the attribute modifiers
        final boolean hasAttributeOne = itemMeta.hasAttributeModifiers();
        final boolean hasAttributeTwo = cachedMeta.hasAttributeModifiers();
        if (hasAttributeOne) {
            if (!hasAttributeTwo
                || !Objects.equals(itemMeta.getAttributeModifiers(), cachedMeta.getAttributeModifiers())) {
                return "neq.attribute";
            }
        } else if (hasAttributeTwo) {
            return "neq.attribute";
        }

        if (StackUtils.IS_1_20_5) {
            // Check if fire-resistant
            if (itemMeta.isFireResistant() != cachedMeta.isFireResistant()) {
                return "neq.fireresistant";
            }

            // Check if unbreakable
            if (itemMeta.isUnbreakable() != cachedMeta.isUnbreakable()) {
                return "neq.unbreakable";
            }

            // Check if hide tooltip
            if (itemMeta.isHideTooltip() != cachedMeta.isHideTooltip()) {
                return "neq.hidetooltip";
            }

            // Check rarity
            final boolean hasRarityOne = itemMeta.hasRarity();
            final boolean hasRarityTwo = cachedMeta.hasRarity();
            if (hasRarityOne) {
                if (!hasRarityTwo || itemMeta.getRarity() != cachedMeta.getRarity()) {
                    return "neq.rarity";
                }
            } else if (hasRarityTwo) {
                return "neq.rarity";
            }

            // Check food components
            if (itemMeta.hasFood() && cachedMeta.hasFood()) {
                if (!Objects.equals(itemMeta.getFood(), cachedMeta.getFood())) {
                    return "neq.food";
                }
            } else if (itemMeta.hasFood() != cachedMeta.hasFood()) {
                return "neq.food";
            }

            // Check tool components
            if (itemMeta.hasTool() && cachedMeta.hasTool()) {
                if (!Objects.equals(itemMeta.getTool(), cachedMeta.getTool())) {
                    return "neq.tool";
                }
            } else if (itemMeta.hasTool() != cachedMeta.hasTool()) {
                return "neq.tool";
            }

            if (StackUtils.IS_1_21) {
                // Check jukebox playable
                if (itemMeta.hasJukeboxPlayable() && cachedMeta.hasJukeboxPlayable()) {
                    if (!Objects.equals(itemMeta.getJukeboxPlayable(), cachedMeta.getJukeboxPlayable())) {
                        return "neq.jukeboxplayable";
                    }
                } else if (itemMeta.hasJukeboxPlayable() != cachedMeta.hasJukeboxPlayable()) {
                    return "neq.jukeboxplayable";
                }
            }
        }

        if (true) {
            if (itemMeta.hasLore() && cachedMeta.hasLore()) {
                if (!Objects.equals(itemMeta.getLore(), cachedMeta.getLore())) {
                    return "neq.lore";
                }
            } else if (itemMeta.hasLore() != cachedMeta.hasLore()) {
                return "neq.lore";
            }
        }

        // Slimefun ID check no need to worry about distinction, covered in PDC + lore
        final Optional<String> optionalStackId1 = Slimefun.getItemDataService().getItemData(itemMeta);
        final Optional<String> optionalStackId2 = Slimefun.getItemDataService().getItemData(cachedMeta);
        if (optionalStackId1.isPresent() != optionalStackId2.isPresent()) {
            return "neq.sfid";
        }
        if (optionalStackId1.isPresent()) {
            if (optionalStackId1.get().equals(optionalStackId2.get())) {
                if (true) {
                    // Custom model data is different, no match
                    final boolean hasCustomOne = itemMeta.hasCustomModelData();
                    final boolean hasCustomTwo = cachedMeta.hasCustomModelData();
                    if (hasCustomOne) {
                        if (!hasCustomTwo || itemMeta.getCustomModelData() != cachedMeta.getCustomModelData()) {
                            return "neq.custommodeldata";
                        }
                    } else {
                        if (!hasCustomTwo) {
                            return "eq";
                        } else {
                            return "neq.custommodeldata";
                        }
                    }
                }
                return "eq";
            }
            return "neq.sfid";
        }

        // Check the display name
        if (!itemMeta.hasDisplayName() || Objects.equals(itemMeta.getDisplayName(), cachedMeta.getDisplayName())) {
            return "eq";
        } else {
            return "neq.displayname";
        }

        // Everything should match if we've managed to get here
    }

    @SuppressWarnings("removal")
    public static String canQuickEscapeMetaVariant(@NotNull ItemMeta metaOne, @NotNull ItemMeta metaTwo) {
        // Damageable (first as everything can be damageable apparently)
        if (metaOne instanceof Damageable instanceOne && metaTwo instanceof Damageable instanceTwo) {
            if (instanceOne.hasDamage() != instanceTwo.hasDamage()) {
                return "neq.damageable.damage.has";
            }

            if (instanceOne.getDamage() != instanceTwo.getDamage()) {
                return "neq.damageable.damage.get";
            }
        }

        if (metaOne instanceof Repairable instanceOne && metaTwo instanceof Repairable instanceTwo) {
            if (instanceOne.hasRepairCost() != instanceTwo.hasRepairCost()) {
                return "neq.repairable.repaircost.has";
            }

            if (instanceOne.getRepairCost() != instanceTwo.getRepairCost()) {
                return "neq.repairable.repaircost.get";
            }
        }

        // Axolotl
        if (metaOne instanceof AxolotlBucketMeta instanceOne && metaTwo instanceof AxolotlBucketMeta instanceTwo) {
            if (instanceOne.hasVariant() != instanceTwo.hasVariant()) {
                return "neq.axolotl.variant.has";
            }

            if (!instanceOne.hasVariant() || !instanceTwo.hasVariant()) {
                return "neq.axolotl.variant.has";
            }

            if (instanceOne.getVariant() != instanceTwo.getVariant()) {
                return "neq.axolotl.variant.get";
            }
        }

        // Banner
        if (metaOne instanceof BannerMeta instanceOne && metaTwo instanceof BannerMeta instanceTwo) {
            if (instanceOne.numberOfPatterns() != instanceTwo.numberOfPatterns()) {
                return "neq.banner.pattern.number";
            }

            if (!instanceOne.getPatterns().equals(instanceTwo.getPatterns())) {
                return "neq.banner.pattern.get";
            }
        }

        // BlockData
        if (metaOne instanceof BlockDataMeta instanceOne && metaTwo instanceof BlockDataMeta instanceTwo) {
            if (instanceOne.hasBlockData() != instanceTwo.hasBlockData()) {
                return "neq.blockdata.has";
            }
        }

        // BlockState
        if (metaOne instanceof BlockStateMeta instanceOne && metaTwo instanceof BlockStateMeta instanceTwo) {
            if (instanceOne.hasBlockState() != instanceTwo.hasBlockState()) {
                return "neq.blockstate.has";
            }

            if (!instanceOne.getBlockState().equals(instanceTwo.getBlockState())) {
                return "neq.blockstate.get";
            }
        }

        // Books
        if (metaOne instanceof BookMeta instanceOne && metaTwo instanceof BookMeta instanceTwo) {
            if (instanceOne.getPageCount() != instanceTwo.getPageCount()) {
                return "neq.book.pagecount.get";
            }
            if (!Objects.equals(instanceOne.getAuthor(), instanceTwo.getAuthor())) {
                return "neq.book.author.get";
            }
            if (!Objects.equals(instanceOne.getTitle(), instanceTwo.getTitle())) {
                return "neq.book.title.get";
            }
            if (!Objects.equals(instanceOne.getGeneration(), instanceTwo.getGeneration())) {
                return "neq.book.generation.get";
            }
        }

        // Bundle
        if (metaOne instanceof BundleMeta instanceOne && metaTwo instanceof BundleMeta instanceTwo) {
            // Patch start - No bundle allowed
            if (true) return "neq.bundle.banned";
            // Patch end - No bundle allowed
            if (instanceOne.hasItems() != instanceTwo.hasItems()) {
                return "neq.bundle.items.has";
            }
            if (!instanceOne.getItems().equals(instanceTwo.getItems())) {
                return "neq.bundle.items.get";
            }
        }

        // Compass
        if (metaOne instanceof CompassMeta instanceOne && metaTwo instanceof CompassMeta instanceTwo) {
            if (instanceOne.isLodestoneTracked() != instanceTwo.isLodestoneTracked()) {
                return "neq.compass.lodestone.tracked";
            }
            if (!Objects.equals(instanceOne.getLodestone(), instanceTwo.getLodestone())) {
                return "neq.compass.lodestone.get";
            }
        }

        // Crossbow
        if (metaOne instanceof CrossbowMeta instanceOne && metaTwo instanceof CrossbowMeta instanceTwo) {
            if (instanceOne.hasChargedProjectiles() != instanceTwo.hasChargedProjectiles()) {
                return "neq.crossbow.charged.has";
            }
            if (!instanceOne.getChargedProjectiles().equals(instanceTwo.getChargedProjectiles())) {
                return "neq.crossbow.charged.get";
            }
        }

        // Enchantment Storage
        if (metaOne instanceof EnchantmentStorageMeta instanceOne
            && metaTwo instanceof EnchantmentStorageMeta instanceTwo) {
            if (instanceOne.hasStoredEnchants() != instanceTwo.hasStoredEnchants()) {
                return "neq.enchantment.has";
            }
            if (!instanceOne.getStoredEnchants().equals(instanceTwo.getStoredEnchants())) {
                return "neq.enchantment.get";
            }
        }

        // Firework Star
        if (metaOne instanceof FireworkEffectMeta instanceOne && metaTwo instanceof FireworkEffectMeta instanceTwo) {
            if (!Objects.equals(instanceOne.getEffect(), instanceTwo.getEffect())) {
                return "neq.fireworkeffect.get";
            }
        }

        // Firework
        if (metaOne instanceof FireworkMeta instanceOne && metaTwo instanceof FireworkMeta instanceTwo) {
            if (instanceOne.getPower() != instanceTwo.getPower()) {
                return "neq.firework.power.get";
            }
            if (!instanceOne.getEffects().equals(instanceTwo.getEffects())) {
                return "neq.firework.effect.get";
            }
        }

        // Leather Armor
        if (metaOne instanceof LeatherArmorMeta instanceOne && metaTwo instanceof LeatherArmorMeta instanceTwo) {
            if (!instanceOne.getColor().equals(instanceTwo.getColor())) {
                return "neq.leatherarmor.color.get";
            }
        }

        // Maps
        if (metaOne instanceof MapMeta instanceOne && metaTwo instanceof MapMeta instanceTwo) {
            if (instanceOne.hasMapView() != instanceTwo.hasMapView()) {
                return "neq.map.view.has";
            }
            if (instanceOne.hasLocationName() != instanceTwo.hasLocationName()) {
                return "neq.map.loactionname.has";
            }
            if (instanceOne.hasColor() != instanceTwo.hasColor()) {
                return "neq.map.color.has";
            }
            if (!Objects.equals(instanceOne.getMapView(), instanceTwo.getMapView())) {
                return "neq.map.view.get";
            }
            if (!Objects.equals(instanceOne.getLocationName(), instanceTwo.getLocationName())) {
                return "neq.map.locationname.get";
            }
            if (!Objects.equals(instanceOne.getColor(), instanceTwo.getColor())) {
                return "neq.map.color.get";
            }
        }

        // Potion
        if (metaOne instanceof PotionMeta instanceOne && metaTwo instanceof PotionMeta instanceTwo) {
            if (StackUtils.IS_1_20_5) {
                if (instanceOne.getBasePotionType() != instanceTwo.getBasePotionType()) {
                    return "neq.potion.type.get";
                }
            } else {
                if (!Objects.equals(instanceOne.getBasePotionData(), instanceTwo.getBasePotionData())) {
                    return "neq.potion.data.get";
                }
            }
            if (instanceOne.hasCustomEffects() != instanceTwo.hasCustomEffects()) {
                return "neq.potion.customeeffect.has";
            }
            if (instanceOne.hasColor() != instanceTwo.hasColor()) {
                return "neq.potion.color.has";
            }
            if (!Objects.equals(instanceOne.getColor(), instanceTwo.getColor())) {
                return "neq.potion.color.get";
            }
            if (!instanceOne.getCustomEffects().equals(instanceTwo.getCustomEffects())) {
                return "neq.potion.customeffect.get";
            }
        }

        // Skull
        if (metaOne instanceof SkullMeta instanceOne && metaTwo instanceof SkullMeta instanceTwo) {
            if (instanceOne.hasOwner() != instanceTwo.hasOwner()) {
                return "neq.skull.owner.has";
            }
            if (!Objects.equals(instanceOne.getOwningPlayer(), instanceTwo.getOwningPlayer())) {
                return "neq.skull.owning.get";
            }
        }

        // Stew
        if (metaOne instanceof SuspiciousStewMeta instanceOne && metaTwo instanceof SuspiciousStewMeta instanceTwo) {
            if (instanceOne.hasCustomEffects() != instanceTwo.hasCustomEffects()) {
                return "neq.suspiciousstew.customeffect.has";
            }

            if (!Objects.equals(instanceOne.getCustomEffects(), instanceTwo.getCustomEffects())) {
                return "neq.suspiciousstew.customeffect.get";
            }
        }

        // Fish Bucket
        if (metaOne instanceof TropicalFishBucketMeta instanceOne
            && metaTwo instanceof TropicalFishBucketMeta instanceTwo) {
            if (instanceOne.hasVariant() != instanceTwo.hasVariant()) {
                return "neq.tropical.variant.has";
            }
            if (!instanceOne.getPattern().equals(instanceTwo.getPattern())) {
                return "neq.tropical.pattern.get";
            }
            if (!instanceOne.getBodyColor().equals(instanceTwo.getBodyColor())) {
                return "neq.tropical.bodycolor.get";
            }
            if (!instanceOne.getPatternColor().equals(instanceTwo.getPatternColor())) {
                return "neq.tropical.patterncolor.get";
            }
        }

        // Knowledge Book
        if (metaOne instanceof KnowledgeBookMeta instanceOne && metaTwo instanceof KnowledgeBookMeta instanceTwo) {
            if (instanceOne.hasRecipes() != instanceTwo.hasRecipes()) {
                return "neq.knowledge.recipe.has";
            }

            if (!Objects.equals(instanceOne.getRecipes(), instanceTwo.getRecipes())) {
                return "neq.knowledge.recipe.get";
            }
        }

        // Music Instrument
        if (metaOne instanceof MusicInstrumentMeta instanceOne && metaTwo instanceof MusicInstrumentMeta instanceTwo) {
            if (!Objects.equals(instanceOne.getInstrument(), instanceTwo.getInstrument())) {
                return "neq.music.instrument.get";
            }
        }

        // Armor
        if (metaOne instanceof ArmorMeta instanceOne && metaTwo instanceof ArmorMeta instanceTwo) {
            if (!Objects.equals(instanceOne.getTrim(), instanceTwo.getTrim())) {
                return "neq.armor.trim";
            }
        }

        if (StackUtils.IS_1_20_5) {
            // Writable Book
            if (metaOne instanceof WritableBookMeta instanceOne && metaTwo instanceof WritableBookMeta instanceTwo) {
                if (instanceOne.getPageCount() != instanceTwo.getPageCount()) {
                    return "neq.writablebook.page.count";
                }
                if (!Objects.equals(instanceOne.getPages(), instanceTwo.getPages())) {
                    return "neq.writablebook.page.get";
                }
            }
            if (StackUtils.IS_1_21) {
                // Ominous Bottle
                if (metaOne instanceof OminousBottleMeta instanceOne
                    && metaTwo instanceof OminousBottleMeta instanceTwo) {
                    if (instanceOne.hasAmplifier() != instanceTwo.hasAmplifier()) {
                        return "neq.ominous.amplifier.has";
                    }

                    if (instanceOne.getAmplifier() != instanceTwo.getAmplifier()) {
                        return "neq.ominous.amplifier.get";
                    }
                }
                // Shield
                if (metaOne instanceof ShieldMeta instanceOne && metaTwo instanceof ShieldMeta instanceTwo) {
                    if (!Objects.equals(instanceOne.getBaseColor(), instanceTwo.getBaseColor())) {
                        return "neq.shield.basecolor";
                    }
                }
            }
        }

        // Cannot escape via any meta extension check
        return null;
    }
}

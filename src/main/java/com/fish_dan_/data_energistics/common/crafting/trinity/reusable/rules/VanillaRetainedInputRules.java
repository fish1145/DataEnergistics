package com.fish_dan_.data_energistics.common.crafting.trinity.reusable.rules;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;
import com.fish_dan_.data_energistics.common.crafting.trinity.reusable.endpoint.NativeReusableCrafting;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BannerItem;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.BannerDuplicateRecipe;
import net.minecraft.world.item.crafting.BookCloningRecipe;
import net.minecraft.world.level.block.entity.BannerPatternLayers;

import java.util.Optional;

/** Vanilla cloning recipes explicitly return one unchanged original; no sampled remainder is used as a proof. */
public final class VanillaRetainedInputRules implements ReusableInputRuleAdapter {

    private static final ResourceLocation ID = Data_Energistics.id("vanilla_retained_input");
    private static final ResourceLocation BOOK = ResourceLocation.withDefaultNamespace("book_cloning");
    private static final ResourceLocation BANNER = ResourceLocation.withDefaultNamespace("banner_duplicate");

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean mayMatch(IPatternDetails pattern, Optional<ResourceLocation> recipeId) {
        return NativeReusableCrafting.usesNativeRecipeValidation(pattern, recipeId) &&
                recipeId.filter(id -> id.equals(BOOK) || id.equals(BANNER)).isPresent();
    }

    @Override
    public Optional<ReusableInputRule> resolve(ReusableInputContext context) {
        if (!mayMatch(context.pattern(), context.recipeId())) {
            return Optional.empty();
        }
        var holder = context.level().getRecipeManager().byKey(context.recipeId().orElseThrow());
        if (holder.isEmpty()) {
            return Optional.empty();
        }
        AEItemKey key = (AEItemKey) context.actualInput().what();
        var stack = key.toStack();
        if (stack.hasCraftingRemainingItem()) {
            // These recipes give item-provided remainder hooks precedence over retaining the original.
            return Optional.empty();
        }
        var recipe = holder.orElseThrow().value();
        boolean originalBook = recipe.getClass() == BookCloningRecipe.class && stack.is(Items.WRITTEN_BOOK);
        boolean originalBanner = recipe.getClass() == BannerDuplicateRecipe.class && stack.getItem() instanceof BannerItem &&
                !stack.getOrDefault(DataComponents.BANNER_PATTERNS, BannerPatternLayers.EMPTY).layers().isEmpty();
        return originalBook || originalBanner ? Optional.of(ReusableInputRule.unchanged(ID, 1L, key)) : Optional.empty();
    }
}

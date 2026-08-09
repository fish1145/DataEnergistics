package com.fish_dan_.data_energistics.mixin.viewer.jei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.jei.transfer.JeiPatternTransferContextBridge;
import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesClient;
import com.fish_dan_.data_energistics.client.transfer.PatternEncodingViewerContext;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.common.transfer.RecipeTransferErrorInternal;
import mezz.jei.common.transfer.RecipeTransferUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.Optional;

/**
 * Captures the exact JEI recipe type around the real transfer handler call.
 */
@Mixin(value = RecipeTransferUtil.class, remap = false)
public abstract class RecipeTransferUtilMixin {

    @Unique
    private static final String TRANSFER_METHOD = "transferRecipe(Lmezz/jei/api/recipe/transfer/IRecipeTransferManager;" + "Lnet/minecraft/world/inventory/AbstractContainerMenu;" + "Lmezz/jei/api/gui/IRecipeLayoutDrawable;" + "Lnet/minecraft/world/entity/player/Player;ZZ)Ljava/util/Optional;";

    @WrapMethod(method = TRANSFER_METHOD)
    private static Optional<IRecipeTransferError> dataEnergistics$captureTransferContext(
                                                                                         IRecipeTransferManager recipeTransferManager,
                                                                                         AbstractContainerMenu container,
                                                                                         IRecipeLayoutDrawable<?> recipeLayout,
                                                                                         Player player,
                                                                                         boolean maxTransfer,
                                                                                         boolean doTransfer,
                                                                                         Operation<Optional<IRecipeTransferError>> original) {
        if (!doTransfer || !(container instanceof PatternEncodingTermMenu menu)) {
            return original.call(recipeTransferManager, container, recipeLayout, player, maxTransfer, doTransfer);
        }
        PatternEncodingRankingContext context;
        try {
            context = JeiPatternTransferContextBridge.resolve(recipeLayout);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Rejected JEI pattern transfer because its recipe-type context could not be resolved",
                    exception);
            PatternEncodingPreferencesClient.clearTransferredRecipeContext(menu);
            return Optional.of(RecipeTransferErrorInternal.INSTANCE);
        }
        Optional<IRecipeTransferError> result = original.call(recipeTransferManager, container, recipeLayout, player, maxTransfer, true);
        if (result.isEmpty()) {
            Recipe<?> recipe = recipeLayout.getRecipe() instanceof RecipeHolder<?> holder ? holder.value() :
                    recipeLayout.getRecipe() instanceof Recipe<?> value ? value : null;
            EncodingMode transferMode = PatternEncodingViewerContext.resolveEncodingMode(recipe, false);
            PatternEncodingSourceHelper.rememberTransferSource(menu, transferMode, context);
            if (transferMode == EncodingMode.PROCESSING) {
                PatternEncodingPreferencesClient.captureTransferredProcessingRecipe(menu, context);
            } else {
                PatternEncodingPreferencesClient.captureTransferredRecipe(menu);
            }
        }
        return result;
    }
}

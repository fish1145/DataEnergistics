package com.fish_dan_.data_energistics.mixin.jei;

import com.fish_dan_.data_energistics.integration.xei.recipe.DataRipperReassemblerRecipeView;
import com.fish_dan_.data_energistics.integration.xei.transfer.PatternEncodingViewerContext;
import com.fish_dan_.data_energistics.menu.patternencoding.source.PatternEncodingSourceHelper;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import tamaized.ae2jeiintegration.integration.modules.jei.transfer.EncodePatternTransferHandler;

@Mixin(value = EncodePatternTransferHandler.class, remap = false)
public abstract class JeiEncodePatternTransferHandlerMixin {

    @Inject(
            method = "transferRecipe(Lnet/minecraft/world/inventory/AbstractContainerMenu;Ljava/lang/Object;Lmezz/jei/api/gui/ingredient/IRecipeSlotsView;Lnet/minecraft/world/entity/player/Player;ZZ)Lmezz/jei/api/recipe/transfer/IRecipeTransferError;",
            at = @At("RETURN"))
    private void dataEnergistics$rememberPatternSource(AbstractContainerMenu menu, Object recipe,
                                                       IRecipeSlotsView recipeSlots, Player player,
                                                       boolean maxTransfer, boolean doTransfer,
                                                       CallbackInfoReturnable<Object> cir) {
        if (!doTransfer || cir.getReturnValue() != null) {
            return;
        }

        if (menu instanceof PatternEncodingTermMenu patternEncodingTermMenu) {
            Recipe<?> transferredRecipe = recipe instanceof RecipeHolder<?> holder ? holder.value() :
                    recipe instanceof Recipe<?> value ? value : null;
            EncodingMode transferMode = PatternEncodingViewerContext.resolveEncodingMode(transferredRecipe, false);
            if (transferMode == EncodingMode.PROCESSING && recipe instanceof DataRipperReassemblerRecipeView view) {
                PatternEncodingSourceHelper.rememberDataRipperTransferMetadata(
                        patternEncodingTermMenu,
                        view.keyInput(),
                        view.keyOutput(),
                        view.fluidInputs(),
                        view.fluidOutputs());
            } else {
                PatternEncodingSourceHelper.rememberDataRipperTransferMetadata(
                        patternEncodingTermMenu, transferMode, recipe, recipeSlots);
            }
        }
    }
}

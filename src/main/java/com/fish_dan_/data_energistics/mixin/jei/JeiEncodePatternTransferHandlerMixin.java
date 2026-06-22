package com.fish_dan_.data_energistics.mixin.jei;

import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import appeng.menu.me.items.PatternEncodingTermMenu;
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
            PatternEncodingSourceHelper.rememberTransferSource(patternEncodingTermMenu, recipe, recipeSlots);
            PatternEncodingSourceHelper.rememberTransferKeyInput(patternEncodingTermMenu, recipe, recipeSlots);
            PatternEncodingSourceHelper.rememberTransferKeyOutput(patternEncodingTermMenu, recipe, recipeSlots);
            PatternEncodingSourceHelper.rememberTransferFluidInputs(patternEncodingTermMenu, recipe, recipeSlots);
            PatternEncodingSourceHelper.rememberTransferFluidOutputs(patternEncodingTermMenu, recipe, recipeSlots);
        }
    }
}

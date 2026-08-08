package com.fish_dan_.data_energistics.mixin.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.emi.transfer.EmiPatternTransferContextBridge;
import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesClient;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;
import com.fish_dan_.data_energistics.util.PatternEncodingSourceHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.integration.modules.emi.AbstractRecipeHandler;
import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.emi.emi.api.recipe.EmiRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EmiEncodePatternHandler.class, remap = false)
public abstract class EmiEncodePatternHandlerMixin {

    @Unique
    private static final String TRANSFER_METHOD = "transferRecipe(Lappeng/menu/me/items/PatternEncodingTermMenu;" + "Lnet/minecraft/world/item/crafting/RecipeHolder;" + "Ldev/emi/emi/api/recipe/EmiRecipe;Z)" + "Lappeng/integration/modules/emi/AbstractRecipeHandler$Result;";

    @WrapMethod(method = TRANSFER_METHOD)
    private AbstractRecipeHandler.Result dataEnergistics$captureTransferContext(
                                                                                PatternEncodingTermMenu menu,
                                                                                RecipeHolder<?> holder,
                                                                                EmiRecipe emiRecipe,
                                                                                boolean doTransfer,
                                                                                Operation<AbstractRecipeHandler.Result> original) {
        if (!doTransfer) {
            return original.call(menu, holder, emiRecipe, false);
        }
        PatternEncodingRankingContext context;
        try {
            context = EmiPatternTransferContextBridge.resolve(emiRecipe);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Rejected EMI pattern transfer because its category/workstation context could not be resolved",
                    exception);
            PatternEncodingPreferencesClient.clearTransferredRecipeContext(menu);
            return AbstractRecipeHandler.Result.createFailed(
                    Component.translatable("data_energistics.pattern_transfer.context_unavailable"));
        }
        EmiPatternTransferContextBridge.begin(menu, context);
        try {
            return original.call(menu, holder, emiRecipe, true);
        } finally {
            try {
                EmiPatternTransferContextBridge.end(menu);
            } catch (RuntimeException exception) {
                Data_Energistics.LOGGER.error("Failed to close the scoped EMI pattern transfer context", exception);
            }
        }
    }

    @Inject(
            method = TRANSFER_METHOD,
            at = @At(
                     value = "INVOKE",
                     target = "Lappeng/integration/modules/emi/AbstractRecipeHandler$Result;createSuccessful()Lappeng/integration/modules/emi/AbstractRecipeHandler$Result$Success;"))
    private void dataEnergistics$rememberPatternSource(PatternEncodingTermMenu menu, RecipeHolder<?> holder,
                                                       EmiRecipe emiRecipe, boolean doTransfer,
                                                       CallbackInfoReturnable<AbstractRecipeHandler.Result> cir) {
        if (!doTransfer) {
            return;
        }
        PatternEncodingRankingContext rankingContext = EmiPatternTransferContextBridge.requireCurrent(menu);
        PatternEncodingSourceHelper.rememberTransferSource(menu, rankingContext);
        PatternEncodingSourceHelper.rememberTransferKeyInput(menu, holder, emiRecipe);
        PatternEncodingSourceHelper.rememberTransferKeyOutput(menu, holder, emiRecipe);
        PatternEncodingSourceHelper.rememberTransferFluidInputs(menu, holder, emiRecipe);
        PatternEncodingSourceHelper.rememberTransferFluidOutputs(menu, holder, emiRecipe);
        if (menu.getMode() == EncodingMode.PROCESSING) {
            PatternEncodingPreferencesClient.captureTransferredProcessingRecipe(menu, rankingContext);
        } else {
            PatternEncodingPreferencesClient.captureTransferredRecipe(menu);
        }
    }
}

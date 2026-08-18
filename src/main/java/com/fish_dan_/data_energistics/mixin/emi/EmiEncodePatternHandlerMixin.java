package com.fish_dan_.data_energistics.mixin.emi;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.integration.viewer.emi.transfer.EmiPatternTransferContextBridge;
import com.fish_dan_.data_energistics.client.preferences.PatternEncodingPreferencesClient;
import com.fish_dan_.data_energistics.integration.viewer.xei.transfer.PatternEncodingViewerContext;
import com.fish_dan_.data_energistics.menu.patternencoding.PatternEncodingViewerRecipeScope;
import com.fish_dan_.data_energistics.menu.patternencoding.source.PatternEncodingSourceHelper;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.crafting.RecipeHolder;

import appeng.integration.modules.emi.AbstractRecipeHandler;
import appeng.integration.modules.emi.EmiEncodePatternHandler;
import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.VanillaEmiRecipeCategories;
import org.jspecify.annotations.Nullable;
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
                                                                                @Nullable RecipeHolder<?> holder,
                                                                                EmiRecipe emiRecipe,
                                                                                boolean doTransfer,
                                                                                Operation<AbstractRecipeHandler.Result> original) {
        if (!doTransfer) {
            return original.call(menu, holder, emiRecipe, false);
        }
        PatternEncodingViewerRecipeScope recipeScope;
        try {
            recipeScope = EmiPatternTransferContextBridge.resolve(emiRecipe);
        } catch (RuntimeException exception) {
            Data_Energistics.LOGGER.error(
                    "Rejected EMI pattern transfer because its recipe-type context could not be resolved",
                    exception);
            PatternEncodingPreferencesClient.clearTransferredRecipeContext(menu);
            return AbstractRecipeHandler.Result.createFailed(
                    Component.translatable("data_energistics.pattern_transfer.context_unavailable"));
        }
        EmiPatternTransferContextBridge.begin(menu, recipeScope);
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
    private void dataEnergistics$rememberPatternSource(PatternEncodingTermMenu menu,
                                                       @Nullable RecipeHolder<?> holder,
                                                       EmiRecipe emiRecipe, boolean doTransfer,
                                                       CallbackInfoReturnable<AbstractRecipeHandler.Result> cir) {
        if (!doTransfer) {
            return;
        }
        EncodingMode transferMode = PatternEncodingViewerContext.resolveEncodingMode(
                holder == null ? null : holder.value(),
                emiRecipe.getCategory().equals(VanillaEmiRecipeCategories.CRAFTING));
        PatternEncodingViewerRecipeScope recipeScope = EmiPatternTransferContextBridge.requireCurrent(menu);
        PatternEncodingSourceHelper.rememberTransferSource(menu, transferMode, recipeScope);
        PatternEncodingSourceHelper.rememberDataRipperTransferMetadata(menu, transferMode, holder, emiRecipe);
        if (transferMode == EncodingMode.PROCESSING) {
            PatternEncodingPreferencesClient.captureTransferredProcessingRecipe(menu, recipeScope);
        } else {
            PatternEncodingPreferencesClient.captureTransferredRecipe(menu, transferMode);
        }
    }
}

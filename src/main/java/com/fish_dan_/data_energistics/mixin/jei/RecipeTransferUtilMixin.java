package com.fish_dan_.data_energistics.mixin.jei;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.client.jei.transfer.JeiPatternTransferContextBridge;
import com.fish_dan_.data_energistics.menu.common.PatternEncodingRankingContext;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

import appeng.menu.me.items.PatternEncodingTermMenu;
import appeng.parts.encoding.EncodingMode;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferManager;
import mezz.jei.common.transfer.RecipeTransferErrorInternal;
import mezz.jei.common.transfer.RecipeTransferUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

/** Captures the exact JEI category/workstation set around the real transfer handler call. */
@Mixin(value = RecipeTransferUtil.class, remap = false)
public abstract class RecipeTransferUtilMixin {

    @Unique
    private static final String TRANSFER_METHOD =
            "transferRecipe(Lmezz/jei/api/recipe/transfer/IRecipeTransferManager;"
                    + "Lnet/minecraft/world/inventory/AbstractContainerMenu;"
                    + "Lmezz/jei/api/gui/IRecipeLayoutDrawable;"
                    + "Lnet/minecraft/world/entity/player/Player;ZZ)Ljava/util/Optional;";

    @Inject(method = TRANSFER_METHOD, at = @At("HEAD"), cancellable = true)
    private static void dataEnergistics$prepareTransfer(
            IRecipeTransferManager recipeTransferManager,
            AbstractContainerMenu container,
            IRecipeLayoutDrawable<?> recipeLayout,
            Player player,
            boolean maxTransfer,
            boolean doTransfer,
            CallbackInfoReturnable<Optional<IRecipeTransferError>> cir) {
        if (!doTransfer || !(container instanceof PatternEncodingTermMenu menu)
                || menu.getMode() != EncodingMode.PROCESSING) {
            return;
        }
        try {
            PatternEncodingRankingContext context = JeiPatternTransferContextBridge.resolve(recipeLayout);
            JeiPatternTransferContextBridge.begin(menu, context);
        } catch (RuntimeException exception) {
            JeiPatternTransferContextBridge.beginUnavailable(menu);
            Data_Energistics.LOGGER.error(
                    "Rejected JEI processing-pattern transfer because its category/workstation context could not be resolved",
                    exception);
            cir.setReturnValue(Optional.of(RecipeTransferErrorInternal.INSTANCE));
        }
    }

    @Inject(method = TRANSFER_METHOD, at = @At("RETURN"))
    private static void dataEnergistics$cleanupTransfer(
            IRecipeTransferManager recipeTransferManager,
            AbstractContainerMenu container,
            IRecipeLayoutDrawable<?> recipeLayout,
            Player player,
            boolean maxTransfer,
            boolean doTransfer,
            CallbackInfoReturnable<Optional<IRecipeTransferError>> cir) {
        if (doTransfer && container instanceof PatternEncodingTermMenu menu
                && menu.getMode() == EncodingMode.PROCESSING) {
            JeiPatternTransferContextBridge.end(menu);
        }
    }
}

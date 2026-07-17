package com.fish_dan_.data_energistics.mixin.emi;

import com.fish_dan_.data_energistics.client.emi.EmiEncodePatternHandlerMultiblockTransferGuard;

import dev.emi.emi.api.recipe.EmiRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "appeng.integration.modules.emi.AbstractRecipeHandler", remap = false)
public abstract class AbstractRecipeHandlerMixin {

    /**
     * Defers typed multiblock recipes before AE2's inherited catch-all handler claims them.
     */
    @Inject(method = "supportsRecipe", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$deferTypedMultiblockRecipe(EmiRecipe recipe,
                                                            CallbackInfoReturnable<Boolean> cir) {
        if (EmiEncodePatternHandlerMultiblockTransferGuard.shouldDefer(this, recipe)) {
            cir.setReturnValue(false);
        }
    }
}

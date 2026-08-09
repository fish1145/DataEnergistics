package com.fish_dan_.data_energistics.mixin.core.menu;

import com.fish_dan_.data_energistics.menu.trinity.TrinityAccessHatchMenu;

import appeng.helpers.patternprovider.PatternContainer;
import appeng.menu.implementations.PatternAccessTermMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Narrows AE2's grid-wide pattern-provider scan only for the Trinity access-hatch menu subtype.
 */
@Mixin(PatternAccessTermMenu.class)
public class PatternAccessTermMenuMixin {

    @Inject(
            method = "isVisible(Lappeng/helpers/patternprovider/PatternContainer;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void dataEnergistics$limitTrinityAccessHatchContainers(
                                                                   PatternContainer container,
                                                                   CallbackInfoReturnable<Boolean> callback) {
        PatternAccessTermMenu patternMenu = (PatternAccessTermMenu) (Object) this;
        if (patternMenu instanceof TrinityAccessHatchMenu menu &&
                !menu.isManagedPatternContainer(container)) {
            callback.setReturnValue(false);
        }
    }
}

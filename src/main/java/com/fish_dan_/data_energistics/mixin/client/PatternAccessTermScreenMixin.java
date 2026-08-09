package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.client.screen.trinity.TrinityAccessHatchScreen;

import net.minecraft.world.item.ItemStack;

import appeng.client.gui.me.patternaccess.PatternAccessTermScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Substitutes EAE-style input/output search text only for the Trinity access-hatch screen.
 */
@Mixin(PatternAccessTermScreen.class)
public abstract class PatternAccessTermScreenMixin {

    /**
     * Leaves every native and third-party pattern-access screen on AE2's output-only behavior.
     */
    @Inject(
            method = "getPatternSearchText(Lnet/minecraft/world/item/ItemStack;)Ljava/lang/String;",
            at = @At("HEAD"),
            cancellable = true)
    private void dataEnergistics$useTrinitySearchScope(
                                                       ItemStack itemStack,
                                                       CallbackInfoReturnable<String> callback) {
        if ((Object) this instanceof TrinityAccessHatchScreen screen) {
            callback.setReturnValue(screen.buildPatternSearchText(itemStack));
        }
    }

    /**
     * Replaces AE2's contiguous substring check with EAE's ordered token matching only for the access-hatch screen.
     */
    @Inject(
            method = "itemStackMatchesSearchTerm(Lnet/minecraft/world/item/ItemStack;Ljava/lang/String;)Z",
            at = @At("HEAD"),
            cancellable = true)
    private void dataEnergistics$matchTrinityPatternTokens(
                                                           ItemStack itemStack,
                                                           String searchTerm,
                                                           CallbackInfoReturnable<Boolean> callback) {
        if ((Object) this instanceof TrinityAccessHatchScreen screen) {
            callback.setReturnValue(screen.matchesPatternSearch(itemStack, searchTerm));
        }
    }
}

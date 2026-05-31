package com.fish_dan_.data_energistics.mixin.core;

import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import appeng.menu.AEBaseMenu;
import appeng.menu.SlotSemantics;
import appeng.menu.me.items.PatternEncodingTermMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AEBaseMenu.class)
public abstract class AEBaseMenuMixin {

    @Inject(method = "isValidForSlot", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$preventBlankPatternSlotInsertion(Slot s, ItemStack i,
                                                                  CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this instanceof PatternEncodingTermMenu patternEncodingTermMenu && patternEncodingTermMenu.getSlotSemantic(s) == SlotSemantics.BLANK_PATTERN && !s.hasItem()) {
            cir.setReturnValue(false);
        }
    }
}

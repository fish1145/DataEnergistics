package com.fish_dan_.data_energistics.mixin.core.grid;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item.TooltipContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import appeng.crafting.pattern.EncodedPatternItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(EncodedPatternItem.class)
public abstract class EncodedPatternItemMixin {

    @Inject(method = "appendHoverText", at = @At("TAIL"))
    private void dataEnergistics$appendPatternSourceTooltip(ItemStack stack,
                                                            TooltipContext context,
                                                            List<Component> lines,
                                                            TooltipFlag flags,
                                                            CallbackInfo ci) {}
}

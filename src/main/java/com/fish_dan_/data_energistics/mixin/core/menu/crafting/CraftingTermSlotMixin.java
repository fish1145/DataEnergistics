package com.fish_dan_.data_energistics.mixin.core.menu.crafting;

import com.fish_dan_.data_energistics.item.powered.PoweredCraftingEnergyHelper;

import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.level.Level;

import appeng.menu.slot.CraftingTermSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(CraftingTermSlot.class)
public abstract class CraftingTermSlotMixin {

    @Inject(
            method = "getRemainingItems",
            at = @At("RETURN"))
    private void dataEnergistics$consumePoweredRemainderEnergy(
                                                               CraftingInput ic,
                                                               Level level,
                                                               CallbackInfoReturnable<NonNullList<ItemStack>> cir) {
        NonNullList<ItemStack> remainders = cir.getReturnValue();
        PoweredCraftingEnergyHelper.consumeEnergyFromCraftingRemainders(ic, remainders);
    }
}

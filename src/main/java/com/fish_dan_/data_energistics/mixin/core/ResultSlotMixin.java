package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.util.DataCaptureBallCraftingRemainderHelper;
import com.fish_dan_.data_energistics.util.PoweredCraftingEnergyHelper;

import net.minecraft.core.NonNullList;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

    @Inject(
            method = "onTake",
            at = @At(
                     value = "INVOKE",
                     target = "Lnet/neoforged/neoforge/common/CommonHooks;setCraftingPlayer(Lnet/minecraft/world/entity/player/Player;)V",
                     shift = At.Shift.AFTER,
                     ordinal = 1),
            locals = LocalCapture.CAPTURE_FAILHARD)
    private void dataEnergistics$consumePoweredRemainderEnergy(
                                                               net.minecraft.world.entity.player.Player player,
                                                               ItemStack stack,
                                                               CallbackInfo ci,
                                                               net.minecraft.world.item.crafting.CraftingInput.Positioned positioned,
                                                               CraftingInput input,
                                                               int left,
                                                               int top,
                                                               NonNullList<ItemStack> remainders) {
        PoweredCraftingEnergyHelper.consumeEnergyFromCraftingRemainders(input, remainders);
        DataCaptureBallCraftingRemainderHelper.applyDataReassemblerRemainder(input, remainders);
    }
}

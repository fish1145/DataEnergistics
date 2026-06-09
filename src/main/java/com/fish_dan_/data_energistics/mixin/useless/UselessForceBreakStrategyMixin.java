package com.fish_dan_.data_energistics.mixin.useless;

import com.fish_dan_.data_energistics.integration.SomeUselessThingsCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.event.level.BlockEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.sorrowmist.useless.utils.mining.ForceBreakStrategy", remap = false)
public abstract class UselessForceBreakStrategyMixin {

    @Redirect(
              method = "handleBreak",
              at = @At(
                       value = "INVOKE",
                       target = "Lcom/sorrowmist/useless/utils/mining/MiningUtils;removeBlockSafely(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;)V",
                       remap = false))
    private void dataEnergistics$removeMeteoriteWithSpecialDrops(ServerLevel level,
                                                                 BlockPos pos,
                                                                 BlockEvent.BreakEvent event,
                                                                 ItemStack tool,
                                                                 Player player) {
        SomeUselessThingsCompat.beforeSpecialMiningRemove(level, pos, player, tool, UselessMiningMixinHelper.isSilkTouchMode(tool));
        SomeUselessThingsCompat.removeBlockSafely(level, pos);
    }
}

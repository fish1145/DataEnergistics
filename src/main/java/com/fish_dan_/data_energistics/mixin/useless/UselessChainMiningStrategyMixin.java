package com.fish_dan_.data_energistics.mixin.useless;

import com.fish_dan_.data_energistics.integration.useless.SomeUselessThingsCompat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.level.BlockEvent;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "com.sorrowmist.useless.utils.mining.ChainMiningStrategy", remap = false)
public abstract class UselessChainMiningStrategyMixin {

    @Redirect(
              method = "handleBreak",
              at = @At(
                       value = "INVOKE",
                       target = "Lnet/minecraft/server/level/ServerLevel;removeBlock(Lnet/minecraft/core/BlockPos;Z)Z",
                       remap = true))
    private boolean dataEnergistics$removeMeteoriteWithSpecialDrops(ServerLevel level,
                                                                    BlockPos pos,
                                                                    boolean moving,
                                                                    BlockEvent.BreakEvent event,
                                                                    ItemStack tool,
                                                                    Player player) {
        SomeUselessThingsCompat.beforeSpecialMiningRemove(level, pos, player, tool, UselessMiningMixinHelper.isSilkTouchMode(tool));
        return level.removeBlock(pos, moving);
    }
}

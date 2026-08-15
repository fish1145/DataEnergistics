package com.fish_dan_.data_energistics.mixin.core.world;

import com.fish_dan_.data_energistics.world.farmland.PersistentFarmlandSavedData;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FarmBlock.class)
public abstract class FarmBlockMixin {

    @Inject(method = "randomTick", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$keepPersistentFarmlandWet(BlockState state, ServerLevel level, BlockPos pos,
                                                           RandomSource random, CallbackInfo ci) {
        if (!PersistentFarmlandSavedData.get(level).contains(pos)) {
            return;
        }

        if (state.hasProperty(FarmBlock.MOISTURE) && state.getValue(FarmBlock.MOISTURE) != FarmBlock.MAX_MOISTURE) {
            level.setBlock(pos, state.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), 2);
        }
        ci.cancel();
    }
}

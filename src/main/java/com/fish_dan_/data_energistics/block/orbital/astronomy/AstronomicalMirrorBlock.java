package com.fish_dan_.data_energistics.block.orbital.astronomy;

import com.fish_dan_.data_energistics.blockentity.orbital.astronomy.AstronomicalMirrorBlockEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Center and exclusive-claim anchor of one 3x3 high-tier observation mirror.
 */
public final class AstronomicalMirrorBlock extends Block implements EntityBlock {

    public AstronomicalMirrorBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AstronomicalMirrorBlockEntity(pos, state);
    }
}

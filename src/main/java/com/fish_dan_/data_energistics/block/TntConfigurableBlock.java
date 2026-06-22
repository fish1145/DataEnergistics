package com.fish_dan_.data_energistics.block;

import com.fish_dan_.data_energistics.entity.TntConfigurablePrimedEntity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.TntBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;

import com.mojang.serialization.MapCodec;
import org.jetbrains.annotations.Nullable;

public class TntConfigurableBlock extends TntBlock {

    public static final MapCodec<TntConfigurableBlock> CODEC = simpleCodec(TntConfigurableBlock::new);

    public TntConfigurableBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @SuppressWarnings("unchecked")
    @Override
    public MapCodec<TntBlock> codec() {
        return (MapCodec<TntBlock>) (MapCodec<?>) CODEC;
    }

    @Override
    public void onCaughtFire(BlockState state, Level level, BlockPos pos, @Nullable Direction face,
                             @Nullable LivingEntity igniter) {
        prime(level, pos, igniter, 80);
    }

    @Override
    public void wasExploded(Level level, BlockPos pos, Explosion explosion) {
        LivingEntity igniter = explosion.getIndirectSourceEntity() instanceof LivingEntity living ? living : null;
        if (!level.isClientSide) {
            int shortenedFuse = level.random.nextInt(20) + 10;
            prime(level, pos, igniter, shortenedFuse);
        }
    }

    private static void prime(Level level, BlockPos pos, @Nullable LivingEntity igniter, int fuse) {
        if (level.isClientSide) {
            return;
        }

        TntConfigurablePrimedEntity primed = new TntConfigurablePrimedEntity(level, pos, igniter);
        primed.setFuse(fuse);
        level.addFreshEntity(primed);
        level.playSound(null, primed.getX(), primed.getY(), primed.getZ(), SoundEvents.TNT_PRIMED, SoundSource.BLOCKS,
                1.0F, 1.0F);
        level.gameEvent(igniter, GameEvent.PRIME_FUSE, pos);
    }
}

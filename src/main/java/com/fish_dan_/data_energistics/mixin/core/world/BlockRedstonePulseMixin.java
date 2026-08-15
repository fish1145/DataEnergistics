package com.fish_dan_.data_energistics.mixin.core.world;

import com.fish_dan_.data_energistics.accessor.patternprovider.RedstoneTuningAwareHost;
import com.fish_dan_.data_energistics.block.patternprovider.AdaptivePatternProviderBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.parts.IPart;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.networking.CableBusBlock;
import appeng.blockentity.networking.CableBusBlockEntity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockBehaviour.class)
public abstract class BlockRedstonePulseMixin {

    @Unique
    private static final String ADVANCED_AE_PATTERN_PROVIDER_BLOCK = "net.pedroksl.advanced_ae.common.blocks.AdvPatternProviderBlock";
    @Unique
    private static final String ADVANCED_AE_SMALL_PATTERN_PROVIDER_BLOCK = "net.pedroksl.advanced_ae.common.blocks.SmallAdvPatternProviderBlock";
    @Unique
    private static final String APPLIED_CREATE_ANDESITE_PATTERN_PROVIDER_BLOCK = "com.loliball.appliedcreate.patternprovider.AndesitePatternProviderBlock";
    @Unique
    private static final String APPLIED_CREATE_BRASS_PATTERN_PROVIDER_BLOCK = "com.loliball.appliedcreate.patternprovider.BrassPatternProviderBlock";
    @Unique
    private static final String EXTENDED_AE_PATTERN_PROVIDER_BLOCK = "com.glodblock.github.extendedae.common.blocks.BlockExPatternProvider";

    @Inject(method = "isSignalSource", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$isSignalSource(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (this.dataEnergistics$isPulseSourceBlock(state.getBlock())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getSignal", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction,
                                           CallbackInfoReturnable<Integer> cir) {
        if (this.dataEnergistics$getTuningHost(level, pos, direction) instanceof RedstoneTuningAwareHost host && host.dataEnergistics$isRedstoneTuningPulseActive()) {
            cir.setReturnValue(15);
        }
    }

    @Inject(method = "getDirectSignal", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction,
                                                 CallbackInfoReturnable<Integer> cir) {
        if (this.dataEnergistics$getTuningHost(level, pos, direction) instanceof RedstoneTuningAwareHost host && host.dataEnergistics$isRedstoneTuningPulseActive()) {
            cir.setReturnValue(15);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void dataEnergistics$tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                      CallbackInfo ci) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneTuningAwareHost host) {
            host.dataEnergistics$serverTick();
            return;
        }
        if (!(blockEntity instanceof CableBusBlockEntity cableBus)) {
            return;
        }

        for (Direction side : Direction.values()) {
            IPart part = cableBus.getPart(side);
            if (part instanceof RedstoneTuningAwareHost host) {
                host.dataEnergistics$serverTick();
            }
        }
    }

    @Unique
    private boolean dataEnergistics$isPulseSourceBlock(Block block) {
        if (block instanceof PatternProviderBlock || block instanceof AdaptivePatternProviderBlock<?> || block instanceof CableBusBlock) {
            return true;
        }

        String blockClassName = block.getClass().getName();
        return ADVANCED_AE_PATTERN_PROVIDER_BLOCK.equals(blockClassName) || ADVANCED_AE_SMALL_PATTERN_PROVIDER_BLOCK.equals(blockClassName) || APPLIED_CREATE_ANDESITE_PATTERN_PROVIDER_BLOCK.equals(blockClassName) || APPLIED_CREATE_BRASS_PATTERN_PROVIDER_BLOCK.equals(blockClassName) || EXTENDED_AE_PATTERN_PROVIDER_BLOCK.equals(blockClassName);
    }

    @Unique
    private RedstoneTuningAwareHost dataEnergistics$getTuningHost(BlockGetter level, BlockPos pos,
                                                                  @Nullable Direction direction) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof RedstoneTuningAwareHost host) {
            return host;
        }
        if (direction == null || !(blockEntity instanceof CableBusBlockEntity cableBus)) {
            return null;
        }

        IPart oppositePart = cableBus.getPart(direction.getOpposite());
        if (oppositePart instanceof RedstoneTuningAwareHost host) {
            return host;
        }

        IPart sameSidePart = cableBus.getPart(direction);
        return sameSidePart instanceof RedstoneTuningAwareHost host ? host : null;
    }
}

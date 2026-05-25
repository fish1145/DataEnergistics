package com.fish_dan_.data_energistics.mixin;

import appeng.api.parts.IPart;
import appeng.block.crafting.PatternProviderBlock;
import appeng.block.networking.CableBusBlock;
import appeng.blockentity.networking.CableBusBlockEntity;
import com.fish_dan_.data_energistics.block.AdaptivePatternProviderBlock;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.slf4j.Logger;

import java.lang.reflect.Method;

@Mixin(BlockBehaviour.class)
public abstract class BlockRedstonePulseMixin {
    @Unique
    private static final Logger DATA_ENERGISTICS$LOGGER = LogUtils.getLogger();
    @Unique
    private static final String ADVANCED_AE_PATTERN_PROVIDER_BLOCK =
            "net.pedroksl.advanced_ae.common.blocks.AdvPatternProviderBlock";
    @Unique
    private static final String ADVANCED_AE_SMALL_PATTERN_PROVIDER_BLOCK =
            "net.pedroksl.advanced_ae.common.blocks.SmallAdvPatternProviderBlock";
    @Unique
    private static final String APPLIED_CREATE_ANDESITE_PATTERN_PROVIDER_BLOCK =
            "com.loliball.appliedcreate.patternprovider.AndesitePatternProviderBlock";
    @Unique
    private static final String APPLIED_CREATE_BRASS_PATTERN_PROVIDER_BLOCK =
            "com.loliball.appliedcreate.patternprovider.BrassPatternProviderBlock";
    @Unique
    private static final String EXTENDED_AE_PATTERN_PROVIDER_BLOCK =
            "com.glodblock.github.extendedae.common.blocks.BlockExPatternProvider";
    @Unique
    private static final String REDSTONE_TUNING_AWARE_HOST_CLASS =
            "com.fish_dan_.data_energistics.accessor.RedstoneTuningAwareHost";
    @Unique
    private static final String PULSE_ACTIVE_METHOD = "dataEnergistics$isRedstoneTuningPulseActive";
    @Unique
    private static final String SERVER_TICK_METHOD = "dataEnergistics$serverTick";
    @Unique
    private static Class<?> dataEnergistics$redstoneTuningAwareHostClass;
    @Unique
    private static Method dataEnergistics$isPulseActiveMethod;
    @Unique
    private static Method dataEnergistics$serverTickMethod;
    @Unique
    private static boolean dataEnergistics$redstoneHostMethodsResolved;
    @Unique
    private static boolean dataEnergistics$redstoneHostMethodsAvailable;
    @Unique
    private static boolean dataEnergistics$redstoneHostResolutionLogged;

    @Inject(method = "isSignalSource", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$isSignalSource(BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (this.dataEnergistics$isPulseSourceBlock(state.getBlock())) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "getSignal", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction,
                                           CallbackInfoReturnable<Integer> cir) {
        Object host = this.dataEnergistics$getTuningHost(level, pos, direction);
        if (this.dataEnergistics$isRedstoneTuningPulseActive(host)) {
            cir.setReturnValue(15);
        }
    }

    @Inject(method = "getDirectSignal", at = @At("HEAD"), cancellable = true)
    private void dataEnergistics$getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction,
                                                  CallbackInfoReturnable<Integer> cir) {
        Object host = this.dataEnergistics$getTuningHost(level, pos, direction);
        if (this.dataEnergistics$isRedstoneTuningPulseActive(host)) {
            cir.setReturnValue(15);
        }
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void dataEnergistics$tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random,
                                      CallbackInfo ci) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (this.dataEnergistics$invokeServerTick(blockEntity)) {
            return;
        }
        if (!(blockEntity instanceof CableBusBlockEntity cableBus)) {
            return;
        }

        for (Direction side : Direction.values()) {
            IPart part = cableBus.getPart(side);
            this.dataEnergistics$invokeServerTick(part);
        }
    }

    @Unique
    private boolean dataEnergistics$isPulseSourceBlock(Block block) {
        if (block instanceof PatternProviderBlock
                || block instanceof AdaptivePatternProviderBlock<?>
                || block instanceof CableBusBlock) {
            return true;
        }

        String blockClassName = block.getClass().getName();
        return ADVANCED_AE_PATTERN_PROVIDER_BLOCK.equals(blockClassName)
                || ADVANCED_AE_SMALL_PATTERN_PROVIDER_BLOCK.equals(blockClassName)
                || APPLIED_CREATE_ANDESITE_PATTERN_PROVIDER_BLOCK.equals(blockClassName)
                || APPLIED_CREATE_BRASS_PATTERN_PROVIDER_BLOCK.equals(blockClassName)
                || EXTENDED_AE_PATTERN_PROVIDER_BLOCK.equals(blockClassName);
    }

    @Unique
    private Object dataEnergistics$getTuningHost(BlockGetter level, BlockPos pos, @Nullable Direction direction) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (this.dataEnergistics$isRedstoneTuningAwareHost(blockEntity)) {
            return blockEntity;
        }
        if (direction == null || !(blockEntity instanceof CableBusBlockEntity cableBus)) {
            return null;
        }

        IPart oppositePart = cableBus.getPart(direction.getOpposite());
        if (this.dataEnergistics$isRedstoneTuningAwareHost(oppositePart)) {
            return oppositePart;
        }

        IPart sameSidePart = cableBus.getPart(direction);
        return this.dataEnergistics$isRedstoneTuningAwareHost(sameSidePart) ? sameSidePart : null;
    }

    @Unique
    private boolean dataEnergistics$isRedstoneTuningAwareHost(@Nullable Object candidate) {
        return candidate != null
                && this.dataEnergistics$ensureRedstoneHostMethods()
                && dataEnergistics$redstoneTuningAwareHostClass.isInstance(candidate);
    }

    @Unique
    private boolean dataEnergistics$isRedstoneTuningPulseActive(@Nullable Object host) {
        if (!this.dataEnergistics$isRedstoneTuningAwareHost(host)) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(dataEnergistics$isPulseActiveMethod.invoke(host));
        } catch (ReflectiveOperationException ex) {
            this.dataEnergistics$logRedstoneHostResolutionFailure(ex);
            return false;
        }
    }

    @Unique
    private boolean dataEnergistics$invokeServerTick(@Nullable Object host) {
        if (!this.dataEnergistics$isRedstoneTuningAwareHost(host)) {
            return false;
        }

        try {
            dataEnergistics$serverTickMethod.invoke(host);
            return true;
        } catch (ReflectiveOperationException ex) {
            this.dataEnergistics$logRedstoneHostResolutionFailure(ex);
            return false;
        }
    }

    @Unique
    private boolean dataEnergistics$ensureRedstoneHostMethods() {
        if (dataEnergistics$redstoneHostMethodsResolved) {
            return dataEnergistics$redstoneHostMethodsAvailable;
        }

        dataEnergistics$redstoneHostMethodsResolved = true;
        try {
            dataEnergistics$redstoneTuningAwareHostClass = Class.forName(
                    REDSTONE_TUNING_AWARE_HOST_CLASS,
                    false,
                    BlockRedstonePulseMixin.class.getClassLoader()
            );
            dataEnergistics$isPulseActiveMethod =
                    dataEnergistics$redstoneTuningAwareHostClass.getMethod(PULSE_ACTIVE_METHOD);
            dataEnergistics$serverTickMethod =
                    dataEnergistics$redstoneTuningAwareHostClass.getMethod(SERVER_TICK_METHOD);
            dataEnergistics$redstoneHostMethodsAvailable = true;
        } catch (ReflectiveOperationException | LinkageError ex) {
            dataEnergistics$redstoneTuningAwareHostClass = null;
            dataEnergistics$isPulseActiveMethod = null;
            dataEnergistics$serverTickMethod = null;
            dataEnergistics$redstoneHostMethodsAvailable = false;
            this.dataEnergistics$logRedstoneHostResolutionFailure(ex);
        }

        return dataEnergistics$redstoneHostMethodsAvailable;
    }

    @Unique
    private void dataEnergistics$logRedstoneHostResolutionFailure(Throwable ex) {
        if (dataEnergistics$redstoneHostResolutionLogged) {
            return;
        }
        dataEnergistics$redstoneHostResolutionLogged = true;
        DATA_ENERGISTICS$LOGGER.warn(
                "Falling back from direct RedstoneTuningAwareHost binding in BlockRedstonePulseMixin; redstone pulse host hooks are unavailable",
                ex
        );
    }
}

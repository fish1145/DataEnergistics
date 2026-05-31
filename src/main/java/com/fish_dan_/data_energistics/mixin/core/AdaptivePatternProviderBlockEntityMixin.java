package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.accessor.PatternProviderLogicAccessor;
import com.fish_dan_.data_energistics.accessor.RedstoneTuningAwareHost;
import com.fish_dan_.data_energistics.ae2.RedstoneTuningMode;
import com.fish_dan_.data_energistics.blockentity.AdaptivePatternProviderBlockEntity;
import com.fish_dan_.data_energistics.registry.ModItems;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdaptivePatternProviderBlockEntity.class)
public abstract class AdaptivePatternProviderBlockEntityMixin implements RedstoneTuningAwareHost {

    @Unique
    private static final String DATA_ENERGISTICS_REDSTONE_TUNING_TAG = "data_energistics_redstone_tuning_mode";
    @Unique
    private static final int DATA_ENERGISTICS_REDSTONE_PULSE_TICKS = 1;

    @Unique
    private RedstoneTuningMode dataEnergistics$redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH;
    @Unique
    private int dataEnergistics$redstonePulseTicks;
    @Unique
    private long dataEnergistics$lastPulseTickTime = Long.MIN_VALUE;
    @Unique
    private boolean dataEnergistics$pendingRedstoneInputCheck;
    @Unique
    private boolean dataEnergistics$lastRedstoneInputPowered;
    @Unique
    private boolean dataEnergistics$redstoneInputPulsePending;

    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void dataEnergistics$saveRedstoneTuning(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        data.putString(DATA_ENERGISTICS_REDSTONE_TUNING_TAG, this.dataEnergistics$redstoneTuningMode.name());
    }

    @Inject(method = "loadTag", at = @At("TAIL"))
    private void dataEnergistics$loadRedstoneTuning(CompoundTag data, HolderLookup.Provider registries, CallbackInfo ci) {
        if (!data.contains(DATA_ENERGISTICS_REDSTONE_TUNING_TAG)) {
            this.dataEnergistics$redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH;
            return;
        }

        try {
            this.dataEnergistics$redstoneTuningMode = RedstoneTuningMode.valueOf(data.getString(DATA_ENERGISTICS_REDSTONE_TUNING_TAG));
        } catch (IllegalArgumentException ignored) {
            this.dataEnergistics$redstoneTuningMode = RedstoneTuningMode.EMIT_ON_DISPATCH;
        }
    }

    @Inject(method = "clearContent", at = @At("TAIL"))
    private void dataEnergistics$clearRedstonePulse(CallbackInfo ci) {
        this.dataEnergistics$redstonePulseTicks = 0;
        this.dataEnergistics$lastPulseTickTime = Long.MIN_VALUE;
        this.dataEnergistics$pendingRedstoneInputCheck = false;
        this.dataEnergistics$lastRedstoneInputPowered = false;
        this.dataEnergistics$redstoneInputPulsePending = false;
    }

    @Override
    public boolean dataEnergistics$hasRedstoneTuningCard() {
        return ((AdaptivePatternProviderBlockEntity) (Object) this).getUpgrades()
                .getInstalledUpgrades(ModItems.REDSTONE_TUNING_CARD.get()) > 0;
    }

    @Override
    public RedstoneTuningMode dataEnergistics$getRedstoneTuningMode() {
        return this.dataEnergistics$redstoneTuningMode;
    }

    @Override
    public boolean dataEnergistics$setRedstoneTuningMode(RedstoneTuningMode mode) {
        if (mode == null || this.dataEnergistics$redstoneTuningMode == mode) {
            return false;
        }
        this.dataEnergistics$clearPulseState();
        this.dataEnergistics$clearInputState();
        this.dataEnergistics$redstoneTuningMode = mode;
        if (mode == RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            this.dataEnergistics$syncRedstoneInputBaseline();
        }
        AdaptivePatternProviderBlockEntity self = (AdaptivePatternProviderBlockEntity) (Object) this;
        self.saveChanges();
        self.markForClientUpdate();
        return true;
    }

    @Override
    public void dataEnergistics$onRedstoneTuningDispatch() {
        if (!this.dataEnergistics$hasRedstoneTuningCard() || this.dataEnergistics$redstoneTuningMode != RedstoneTuningMode.EMIT_ON_DISPATCH) {
            return;
        }
        if (this.dataEnergistics$redstonePulseTicks > 0) {
            return;
        }

        this.dataEnergistics$redstonePulseTicks = DATA_ENERGISTICS_REDSTONE_PULSE_TICKS;
        this.dataEnergistics$notifyPulseChanged();
        this.dataEnergistics$schedulePulseTick();
    }

    @Override
    public void dataEnergistics$serverTick() {
        AdaptivePatternProviderBlockEntity self = (AdaptivePatternProviderBlockEntity) (Object) this;
        if (self.getLevel() == null) {
            return;
        }
        long gameTime = self.getLevel().getGameTime();
        if (this.dataEnergistics$lastPulseTickTime == gameTime) {
            return;
        }
        this.dataEnergistics$lastPulseTickTime = gameTime;
        if (this.dataEnergistics$pendingRedstoneInputCheck) {
            this.dataEnergistics$pendingRedstoneInputCheck = false;
            boolean powered = self.getLevel().hasNeighborSignal(self.getBlockPos());
            if (powered && !this.dataEnergistics$lastRedstoneInputPowered) {
                this.dataEnergistics$redstoneInputPulsePending = true;
                this.dataEnergistics$tryForcePulseUnlock();
            }
            this.dataEnergistics$lastRedstoneInputPowered = powered;
        }
        if (this.dataEnergistics$redstonePulseTicks <= 0) {
            return;
        }
        this.dataEnergistics$redstonePulseTicks--;
        if (this.dataEnergistics$redstonePulseTicks > 0) {
            this.dataEnergistics$schedulePulseTick();
        } else {
            this.dataEnergistics$notifyPulseChanged();
        }
    }

    @Override
    public boolean dataEnergistics$isRedstoneTuningPulseActive() {
        return this.dataEnergistics$redstoneTuningMode == RedstoneTuningMode.EMIT_ON_DISPATCH && this.dataEnergistics$redstonePulseTicks > 0;
    }

    @Override
    public void dataEnergistics$scheduleRedstoneInputCheck() {
        AdaptivePatternProviderBlockEntity self = (AdaptivePatternProviderBlockEntity) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide()) {
            return;
        }
        if (this.dataEnergistics$redstoneTuningMode != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            return;
        }
        boolean powered = self.getLevel().hasNeighborSignal(self.getBlockPos());
        if (powered && !this.dataEnergistics$lastRedstoneInputPowered) {
            this.dataEnergistics$redstoneInputPulsePending = true;
            this.dataEnergistics$tryForcePulseUnlock();
        }
        this.dataEnergistics$lastRedstoneInputPowered = powered;
        this.dataEnergistics$pendingRedstoneInputCheck = true;
        if (self.getLevel() instanceof ServerLevel level) {
            level.scheduleTick(self.getBlockPos(), self.getBlockState().getBlock(), 1);
        }
    }

    @Override
    public boolean dataEnergistics$consumeRedstoneInputPulse() {
        boolean pending = this.dataEnergistics$redstoneInputPulsePending;
        this.dataEnergistics$redstoneInputPulsePending = false;
        return pending;
    }

    @Unique
    private void dataEnergistics$notifyPulseChanged() {
        AdaptivePatternProviderBlockEntity self = (AdaptivePatternProviderBlockEntity) (Object) this;
        if (self.getLevel() != null) {
            self.getLevel().updateNeighborsAt(self.getBlockPos(), self.getBlockState().getBlock());
        }
    }

    @Unique
    private void dataEnergistics$schedulePulseTick() {
        AdaptivePatternProviderBlockEntity self = (AdaptivePatternProviderBlockEntity) (Object) this;
        if (self.getLevel() instanceof ServerLevel level) {
            level.scheduleTick(self.getBlockPos(), self.getBlockState().getBlock(), 1);
        }
    }

    @Unique
    private void dataEnergistics$clearPulseState() {
        if (this.dataEnergistics$redstonePulseTicks <= 0) {
            this.dataEnergistics$lastPulseTickTime = Long.MIN_VALUE;
            return;
        }
        this.dataEnergistics$redstonePulseTicks = 0;
        this.dataEnergistics$lastPulseTickTime = Long.MIN_VALUE;
        this.dataEnergistics$notifyPulseChanged();
    }

    @Unique
    private void dataEnergistics$clearInputState() {
        this.dataEnergistics$pendingRedstoneInputCheck = false;
        this.dataEnergistics$redstoneInputPulsePending = false;
        this.dataEnergistics$lastRedstoneInputPowered = false;
    }

    @Unique
    private void dataEnergistics$syncRedstoneInputBaseline() {
        AdaptivePatternProviderBlockEntity self = (AdaptivePatternProviderBlockEntity) (Object) this;
        if (self.getLevel() != null) {
            this.dataEnergistics$lastRedstoneInputPowered = self.getLevel().hasNeighborSignal(self.getBlockPos());
        }
    }

    @Unique
    private void dataEnergistics$tryForcePulseUnlock() {
        if (!this.dataEnergistics$redstoneInputPulsePending || !this.dataEnergistics$hasRedstoneTuningCard() || this.dataEnergistics$redstoneTuningMode != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE) {
            return;
        }
        if (((AdaptivePatternProviderBlockEntity) (Object) this).getLogic() instanceof PatternProviderLogicAccessor accessor && accessor.dataEnergistics$forcePulseUnlock()) {
            this.dataEnergistics$redstoneInputPulsePending = false;
        }
    }
}

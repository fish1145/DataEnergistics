package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.accessor.PatternProviderLogicAccessor;
import com.fish_dan_.data_energistics.accessor.RedstoneTuningAwareHost;
import com.fish_dan_.data_energistics.ae2.RedstoneTuningAutoRequestHelper;
import com.fish_dan_.data_energistics.ae2.RedstoneTuningMode;

import appeng.api.config.LockCraftingMode;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(PatternProviderLogic.class)
public abstract class PatternProviderLogicMixin implements PatternProviderLogicAccessor {

    @Shadow
    @Final
    private PatternProviderLogicHost host;

    @Shadow
    @Final
    private IActionSource actionSource;

    @Shadow
    @Final
    private List<GenericStack> sendList;

    @Shadow
    public abstract LockCraftingMode getCraftingLockedReason();

    @Shadow
    public abstract void updateRedstoneState();

    @Shadow
    public abstract void saveChanges();

    @Unique
    private boolean dataEnergistics$dispatchPulsePending;

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void dataEnergistics$afterPushPattern(appeng.api.crafting.IPatternDetails patternDetails,
                                                  appeng.api.stacks.KeyCounter[] inputHolder,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        this.dataEnergistics$dispatchPulsePending = true;
        this.dataEnergistics$tryFinishDispatchPulse();
    }

    @Inject(method = "updateRedstoneState", at = @At("HEAD"))
    private void dataEnergistics$handlePulseUnlock(CallbackInfo ci) {
        if (!(this.host instanceof RedstoneTuningAwareHost accessor)) {
            return;
        }
        accessor.dataEnergistics$scheduleRedstoneInputCheck();
    }

    @Inject(method = "doWork", at = @At("HEAD"))
    private void dataEnergistics$tickRedstoneEmitter(CallbackInfoReturnable<Boolean> cir) {
        if (this.host instanceof RedstoneTuningAwareHost accessor) {
            accessor.dataEnergistics$serverTick();
            this.dataEnergistics$tryConsumePulseUnlock(accessor);
        }
    }

    @Inject(method = "doWork", at = @At("TAIL"))
    private void dataEnergistics$finishDispatchPulse(CallbackInfoReturnable<Boolean> cir) {
        this.dataEnergistics$tryFinishDispatchPulse();
    }

    @Override
    public boolean dataEnergistics$forcePulseUnlock() {
        if (this.host instanceof RedstoneTuningAwareHost accessor && accessor.dataEnergistics$getRedstoneTuningMode() == RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE && this.host.getBlockEntity().getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            RedstoneTuningAutoRequestHelper.requestPrimaryOutputs(
                    serverLevel,
                    this.host.getGrid(),
                    this.actionSource,
                    ((PatternProviderLogic) (Object) this).getAvailablePatterns());
            return true;
        }
        return false;
    }

    @Unique
    private void dataEnergistics$tryFinishDispatchPulse() {
        if (!this.dataEnergistics$dispatchPulsePending) {
            return;
        }
        if (!this.sendList.isEmpty()) {
            return;
        }
        this.dataEnergistics$dispatchPulsePending = false;
        if (this.host instanceof RedstoneTuningAwareHost accessor) {
            accessor.dataEnergistics$onRedstoneTuningDispatch();
        }
    }

    @Unique
    private void dataEnergistics$tryConsumePulseUnlock(RedstoneTuningAwareHost accessor) {
        if (!accessor.dataEnergistics$hasRedstoneTuningCard() || accessor.dataEnergistics$getRedstoneTuningMode() != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE || !accessor.dataEnergistics$consumeRedstoneInputPulse() || !(this.host.getBlockEntity().getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        RedstoneTuningAutoRequestHelper.requestPrimaryOutputs(
                serverLevel,
                this.host.getGrid(),
                this.actionSource,
                ((PatternProviderLogic) (Object) this).getAvailablePatterns());
    }
}

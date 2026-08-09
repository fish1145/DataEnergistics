package com.fish_dan_.data_energistics.mixin.advancedae;

import com.fish_dan_.data_energistics.accessor.PatternProviderHostAccessor;
import com.fish_dan_.data_energistics.accessor.PatternProviderLogicAccessor;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningAutoRequestHelper;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningMode;

import net.minecraft.server.level.ServerLevel;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic;
import net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogicHost;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(AdvPatternProviderLogic.class)
public abstract class AdvancedAePatternProviderLogicMixin implements PatternProviderLogicAccessor {

    @Shadow
    @Final
    private AdvPatternProviderLogicHost host;

    @Shadow
    public abstract void saveChanges();

    @Unique
    private boolean dataEnergistics$dispatchPulsePending;

    @Inject(method = "pushPattern", at = @At("RETURN"))
    private void dataEnergistics$afterPushPattern(IPatternDetails patternDetails,
                                                  KeyCounter[] inputHolder,
                                                  CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }
        this.dataEnergistics$dispatchPulsePending = true;
        this.dataEnergistics$tryFinishDispatchPulse();
    }

    @Inject(method = "updateRedstoneState", at = @At("HEAD"))
    private void dataEnergistics$handlePulseUnlock(CallbackInfo ci) {
        if (!(this.host instanceof PatternProviderHostAccessor accessor)) {
            return;
        }
        accessor.dataEnergistics$scheduleRedstoneInputCheck();
    }

    @Inject(method = "doWork", at = @At("HEAD"))
    private void dataEnergistics$tickRedstoneEmitter(CallbackInfoReturnable<Boolean> cir) {
        if (this.host instanceof PatternProviderHostAccessor accessor) {
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
        if (this.host instanceof PatternProviderHostAccessor accessor && accessor.dataEnergistics$getRedstoneTuningMode() == RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE && this.host.getBlockEntity().getLevel() instanceof ServerLevel serverLevel) {
            RedstoneTuningAutoRequestHelper.requestPrimaryOutputs(
                    serverLevel,
                    this.host.getGrid(),
                    ((AdvancedAePatternProviderLogicFieldAccessor) this).dataEnergistics$getActionSource(),
                    ((AdvPatternProviderLogic) (Object) this).getAvailablePatterns());
            return true;
        }
        return false;
    }

    @Unique
    private void dataEnergistics$tryFinishDispatchPulse() {
        if (!this.dataEnergistics$dispatchPulsePending) {
            return;
        }
        if (!((AdvancedAePatternProviderLogicFieldAccessor) this).dataEnergistics$getSendList().isEmpty()) {
            return;
        }
        this.dataEnergistics$dispatchPulsePending = false;
        if (this.host instanceof PatternProviderHostAccessor accessor) {
            accessor.dataEnergistics$onRedstoneTuningDispatch();
        }
    }

    @Unique
    private void dataEnergistics$tryConsumePulseUnlock(PatternProviderHostAccessor accessor) {
        if (!accessor.dataEnergistics$hasRedstoneTuningCard() || accessor.dataEnergistics$getRedstoneTuningMode() != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE || !accessor.dataEnergistics$consumeRedstoneInputPulse() || !(this.host.getBlockEntity().getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        RedstoneTuningAutoRequestHelper.requestPrimaryOutputs(
                serverLevel,
                this.host.getGrid(),
                ((AdvancedAePatternProviderLogicFieldAccessor) this).dataEnergistics$getActionSource(),
                ((AdvPatternProviderLogic) (Object) this).getAvailablePatterns());
    }
}

package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.accessor.PatternProviderBatchAccess;
import com.fish_dan_.data_energistics.accessor.PatternProviderBatchBridge;
import com.fish_dan_.data_energistics.accessor.PatternProviderLogicAccessor;
import com.fish_dan_.data_energistics.accessor.RedstoneTuningAwareHost;
import com.fish_dan_.data_energistics.ae2.PatternProviderBatching;
import com.fish_dan_.data_energistics.ae2.RedstoneTuningAutoRequestHelper;
import com.fish_dan_.data_energistics.ae2.RedstoneTuningMode;
import com.fish_dan_.data_energistics.api.crafting.dispatch.CountedCraftingAdmission;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.commit.CountedCraftingPreparation;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchRejection;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchStatus;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTargetAvailability;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.provider.CountedCraftingProvider;

import net.minecraft.server.level.ServerLevel;

import appeng.api.config.LockCraftingMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import org.jetbrains.annotations.Nullable;
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
public abstract class PatternProviderLogicMixin
                                                implements PatternProviderLogicAccessor, PatternProviderBatchBridge, CountedCraftingProvider {

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

    @Override
    @Nullable
    public CountedCraftingAdmission prepareBatch(
                                                 IPatternDetails patternDetails,
                                                 KeyCounter[] prototype,
                                                 long requestedCount) {
        return prepareBatch(
                patternDetails,
                prototype,
                requestedCount,
                CraftingDispatchTargetAvailability.all()).admission();
    }

    @Override
    public CountedCraftingPreparation prepareBatch(
                                                   IPatternDetails patternDetails,
                                                   KeyCounter[] prototype,
                                                   long requestedCount,
                                                   CraftingDispatchTargetAvailability targetAvailability) {
        if (targetAvailability == null) {
            throw new IllegalArgumentException("Crafting dispatch target availability must not be null");
        }
        PatternProviderLogic logic = (PatternProviderLogic) (Object) this;
        if (logic.getClass() != PatternProviderLogic.class) {
            CraftingDispatchTarget target = CraftingDispatchTarget.provider();
            if (!targetAvailability.canAttempt(target)) {
                return CountedCraftingPreparation.rejected(
                        CraftingDispatchRejection.targeted(CraftingDispatchStatus.NO_CAPACITY, target));
            }
            return CountedCraftingPreparation.accepted(
                    PatternProviderBatching.prepareSingle(logic, patternDetails, prototype, requestedCount),
                    target);
        }
        return this.dataEnergistics$prepareStandardBatch(
                patternDetails,
                prototype,
                requestedCount,
                this::dataEnergistics$afterCountedPush,
                targetAvailability);
    }

    @Override
    @Nullable
    public CountedCraftingAdmission dataEnergistics$prepareStandardBatch(
                                                                         IPatternDetails patternDetails,
                                                                         KeyCounter[] prototype,
                                                                         long requestedCount,
                                                                         Runnable afterCommit) {
        return this.dataEnergistics$prepareStandardBatch(
                patternDetails,
                prototype,
                requestedCount,
                afterCommit,
                CraftingDispatchTargetAvailability.all()).admission();
    }

    @Override
    public CountedCraftingPreparation dataEnergistics$prepareStandardBatch(
                                                                           IPatternDetails patternDetails,
                                                                           KeyCounter[] prototype,
                                                                           long requestedCount,
                                                                           Runnable afterCommit,
                                                                           CraftingDispatchTargetAvailability targetAvailability) {
        return PatternProviderBatching.prepareStandardBatch(
                (PatternProviderLogic) (Object) this,
                (PatternProviderBatchAccess) this,
                patternDetails,
                prototype,
                requestedCount,
                afterCommit,
                targetAvailability);
    }

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

    @Unique
    private void dataEnergistics$afterCountedPush() {
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
        if (this.host instanceof RedstoneTuningAwareHost accessor && accessor.dataEnergistics$getRedstoneTuningMode() == RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE && this.host.getBlockEntity().getLevel() instanceof ServerLevel serverLevel) {
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
        if (!accessor.dataEnergistics$hasRedstoneTuningCard() || accessor.dataEnergistics$getRedstoneTuningMode() != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE || !accessor.dataEnergistics$consumeRedstoneInputPulse() || !(this.host.getBlockEntity().getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }
        RedstoneTuningAutoRequestHelper.requestPrimaryOutputs(
                serverLevel,
                this.host.getGrid(),
                this.actionSource,
                ((PatternProviderLogic) (Object) this).getAvailablePatterns());
    }
}

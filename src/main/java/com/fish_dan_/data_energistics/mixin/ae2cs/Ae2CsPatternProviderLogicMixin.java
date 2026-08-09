package com.fish_dan_.data_energistics.mixin.ae2cs;

import com.fish_dan_.data_energistics.accessor.PatternProviderHostAccessor;
import com.fish_dan_.data_energistics.ae2.patternprovider.RedstoneTuningAutoRequestHelper;
import com.fish_dan_.data_energistics.mixin.core.PatternProviderLogicFieldAccessor;

import net.minecraft.server.level.ServerLevel;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogic;
import io.github.lounode.ae2cs.common.me.logic.MeteoritePatternProviderLogic;
import io.github.lounode.ae2cs.common.me.logic.ResonatingPatternProviderLogic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin({ ResonatingPatternProviderLogic.class, MeteoritePatternProviderLogic.class })
public abstract class Ae2CsPatternProviderLogicMixin {

    @Unique
    private boolean dataEnergistics$dispatchPulsePending;

    @Inject(method = "pushPattern", at = @At("HEAD"))
    private void dataEnergistics$scheduleAutoRequestPulse(IPatternDetails patternDetails,
                                                          KeyCounter[] inputHolder,
                                                          CallbackInfoReturnable<Boolean> cir) {
        var host = ((PatternProviderLogicFieldAccessor) this).dataEnergistics$getHost();
        if (!(host instanceof PatternProviderHostAccessor accessor)) {
            return;
        }
        accessor.dataEnergistics$scheduleRedstoneInputCheck();
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
    private void dataEnergistics$tryFinishDispatchPulse() {
        if (!this.dataEnergistics$dispatchPulsePending) {
            return;
        }
        if (!((PatternProviderLogicFieldAccessor) this).dataEnergistics$getSendList().isEmpty()) {
            return;
        }
        this.dataEnergistics$dispatchPulsePending = false;
        var host = ((PatternProviderLogicFieldAccessor) this).dataEnergistics$getHost();
        if (host instanceof PatternProviderHostAccessor accessor) {
            if (accessor.dataEnergistics$consumeRedstoneInputPulse() && host.getBlockEntity().getLevel() instanceof ServerLevel serverLevel) {
                RedstoneTuningAutoRequestHelper.requestPrimaryOutputs(
                        serverLevel,
                        host.getGrid(),
                        ((PatternProviderLogicFieldAccessor) this).dataEnergistics$getActionSource(),
                        ((PatternProviderLogic) (Object) this).getAvailablePatterns());
            }
            accessor.dataEnergistics$onRedstoneTuningDispatch();
        }
    }
}

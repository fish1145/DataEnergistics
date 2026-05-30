package com.fish_dan_.data_energistics.mixin;

import com.fish_dan_.data_energistics.accessor.PatternProviderHostAccessor;
import com.fish_dan_.data_energistics.accessor.PatternProviderLogicAccessor;
import com.fish_dan_.data_energistics.ae2.RedstoneTuningAutoRequestHelper;
import com.fish_dan_.data_energistics.ae2.RedstoneTuningMode;

import com.moakiee.ae2lt.blockentity.OverloadedPatternProviderBlockEntity;
import com.moakiee.ae2lt.logic.OverloadedPatternProviderLogic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Map;

@Mixin(OverloadedPatternProviderLogic.class)
public abstract class Ae2ltOverloadedPatternProviderLogicMixin implements PatternProviderLogicAccessor {

    @Shadow
    @Final
    private OverloadedPatternProviderBlockEntity overloadedHost;

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

    @Inject(method = "onNeighborChanged", at = @At("HEAD"))
    private void dataEnergistics$handlePulseUnlock(CallbackInfo ci) {
        if (!(this.overloadedHost instanceof PatternProviderHostAccessor accessor)) {
            return;
        }
        accessor.dataEnergistics$scheduleRedstoneInputCheck();
    }

    @Inject(method = "tickAutoReturn", at = @At("HEAD"))
    private void dataEnergistics$tickRedstoneEmitter(CallbackInfo ci) {
        if (this.overloadedHost instanceof PatternProviderHostAccessor accessor) {
            accessor.dataEnergistics$serverTick();
            this.dataEnergistics$tryConsumePulseUnlock(accessor);
        }
        this.dataEnergistics$tryFinishDispatchPulse();
    }

    @Override
    public boolean dataEnergistics$forcePulseUnlock() {
        if (this.overloadedHost instanceof PatternProviderHostAccessor accessor && accessor.dataEnergistics$getRedstoneTuningMode() == RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE && this.overloadedHost.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            RedstoneTuningAutoRequestHelper.requestPrimaryOutputs(
                    serverLevel,
                    this.overloadedHost.getGrid(),
                    ((PatternProviderLogicFieldAccessor) this).dataEnergistics$getActionSource(),
                    ((OverloadedPatternProviderLogic) (Object) this).getAvailablePatterns());
            return true;
        }
        return false;
    }

    @Unique
    private void dataEnergistics$tryFinishDispatchPulse() {
        if (!this.dataEnergistics$dispatchPulsePending) {
            return;
        }
        if (!((PatternProviderLogicFieldAccessor) this).dataEnergistics$getSendList().isEmpty() || this.dataEnergistics$hasAe2LtWirelessOverflow()) {
            return;
        }
        this.dataEnergistics$dispatchPulsePending = false;
        if (this.overloadedHost instanceof PatternProviderHostAccessor accessor) {
            accessor.dataEnergistics$onRedstoneTuningDispatch();
        }
    }

    @Unique
    private void dataEnergistics$tryConsumePulseUnlock(PatternProviderHostAccessor accessor) {
        if (!accessor.dataEnergistics$hasRedstoneTuningCard() || accessor.dataEnergistics$getRedstoneTuningMode() != RedstoneTuningMode.PULSE_TO_UNLOCK_ONCE || !accessor.dataEnergistics$consumeRedstoneInputPulse() || !(this.overloadedHost.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel)) {
            return;
        }
        RedstoneTuningAutoRequestHelper.requestPrimaryOutputs(
                serverLevel,
                this.overloadedHost.getGrid(),
                ((PatternProviderLogicFieldAccessor) this).dataEnergistics$getActionSource(),
                ((OverloadedPatternProviderLogic) (Object) this).getAvailablePatterns());
    }

    @Unique
    private boolean dataEnergistics$hasAe2LtWirelessOverflow() {
        Object logic = this;

        Object legacySendList = dataEnergistics$getFieldValue(logic, "wirelessSendList");
        if (legacySendList instanceof Collection<?> collection && !collection.isEmpty()) {
            return true;
        }

        Object modernOverflowMap = dataEnergistics$getFieldValue(logic, "pendingOverflowByConn");
        if (modernOverflowMap instanceof Map<?, ?> map && !map.isEmpty()) {
            return true;
        }

        return false;
    }

    @Unique
    private static Object dataEnergistics$getFieldValue(Object instance, String fieldName) {
        Class<?> current = instance.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException ignored) {
                return null;
            }
        }
        return null;
    }
}

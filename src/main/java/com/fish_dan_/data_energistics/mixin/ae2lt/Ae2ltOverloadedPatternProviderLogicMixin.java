package com.fish_dan_.data_energistics.mixin.ae2lt;

import com.fish_dan_.data_energistics.accessor.PatternProviderHostAccessor;
import com.fish_dan_.data_energistics.accessor.PatternProviderLogicAccessor;
import com.fish_dan_.data_energistics.ae2.RedstoneTuningAutoRequestHelper;
import com.fish_dan_.data_energistics.ae2.RedstoneTuningMode;
import com.fish_dan_.data_energistics.mixin.core.PatternProviderLogicFieldAccessor;

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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Mixin(OverloadedPatternProviderLogic.class)
public abstract class Ae2ltOverloadedPatternProviderLogicMixin implements PatternProviderLogicAccessor {

    @Shadow
    @Final
    private OverloadedPatternProviderBlockEntity overloadedHost;

    @Unique
    private boolean dataEnergistics$dispatchPulsePending;
    @Unique
    private static final ConcurrentHashMap<FieldLookupKey, Optional<VarHandle>> dataEnergistics$FIELD_HANDLES = new ConcurrentHashMap<>();

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
        Optional<VarHandle> handle = dataEnergistics$FIELD_HANDLES.computeIfAbsent(
                new FieldLookupKey(instance.getClass(), fieldName),
                dataEnergistics$key -> dataEnergistics$findFieldHandle(dataEnergistics$key.type(), dataEnergistics$key.fieldName()));
        if (handle.isEmpty()) {
            return null;
        }

        try {
            return handle.get().get(instance);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @Unique
    private static Optional<VarHandle> dataEnergistics$findFieldHandle(Class<?> type, String fieldName) {
        Class<?> current = type;
        while (current != null) {
            try {
                MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(current, MethodHandles.lookup());
                return Optional.of(dataEnergistics$findDeclaredVarHandle(lookup, current, fieldName));
            } catch (NoSuchFieldException | NoSuchFieldError ignored) {
                current = current.getSuperclass();
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @Unique
    private static VarHandle dataEnergistics$findDeclaredVarHandle(MethodHandles.Lookup lookup,
                                                                   Class<?> owner,
                                                                   String fieldName) throws ReflectiveOperationException {
        for (var field : owner.getDeclaredFields()) {
            if (field.getName().equals(fieldName)) {
                return lookup.findVarHandle(owner, fieldName, field.getType());
            }
        }
        throw new NoSuchFieldException(fieldName);
    }

    @Unique
    private record FieldLookupKey(Class<?> type, String fieldName) {}
}

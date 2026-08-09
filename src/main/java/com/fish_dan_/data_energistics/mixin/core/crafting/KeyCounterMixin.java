package com.fish_dan_.data_energistics.mixin.core.crafting;

import com.fish_dan_.data_energistics.ae2.key.SaturatingKeyCounter;
import com.fish_dan_.data_energistics.ae2.key.SaturatingKeyCounterBridge;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds a short-lived overflow-safe accumulation mode to AE2's mutable key counter.
 *
 * <p>
 * AE2's {@code MEStorage#getAvailableStacks} contract says each storage adds its amounts to the supplied counter.
 * Running that contract directly against the network total avoids populating and traversing a second contribution
 * counter.
 * </p>
 */
@Mixin(KeyCounter.class)
public abstract class KeyCounterMixin implements SaturatingKeyCounterBridge {

    @Unique
    private int dataEnergistics$saturatingMergeDepth;

    @Shadow
    public abstract long get(AEKey key);

    @Shadow
    public abstract void set(AEKey key, long amount);

    @Override
    public void dataEnergistics$beginSaturatingMerge() {
        this.dataEnergistics$saturatingMergeDepth = Math.incrementExact(
                this.dataEnergistics$saturatingMergeDepth);
    }

    @Override
    public void dataEnergistics$endSaturatingMerge() {
        if (this.dataEnergistics$saturatingMergeDepth <= 0) {
            throw new IllegalStateException("AE storage saturation scope is not active");
        }
        this.dataEnergistics$saturatingMergeDepth = Math.decrementExact(
                this.dataEnergistics$saturatingMergeDepth);
    }

    /**
     * Replaces AE2's unchecked add with one saturating write while the network report scope is active.
     */
    @Inject(method = "add", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$saturatingAdd(AEKey key, long amount, CallbackInfo callback) {
        if (this.dataEnergistics$saturatingMergeDepth == 0) {
            return;
        }
        this.dataEnergistics$writeRaw(key, SaturatingKeyCounter.mergeAmount(this.get(key), amount));
        callback.cancel();
    }

    /**
     * Treats a non-contractual set from a storage reporter as one contribution instead of allowing it to overwrite
     * unrelated mounted totals.
     */
    @Inject(method = "set", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$saturatingSet(AEKey key, long amount, CallbackInfo callback) {
        if (this.dataEnergistics$saturatingMergeDepth == 0) {
            return;
        }
        this.dataEnergistics$writeRaw(key, SaturatingKeyCounter.mergeAmount(this.get(key), amount));
        callback.cancel();
    }

    /**
     * Preserves add-all semantics without materializing a separate contribution counter.
     */
    @Inject(method = "addAll", at = @At("HEAD"), cancellable = true, require = 1)
    private void dataEnergistics$saturatingAddAll(KeyCounter other, CallbackInfo callback) {
        if (this.dataEnergistics$saturatingMergeDepth == 0) {
            return;
        }
        if (other == (Object) this) {
            throw new IllegalArgumentException("An AE storage report cannot add a KeyCounter to itself");
        }
        for (var entry : other) {
            this.dataEnergistics$saturatingAdd(entry.getKey(), entry.getLongValue());
        }
        callback.cancel();
    }

    /**
     * Invokes the original KeyCounter setter without re-entering the saturating interception.
     */
    @Unique
    private void dataEnergistics$writeRaw(AEKey key, long amount) {
        int previousDepth = this.dataEnergistics$saturatingMergeDepth;
        this.dataEnergistics$saturatingMergeDepth = 0;
        try {
            this.set(key, amount);
        } finally {
            this.dataEnergistics$saturatingMergeDepth = previousDepth;
        }
    }

    /**
     * Applies one contribution through the injected add path for add-all reports.
     */
    @Unique
    private void dataEnergistics$saturatingAdd(AEKey key, long amount) {
        this.dataEnergistics$writeRaw(key, SaturatingKeyCounter.mergeAmount(this.get(key), amount));
    }
}

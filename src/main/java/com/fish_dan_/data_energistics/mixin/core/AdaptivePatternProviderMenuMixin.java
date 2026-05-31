package com.fish_dan_.data_energistics.mixin.core;

import com.fish_dan_.data_energistics.accessor.PatternProviderMenuAccessor;
import com.fish_dan_.data_energistics.accessor.RedstoneTuningAwareHost;
import com.fish_dan_.data_energistics.ae2.RedstoneTuningMode;
import com.fish_dan_.data_energistics.menu.AdaptivePatternProviderMenu;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(AdaptivePatternProviderMenu.class)
public abstract class AdaptivePatternProviderMenuMixin implements PatternProviderMenuAccessor {

    @Unique
    private AdaptivePatternProviderMenu dataEnergistics$self() {
        return (AdaptivePatternProviderMenu) (Object) this;
    }

    @Unique
    private RedstoneTuningAwareHost dataEnergistics$getHost() {
        var target = this.dataEnergistics$self().getTarget();
        return target instanceof RedstoneTuningAwareHost awareHost ? awareHost : null;
    }

    @Override
    public boolean dataEnergistics$hasRedstoneTuningCard() {
        var host = this.dataEnergistics$getHost();
        return host != null && host.dataEnergistics$hasRedstoneTuningCard();
    }

    @Override
    public int dataEnergistics$getRedstoneTuningMode() {
        var host = this.dataEnergistics$getHost();
        return host != null ? host.dataEnergistics$getRedstoneTuningMode().ordinal() : RedstoneTuningMode.EMIT_ON_DISPATCH.ordinal();
    }

    @Override
    public void dataEnergistics$setRedstoneTuningMode(int ordinal) {
        var host = this.dataEnergistics$getHost();
        if (host == null) {
            return;
        }

        var values = RedstoneTuningMode.values();
        int clamped = Math.max(0, Math.min(values.length - 1, ordinal));
        host.dataEnergistics$setRedstoneTuningMode(values[clamped]);
    }
}

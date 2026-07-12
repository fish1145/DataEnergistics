package com.fish_dan_.data_energistics.mixin.client;

import com.fish_dan_.data_energistics.client.util.Ae2AmountFormatter;
import com.fish_dan_.data_energistics.client.util.Ae2AmountFormatter.FormattedAmount;

import appeng.core.localization.Tooltips;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Extends AE2's compact amount formatting to cover every non-negative {@code long} value safely. */
@Mixin(Tooltips.class)
public abstract class TooltipsMixin {

    @Inject(
            method = "getByteAmount(J)Lappeng/core/localization/Tooltips$Amount;",
            at = @At("HEAD"),
            cancellable = true)
    private static void dataEnergistics$formatByteAmount(long amount,
                                                         CallbackInfoReturnable<Tooltips.Amount> cir) {
        if (amount >= 0L) {
            cir.setReturnValue(dataEnergistics$toAe2Amount(Ae2AmountFormatter.formatByteAmount(amount)));
        }
    }

    @Inject(
            method = "getAmount(J)Lappeng/core/localization/Tooltips$Amount;",
            at = @At("HEAD"),
            cancellable = true)
    private static void dataEnergistics$formatAmount(long amount, CallbackInfoReturnable<Tooltips.Amount> cir) {
        if (amount >= 0L) {
            cir.setReturnValue(dataEnergistics$toAe2Amount(Ae2AmountFormatter.formatAmount(amount)));
        }
    }

    @Unique
    private static Tooltips.Amount dataEnergistics$toAe2Amount(FormattedAmount amount) {
        return new Tooltips.Amount(amount.digits(), amount.unit());
    }
}

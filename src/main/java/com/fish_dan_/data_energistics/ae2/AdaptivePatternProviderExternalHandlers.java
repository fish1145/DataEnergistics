package com.fish_dan_.data_energistics.ae2;

import com.fish_dan_.data_energistics.integration.AppMekCompat;
import com.fish_dan_.data_energistics.integration.AppliedCreateCompat;

import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

public final class AdaptivePatternProviderExternalHandlers {

    private AdaptivePatternProviderExternalHandlers() {}

    @Nullable
    public static Object createChemicalHandler(Supplier<@Nullable AdaptivePatternProviderLogic> logicSupplier) {
        return AppMekCompat.createReturnChemicalHandler(logicSupplier);
    }

    public static boolean supportsMechanicalProviders() {
        return AppliedCreateCompat.isMechanicalProviderSupportEnabled();
    }
}

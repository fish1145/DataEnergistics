package com.fish_dan_.data_energistics.ae2.patternprovider.adaptive;

import com.fish_dan_.data_energistics.integration.ModFlags;
import com.fish_dan_.data_energistics.integration.ae.appmek.AppMekCompat;

import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

public final class AdaptivePatternProviderExternalHandlers {

    private AdaptivePatternProviderExternalHandlers() {}

    @Nullable
    public static Object createChemicalHandler(Supplier<@Nullable AdaptivePatternProviderLogic> logicSupplier) {
        if (!ModFlags.isAppMekChemicalSupportLoaded()) {
            return null;
        }

        return AppMekCompat.createReturnChemicalHandler(logicSupplier);
    }

    public static boolean supportsMechanicalProviders() {
        return ModFlags.isAppliedCreateMechanicalProviderSupportLoaded();
    }
}

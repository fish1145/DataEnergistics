package com.fish_dan_.data_energistics.integration;

public final class AppliedCreateCompat {

    private AppliedCreateCompat() {}

    public static boolean isMechanicalProviderSupportEnabled() {
        return ModFlags.isCreateLoaded() && ModFlags.isAppliedCreateLoaded();
    }
}

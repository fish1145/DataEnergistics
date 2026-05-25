package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.compat.CompatIds;
import com.fish_dan_.data_energistics.compat.OptionalMods;

public final class AppliedCreateCompat {
    private static final boolean CREATE_LOADED = OptionalMods.isLoaded(CompatIds.CREATE);
    private static final boolean APPLIED_CREATE_LOADED = OptionalMods.isLoaded(CompatIds.APPLIED_CREATE);

    private AppliedCreateCompat() {
    }

    public static boolean isCreateLoaded() {
        return CREATE_LOADED;
    }

    public static boolean isAppliedCreateLoaded() {
        return APPLIED_CREATE_LOADED;
    }

    public static boolean isMechanicalProviderSupportEnabled() {
        return CREATE_LOADED && APPLIED_CREATE_LOADED;
    }
}

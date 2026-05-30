package com.fish_dan_.data_energistics.integration;

import net.neoforged.fml.ModList;

public final class AppliedCreateCompat {

    private static final boolean CREATE_LOADED = ModList.get().isLoaded("create");
    private static final boolean APPLIED_CREATE_LOADED = ModList.get().isLoaded("appliedcreate");

    private AppliedCreateCompat() {}

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

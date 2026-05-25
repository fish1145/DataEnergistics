package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.compat.CompatIds;
import com.fish_dan_.data_energistics.compat.OptionalMods;

public final class Ae2LtCompat {
    private static final boolean AE2LT_LOADED = OptionalMods.isLoaded(CompatIds.AE2LT);

    private Ae2LtCompat() {
    }

    public static boolean isLoaded() {
        return AE2LT_LOADED;
    }
}

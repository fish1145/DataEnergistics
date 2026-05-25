package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.compat.CompatIds;
import com.fish_dan_.data_energistics.compat.OptionalMods;

public final class ExtendedAePlusCompat {
    public static final String MOD_ID = CompatIds.EXTENDEDAE_PLUS;
    public static final String CREATE_PATTERN_KEY = "key.extendedae_plus.create_pattern";
    public static final String FILL_SEARCH_KEY = "key.extendedae_plus.fill_search";

    private static final boolean LOADED = OptionalMods.isLoaded(MOD_ID);

    private ExtendedAePlusCompat() {
    }

    public static boolean isLoaded() {
        return LOADED;
    }
}

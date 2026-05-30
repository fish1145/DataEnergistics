package com.fish_dan_.data_energistics.integration;

import net.neoforged.fml.ModList;

public final class Ae2LtCompat {

    private static final boolean AE2LT_LOADED = ModList.get().isLoaded("ae2lt");

    private Ae2LtCompat() {}

    public static boolean isLoaded() {
        return AE2LT_LOADED;
    }
}

package com.fish_dan_.data_energistics.compat;

import net.neoforged.fml.ModList;

public final class OptionalMods {
    private OptionalMods() {
    }

    public static boolean isLoaded(String modId) {
        ModList modList = ModList.get();
        return modList != null && modList.isLoaded(modId);
    }

    public static boolean areLoaded(String... modIds) {
        for (String modId : modIds) {
            if (!isLoaded(modId)) {
                return false;
            }
        }
        return true;
    }
}

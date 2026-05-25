package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.compat.CompatIds;
import com.fish_dan_.data_energistics.compat.OptionalMods;

public final class Ae2LtMixinGuards {
    public static final String MIXIN_PREFIX = "com.fish_dan_.data_energistics.mixin.Ae2lt";

    private Ae2LtMixinGuards() {
    }

    public static boolean isAe2LtMixin(String mixinClassName) {
        return mixinClassName.startsWith(MIXIN_PREFIX);
    }

    public static boolean shouldApply(String mixinClassName) {
        return mixinClassName.startsWith(MIXIN_PREFIX) && isPresent();
    }

    public static boolean isPresent() {
        return OptionalMods.isLoaded(CompatIds.AE2LT)
                && isClassPresent(Ae2LtInternalNames.EJECT_MODE_REGISTRY_RESOURCE);
    }

    private static boolean isClassPresent(String classResourcePath) {
        return Ae2LtMixinGuards.class.getClassLoader().getResource(classResourcePath) != null;
    }
}

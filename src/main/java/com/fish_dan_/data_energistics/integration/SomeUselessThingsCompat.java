package com.fish_dan_.data_energistics.integration;

import com.fish_dan_.data_energistics.util.ReflectionAccess;

import appeng.helpers.patternprovider.PatternContainer;

public final class SomeUselessThingsCompat {

    private static final String ADVANCED_ALLOY_FURNACE_BLOCK_ENTITY_CLASS = "com.sorrowmist.useless.content.blockentities.AdvancedAlloyFurnaceBlockEntity";

    private SomeUselessThingsCompat() {}

    public static void afterPatternUpload(PatternContainer container) {
        if (!isAdvancedAlloyFurnace(container)) {
            return;
        }

        ReflectionAccess.invokeNoArgBestEffort(container, "updatePatterns");
        ReflectionAccess.invokeNoArgBestEffort(container, "markChanged");
    }

    private static boolean isAdvancedAlloyFurnace(PatternContainer container) {
        return container != null && ADVANCED_ALLOY_FURNACE_BLOCK_ENTITY_CLASS.equals(container.getClass().getName());
    }
}

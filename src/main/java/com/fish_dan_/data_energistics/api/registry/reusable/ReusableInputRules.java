package com.fish_dan_.data_energistics.api.registry.reusable;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputContext;
import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRule;

import appeng.api.crafting.IPatternDetails;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Frozen server-thread lookup for explicit reusable-input contracts. Only the returned immutable
 * rule may cross thread boundaries; a query must not retain its context or mutate the world.
 */
@FunctionalInterface
public interface ReusableInputRules {

    /** Server-thread conservative pattern filter; false avoids a capture without inspecting live inventory. */
    default boolean mayMatch(IPatternDetails pattern, Optional<ResourceLocation> recipeId) {
        return true;
    }

    /**
     * @param context live server-thread query, not retained by the lookup
     * @return the sole applicable rule, or empty for legacy handling
     * @throws IllegalStateException on conflicting adapters or a rule for the wrong initial key
     */
    Optional<ReusableInputRule> resolve(ReusableInputContext context);
}

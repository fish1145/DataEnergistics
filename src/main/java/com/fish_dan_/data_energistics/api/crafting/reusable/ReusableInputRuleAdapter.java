package com.fish_dan_.data_energistics.api.crafting.reusable;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Explicit, stateless source of guaranteed tool transitions for known recipes or machines.
 * Resolution runs on the server thread without mutation. Unknown or stochastic behavior must
 * return empty; observing an unchanged remainder once is not evidence of unlimited use.
 */
public interface ReusableInputRuleAdapter {

    /** @return stable registration identity; never changes during the adapter lifetime */
    ResourceLocation id();

    /**
     * Captures a complete immutable rule with no live references or deferred callbacks.
     *
     * @param context scoped server-thread query; must not be retained
     * @return an authoritative rule for the actual input, or empty when unsupported
     * @throws IllegalArgumentException if a known rule cannot represent the supplied state
     */
    Optional<ReusableInputRule> resolve(ReusableInputContext context);
}

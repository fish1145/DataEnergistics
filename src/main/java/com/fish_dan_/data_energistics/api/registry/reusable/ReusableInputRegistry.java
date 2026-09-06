package com.fish_dan_.data_energistics.api.registry.reusable;

import com.fish_dan_.data_energistics.api.crafting.reusable.ReusableInputRuleAdapter;

/**
 * Plugin registration-stage boundary for authoritative reusable-input rules. Registrations follow
 * the owning plugin transaction and may not change after the plugin registry freezes.
 */
@FunctionalInterface
public interface ReusableInputRegistry {

    /**
     * @param adapter stateless rule source with a unique stable ID
     * @throws IllegalStateException when registration is closed or the adapter ID is already registered
     */
    void register(ReusableInputRuleAdapter adapter);
}

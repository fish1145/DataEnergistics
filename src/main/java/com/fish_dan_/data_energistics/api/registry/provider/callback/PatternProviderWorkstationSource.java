package com.fish_dan_.data_energistics.api.registry.provider.callback;

import it.unimi.dsi.fastutil.objects.ObjectList;

/**
 * Resolves the actual workstations that may receive inputs from one exact pattern-provider leaf.
 *
 * <p>
 * Standard AE2 providers are resolved by the runtime. Custom, remote or non-adjacent providers can implement this
 * interface directly or publish it through {@code PatternProviderRegistration}. The callback must report real live
 * routes rather than terminal display groups or viewer catalysts.
 * </p>
 */
@FunctionalInterface
public interface PatternProviderWorkstationSource {

    /**
     * Resolves the current workstation routes for one synchronous upload attempt.
     *
     * @param context exact provider, player and pattern facts
     * @return routes in deterministic preference order; an empty list means no machine-side upload behavior
     */
    ObjectList<PatternProviderWorkstationTarget> resolveWorkstations(PatternProviderWorkstationSourceContext context);
}

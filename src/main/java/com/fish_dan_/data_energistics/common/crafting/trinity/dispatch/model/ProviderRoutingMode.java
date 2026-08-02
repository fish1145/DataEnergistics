package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model;

/**
 * Describes the conservative routing contract selected for one provider capacity snapshot.
 */
public enum ProviderRoutingMode {
    /**
     * The dispatcher can address an exact physical target.
     */
    TARGETED,
    /**
     * The provider exposes a stable target order but retains target selection.
     */
    ORDERED,
    /**
     * The provider can accept a counted logical batch through one physical submission.
     */
    AGGREGATE,
    /**
     * No stronger routing or counted-capacity contract has been proven.
     */
    UNKNOWN
}

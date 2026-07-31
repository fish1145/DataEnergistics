package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch;

/**
 * Read-only current-window filter supplied to a provider while it prepares a target.
 */
@FunctionalInterface
public interface CraftingDispatchTargetAvailability {

    /**
     * Returns whether the target is not currently excluded by a provider-pattern-target rejection.
     *
     * @param target provider-local target identity
     * @return whether preparation may inspect the target
     */
    boolean canAttempt(CraftingDispatchTarget target);

    /**
     * Creates an unrestricted filter for original provider callers outside Trinity dispatch.
     *
     * @return filter accepting every valid target
     */
    static CraftingDispatchTargetAvailability all() {
        return target -> {
            if (target == null) {
                throw new IllegalArgumentException("Crafting dispatch target must not be null");
            }
            return true;
        };
    }
}

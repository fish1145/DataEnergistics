package com.fish_dan_.data_energistics.accessor;

import appeng.api.stacks.GenericStack;

/**
 * Stable bridge for the PatternProviderLogic fields that business logic must read or update.
 *
 * <p>
 * This interface exists so non-mixin code can depend on a Data Energistics-owned contract
 * instead of importing mixin accessor types directly. The current field access is limited to the
 * crafting unlock stack because AdaptivePatternProviderLogic only needs that lock state.
 */
public interface PatternProviderLogicFieldAccess {

    /**
     * Returns the stack AE2 currently waits for before clearing LOCK_UNTIL_RESULT.
     *
     * <p>
     * The method is exposed to let adaptive AE2LT unlock handling compare returned stacks
     * against AE2's active unlock target without depending on the mixin accessor package.
     */
    GenericStack dataEnergistics$getUnlockStack();

    /**
     * Replaces the stack AE2 currently waits for before clearing LOCK_UNTIL_RESULT.
     *
     * <p>
     * The method is exposed to let adaptive AE2LT unlock handling reduce the remaining unlock
     * amount after a partial returned stack match.
     */
    void dataEnergistics$setUnlockStack(GenericStack unlockStack);
}

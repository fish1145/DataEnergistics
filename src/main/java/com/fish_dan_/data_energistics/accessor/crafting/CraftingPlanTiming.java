package com.fish_dan_.data_energistics.accessor.crafting;

/**
 * Exposes the elapsed time captured while AE2 calculates a native crafting plan.
 *
 * <p>
 * AE2's public crafting-plan contract does not carry timing metadata, so this bridge keeps the value attached to the
 * plan that is eventually selected for the confirmation screen.
 * </p>
 */
public interface CraftingPlanTiming {

    /**
     * @return elapsed wall-clock time in nanoseconds
     */
    long dataEnergistics$calculationNanos();

    /**
     * Mutable side of the bridge used only while AE2 publishes the freshly calculated plan.
     */
    interface Mutable extends CraftingPlanTiming {

        /**
         * Stores the elapsed time before the calculation future becomes visible to its consumer.
         *
         * @param calculationNanos elapsed wall-clock time in nanoseconds
         */
        void dataEnergistics$setCalculationNanos(long calculationNanos);
    }
}

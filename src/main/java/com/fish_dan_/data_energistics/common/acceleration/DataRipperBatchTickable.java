package com.fish_dan_.data_energistics.common.acceleration;

/**
 * Opt-in contract for block entities that can advance Data Ripper ticks without replaying their complete ticker.
 *
 * <p>
 * The natural server tick is not part of {@code additionalTicks}. Implementations advance only the extra
 * block-entity ticks requested by the Data Ripper; any independent AE grid-tick service keeps its own schedule.
 */
public interface DataRipperBatchTickable {

    /**
     * Advances extra logical server ticks in batches split at real work, capacity, and external-I/O boundaries.
     *
     * @param additionalTicks positive number of extra ticks after the target's natural tick
     */
    void advanceAdditionalTicks(int additionalTicks);
}

package com.fish_dan_.data_energistics.blockentity.tower.equalization;

/**
 * Plans one immutable, capacity-proportional FE equalization pass for Data Distribution Tower endpoints.
 *
 * <p>
 * Implementations operate only on frozen scalar snapshots. Capability simulation and mutation remain the caller's
 * responsibility so the same plan can drive simulation, real transfer, and compensation phases.
 * </p>
 */
public interface TowerEnergyEqualizer {

    /**
     * Computes the source withdrawals and sink deposits required by one frozen endpoint snapshot.
     *
     * @param snapshot ordered endpoint state captured before any transfer begins
     * @return immutable transfer plan whose source and sink totals are equal
     */
    TowerEnergyEqualizationPlan plan(TowerEnergyEqualizationSnapshot snapshot);
}

package com.fish_dan_.data_energistics.network;

/**
 * Narrow menu contract for accepting a complete Data Distribution Tower target snapshot.
 *
 * <p>
 * The network handler performs container validation and batch assembly before invoking this contract, so menu code
 * never observes a partial revision.
 * </p>
 */
public interface DataDistributionTowerTargetsReceiver {

    /**
     * Replaces the menu's target state with one complete server revision.
     *
     * @param snapshot complete immutable target list for this menu container
     */
    void receiveDataDistributionTowerTargets(DataDistributionTowerTargetsSnapshot snapshot);
}

package com.fish_dan_.data_energistics.blockentity.tower.virtual;

/**
 * Selects one tower owner for each target grid without physically merging AE grids.
 *
 * <p>
 * Candidate requests carry globally unique FIFO tokens. Selection is rebuilt in that explicit order, rejects edges
 * that would form cycles, and therefore gives every connected ownership tree exactly one primary root grid.
 * </p>
 *
 * @param <G> grid key type with stable equality and hash semantics
 * @param <T> tower key type with stable equality and hash semantics
 */
public interface VirtualGridOwnership<G, T> {

    /**
     * Adds or refreshes a tower's current local grid and availability.
     *
     * @param tower immutable tower description
     */
    void upsertTower(VirtualGridTower<G, T> tower);

    /**
     * Removes a tower and every candidate registered for it.
     *
     * @param towerKey tower key
     * @return true when a tower was removed
     */
    boolean removeTower(T towerKey);

    /**
     * Enables or disables a tower while preserving all candidate FIFO positions.
     *
     * @param towerKey  tower key
     * @param available desired availability
     * @return true when the tower exists
     */
    boolean setTowerAvailable(T towerKey, boolean available);

    /**
     * Adds a target-specific candidate or refreshes its enabled state.
     *
     * <p>
     * The FIFO token is immutable for an existing target/tower pair. The referenced tower must already exist.
     * </p>
     *
     * @param candidate immutable ownership candidate
     */
    void upsertCandidate(VirtualGridCandidate<G, T> candidate);

    /**
     * Removes one target-specific ownership candidate.
     *
     * @param targetGrid target grid key
     * @param towerKey   candidate tower key
     * @return true when a candidate was removed
     */
    boolean removeCandidate(G targetGrid, T towerKey);

    /**
     * Enables or disables one candidate without changing its FIFO token.
     *
     * @param targetGrid target grid key
     * @param towerKey   candidate tower key
     * @param enabled    desired candidate state
     * @return true when the candidate exists
     */
    boolean setCandidateEnabled(G targetGrid, T towerKey, boolean enabled);

    /**
     * Computes deterministic owner selection and cycle diagnostics.
     *
     * @return immutable ownership snapshot
     */
    VirtualGridOwnershipSnapshot<G, T> snapshot();
}

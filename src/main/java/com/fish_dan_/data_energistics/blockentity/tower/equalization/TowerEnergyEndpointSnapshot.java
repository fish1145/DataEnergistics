package com.fish_dan_.data_energistics.blockentity.tower.equalization;

import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;

/**
 * Frozen scalar state of one tower energy endpoint used by the equalization planner.
 *
 * @param endpoint    stable endpoint identity
 * @param stored      FE stored when the snapshot was captured
 * @param capacity    maximum FE the endpoint can store
 * @param extractable maximum FE the endpoint can provide in this transaction
 * @param receivable  maximum FE the endpoint can accept in this transaction
 * @param direction   transfer permissions captured with the scalar state
 * @param role        participation role used independently of transfer permissions
 */
public record TowerEnergyEndpointSnapshot(TowerEnergyEndpointId endpoint,
                                          long stored,
                                          long capacity,
                                          long extractable,
                                          long receivable,
                                          TowerEnergyDirection direction,
                                          TowerEnergyEndpointRole role) {

    /**
     * Creates a balanced snapshot with explicit transaction budgets.
     *
     * @param endpoint    stable endpoint identity
     * @param stored      FE stored when the snapshot was captured
     * @param capacity    maximum FE the endpoint can store
     * @param extractable maximum FE the endpoint can provide in this transaction
     * @param receivable  maximum FE the endpoint can accept in this transaction
     * @param direction   transfer permissions captured with the scalar state
     */
    public TowerEnergyEndpointSnapshot(TowerEnergyEndpointId endpoint,
                                       long stored,
                                       long capacity,
                                       long extractable,
                                       long receivable,
                                       TowerEnergyDirection direction) {
        this(endpoint, stored, capacity, extractable, receivable, direction, TowerEnergyEndpointRole.BALANCED);
    }

    /**
     * Creates an unrestricted snapshot for callers that only model scalar state.
     *
     * @param endpoint  stable endpoint identity
     * @param stored    FE stored when the snapshot was captured
     * @param capacity  maximum FE the endpoint can store
     * @param direction transfer permissions captured with the scalar state
     */
    public TowerEnergyEndpointSnapshot(TowerEnergyEndpointId endpoint,
                                       long stored,
                                       long capacity,
                                       TowerEnergyDirection direction) {
        this(
                endpoint,
                stored,
                capacity,
                direction.allowsExtract() ? stored : 0,
                direction.allowsReceive() ? Math.subtractExact(capacity, stored) : 0,
                direction,
                TowerEnergyEndpointRole.BALANCED);
    }

    /**
     * Validates the energy bounds and required endpoint metadata at the snapshot boundary.
     *
     * @param endpoint    stable endpoint identity
     * @param stored      FE stored when the snapshot was captured
     * @param capacity    maximum FE the endpoint can store
     * @param extractable maximum FE the endpoint can provide in this transaction
     * @param receivable  maximum FE the endpoint can accept in this transaction
     * @param direction   transfer permissions captured with the scalar state
     * @param role        participation role used independently of transfer permissions
     */
    public TowerEnergyEndpointSnapshot {
        if (stored < 0) {
            throw new IllegalArgumentException("Stored energy must not be negative");
        }
        if (capacity < 0) {
            throw new IllegalArgumentException("Energy capacity must not be negative");
        }
        if (stored > capacity) {
            throw new IllegalArgumentException("Stored energy must not exceed capacity");
        }
        if (extractable < 0 || extractable > stored) {
            throw new IllegalArgumentException("Extractable energy must remain within stored energy");
        }
        if (receivable < 0 || receivable > capacity - stored) {
            throw new IllegalArgumentException("Receivable energy must remain within free capacity");
        }
        if (!direction.allowsExtract() && extractable != 0) {
            throw new IllegalArgumentException("Non-extracting endpoint cannot expose an extraction budget");
        }
        if (!direction.allowsReceive() && receivable != 0) {
            throw new IllegalArgumentException("Non-receiving endpoint cannot expose an insertion budget");
        }
        if (role == TowerEnergyEndpointRole.BUFFER && direction != TowerEnergyDirection.BIDIRECTIONAL) {
            throw new IllegalArgumentException("Tower energy buffer must support both extraction and insertion");
        }
    }
}

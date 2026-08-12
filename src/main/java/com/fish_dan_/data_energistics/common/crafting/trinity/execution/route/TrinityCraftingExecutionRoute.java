package com.fish_dan_.data_energistics.common.crafting.trinity.execution.route;

import appeng.api.networking.IGrid;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable identity of the AE grid route used by one Trinity crafting publication.
 *
 * <p>
 * The owning grid remains the physical lease identity. The service grid is the grid whose crafting, energy and
 * storage services execute the job. Lease and membership generations prevent a detached route from becoming current
 * again merely because the same grid objects are later reattached.
 * </p>
 *
 * @param owningGrid           physical grid that owns the information-exchange-depot node
 * @param serviceGrid          effective grid providing crafting services
 * @param leaseEpoch           host lease epoch captured for this route
 * @param membershipGeneration node virtual-membership generation captured for this route
 */
public record TrinityCraftingExecutionRoute(IGrid owningGrid,
                                            IGrid serviceGrid,
                                            long leaseEpoch,
                                            long membershipGeneration) {

    /**
     * Validates the complete route identity at its creation boundary.
     */
    public TrinityCraftingExecutionRoute {
        if (owningGrid == null || serviceGrid == null) {
            throw new IllegalArgumentException("Trinity crafting route grids are required");
        }
        if (leaseEpoch < 0L) {
            throw new IllegalArgumentException("Trinity crafting route lease epoch must not be negative");
        }
        if (membershipGeneration < 0L) {
            throw new IllegalArgumentException("Trinity crafting route membership generation must not be negative");
        }
    }

    /**
     * Tests the complete identity using grid object identity and both monotonic generations.
     *
     * @param currentRoute route resolved from the current live topology
     * @return whether both values describe the same uninterrupted execution route
     */
    public boolean isCurrent(@Nullable TrinityCraftingExecutionRoute currentRoute) {
        return currentRoute != null &&
                this.owningGrid == currentRoute.owningGrid &&
                this.serviceGrid == currentRoute.serviceGrid &&
                this.leaseEpoch == currentRoute.leaseEpoch &&
                this.membershipGeneration == currentRoute.membershipGeneration;
    }
}

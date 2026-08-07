package com.fish_dan_.data_energistics.blockentity.tower.network;

import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Represents one physical energy backing exposed through multiple context-sensitive access routes.
 *
 * <p>
 * Exactly one route is selected from each frozen snapshot and retained for the complete transaction. This prevents
 * duplicate planner weight without treating route-specific permission contexts as interchangeable.
 * </p>
 */
public final class TowerEnergyTransferRouteGroupImpl implements TowerEnergyTransferEndpoint {

    /** Stable planner identity inherited from the first deterministic route. */
    private final TowerEnergyEndpointId endpoint;

    /** Deterministically ordered access routes sharing one proven backing identity. */
    private final List<TowerEnergyTransferEndpoint> routes;

    /** Route selected by the latest successful freeze and retained until the next freeze. */
    @Nullable
    private TowerEnergyTransferEndpoint selectedRoute;

    /**
     * Creates one planner endpoint from alternative routes to the same physical storage.
     *
     * @param routes non-empty deterministic access-route list
     */
    public TowerEnergyTransferRouteGroupImpl(List<TowerEnergyTransferEndpoint> routes) {
        this.routes = List.copyOf(routes);
        if (this.routes.isEmpty()) {
            throw new IllegalArgumentException("Energy transfer route group must not be empty");
        }
        this.endpoint = this.routes.getFirst().endpoint();
    }

    @Override
    public TowerEnergyEndpointId endpoint() {
        return this.endpoint;
    }

    @Override
    public TowerEnergyEndpointSnapshot freeze() {
        this.selectedRoute = null;
        TowerEnergyTransferEndpoint bestRoute = null;
        TowerEnergyEndpointSnapshot bestSnapshot = null;
        RuntimeException firstFailure = null;
        for (TowerEnergyTransferEndpoint route : this.routes) {
            TowerEnergyEndpointSnapshot snapshot;
            try {
                snapshot = route.freeze();
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
                continue;
            }
            if (bestSnapshot == null || compareRouteSnapshots(snapshot, bestSnapshot) > 0) {
                bestRoute = route;
                bestSnapshot = snapshot;
            }
        }
        if (bestRoute == null || bestSnapshot == null) {
            throw new TowerEnergyTransferException(
                    "Could not freeze any access route for " + description(), firstFailure);
        }
        this.selectedRoute = bestRoute;
        return new TowerEnergyEndpointSnapshot(
                this.endpoint,
                bestSnapshot.stored(),
                bestSnapshot.capacity(),
                bestSnapshot.extractable(),
                bestSnapshot.receivable(),
                bestSnapshot.direction());
    }

    @Override
    public long simulateExtraction(long amount) {
        return selectedRoute().simulateExtraction(amount);
    }

    @Override
    public long extract(long amount) {
        return selectedRoute().extract(amount);
    }

    @Override
    public long compensateExtraction(long amount) {
        return selectedRoute().compensateExtraction(amount);
    }

    @Override
    public long simulateInsertion(long amount) {
        return selectedRoute().simulateInsertion(amount);
    }

    @Override
    public long insert(long amount) {
        return selectedRoute().insert(amount);
    }

    @Override
    public void publishMutation() {
        selectedRoute().publishMutation();
    }

    @Override
    public String description() {
        return "energy backing " + this.endpoint + " through " + this.routes.size() + " access routes";
    }

    /** Keeps the highest current transfer budget, then the widest visible physical state. */
    private static int compareRouteSnapshots(
                                             TowerEnergyEndpointSnapshot left,
                                             TowerEnergyEndpointSnapshot right) {
        // Two non-negative long components fit exactly in the unsigned range up to 2^64 - 2.
        long leftBudget = left.extractable() + left.receivable();
        long rightBudget = right.extractable() + right.receivable();
        int result = Long.compareUnsigned(leftBudget, rightBudget);
        if (result != 0) {
            return result;
        }
        result = Long.compare(left.capacity(), right.capacity());
        return result != 0 ? result : Long.compare(left.stored(), right.stored());
    }

    /** Fails fast when transaction methods are invoked without their matching frozen route. */
    private TowerEnergyTransferEndpoint selectedRoute() {
        TowerEnergyTransferEndpoint route = this.selectedRoute;
        if (route == null) {
            throw new IllegalStateException("Energy access route was not frozen before transaction execution");
        }
        return route;
    }
}

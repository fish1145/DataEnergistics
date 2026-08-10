package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;

import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Represents one physical energy backing exposed through multiple context-sensitive access routes.
 *
 * <p>
 * Extraction and insertion routes are selected independently from each frozen snapshot and retained for the complete
 * transaction. This prevents duplicate planner weight without discarding side-specific input or output access.
 * </p>
 */
public final class MultiRouteEnergyTransferEndpoint implements TowerEnergyTransferEndpoint {

    /**
     * Stable planner identity inherited from the first deterministic route.
     */
    private final TowerEnergyEndpointId endpoint;

    /**
     * Deterministically ordered access routes sharing one proven backing identity.
     */
    private final List<TowerEnergyTransferEndpoint> routes;

    /**
     * Extraction route selected by the latest successful freeze.
     */
    @Nullable
    private TowerEnergyTransferEndpoint selectedExtractionRoute;

    /**
     * Insertion route selected by the latest successful freeze.
     */
    @Nullable
    private TowerEnergyTransferEndpoint selectedInsertionRoute;

    /**
     * Routes that performed a real mutation since the latest freeze.
     */
    private final Set<TowerEnergyTransferEndpoint> mutatedRoutes = Collections.newSetFromMap(new IdentityHashMap<>());

    /**
     * Prevents a persistently unavailable alternative route from logging every server tick.
     */
    private boolean routeFailureReported;

    /**
     * Creates one planner endpoint from alternative routes to the same physical storage.
     *
     * @param routes non-empty deterministic access-route list
     */
    public MultiRouteEnergyTransferEndpoint(List<TowerEnergyTransferEndpoint> routes) {
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
        this.selectedExtractionRoute = null;
        this.selectedInsertionRoute = null;
        this.mutatedRoutes.clear();
        TowerEnergyEndpointSnapshot physicalSnapshot = null;
        TowerEnergyEndpointSnapshot extractionSnapshot = null;
        TowerEnergyEndpointSnapshot insertionSnapshot = null;
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

            if (physicalSnapshot == null) {
                physicalSnapshot = snapshot;
            } else if (snapshot.stored() != physicalSnapshot.stored() || snapshot.capacity() != physicalSnapshot.capacity()) {
                throw new TowerEnergyTransferException(
                        "Energy routes for one backing reported inconsistent state: " + physicalSnapshot.stored() + "/" + physicalSnapshot.capacity() + " and " + snapshot.stored() + "/" + snapshot.capacity() + " through " + description());
            }
            if (snapshot.direction().allowsExtract() && (extractionSnapshot == null || snapshot.extractable() > extractionSnapshot.extractable())) {
                this.selectedExtractionRoute = route;
                extractionSnapshot = snapshot;
            }
            if (snapshot.direction().allowsReceive() && (insertionSnapshot == null || snapshot.receivable() > insertionSnapshot.receivable())) {
                this.selectedInsertionRoute = route;
                insertionSnapshot = snapshot;
            }
        }
        if (physicalSnapshot == null) {
            throw new TowerEnergyTransferException(
                    "Could not freeze any access route for " + description(), firstFailure);
        }
        if (firstFailure == null) {
            this.routeFailureReported = false;
        } else if (!this.routeFailureReported) {
            Data_Energistics.LOGGER.warn(
                    "Isolated an unavailable tower energy access route for {}; using the remaining routes",
                    description(),
                    firstFailure);
            this.routeFailureReported = true;
        }

        boolean canExtract = this.selectedExtractionRoute != null;
        boolean canReceive = this.selectedInsertionRoute != null;
        TowerEnergyDirection direction = TowerEnergyDirection.fromPermissions(canExtract, canReceive);
        if (direction == null) {
            throw new TowerEnergyTransferException("Frozen energy backing exposes no usable route: " + description());
        }
        return new TowerEnergyEndpointSnapshot(
                this.endpoint,
                physicalSnapshot.stored(),
                physicalSnapshot.capacity(),
                extractionSnapshot == null ? 0 : extractionSnapshot.extractable(),
                insertionSnapshot == null ? 0 : insertionSnapshot.receivable(),
                direction);
    }

    @Override
    public long simulateExtraction(long amount) {
        return selectedExtractionRoute().simulateExtraction(amount);
    }

    @Override
    public long extractionQuantum() {
        return selectedExtractionRoute().extractionQuantum();
    }

    @Override
    public long extract(long amount) {
        TowerEnergyTransferEndpoint route = selectedExtractionRoute();
        long extracted = route.extract(amount);
        recordMutation(route, extracted);
        return extracted;
    }

    @Override
    public long compensateExtraction(long amount) {
        TowerEnergyTransferEndpoint route = selectedExtractionRoute();
        long restored = route.compensateExtraction(amount);
        recordMutation(route, restored);
        return restored;
    }

    @Override
    public long simulateInsertion(long amount) {
        return selectedInsertionRoute().simulateInsertion(amount);
    }

    @Override
    public long insertionQuantum() {
        return selectedInsertionRoute().insertionQuantum();
    }

    @Override
    public long insert(long amount) {
        TowerEnergyTransferEndpoint route = selectedInsertionRoute();
        long inserted = route.insert(amount);
        recordMutation(route, inserted);
        return inserted;
    }

    @Override
    public void publishMutation() {
        RuntimeException firstFailure = null;
        for (TowerEnergyTransferEndpoint route : this.mutatedRoutes) {
            try {
                route.publishMutation();
            } catch (RuntimeException exception) {
                if (firstFailure == null) {
                    firstFailure = exception;
                } else {
                    firstFailure.addSuppressed(exception);
                }
            }
        }
        this.mutatedRoutes.clear();
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    @Override
    public String description() {
        return "energy backing " + this.endpoint + " through " + this.routes.size() + " access routes";
    }

    /**
     * Records only confirmed real mutations so callbacks are published through the route that changed state.
     */
    private void recordMutation(TowerEnergyTransferEndpoint route, long amount) {
        if (amount > 0) {
            this.mutatedRoutes.add(route);
        }
    }

    /**
     * Fails fast when extraction is invoked without a matching frozen route.
     */
    private TowerEnergyTransferEndpoint selectedExtractionRoute() {
        TowerEnergyTransferEndpoint route = this.selectedExtractionRoute;
        if (route == null) {
            throw new IllegalStateException("Energy extraction route was not frozen before transaction execution");
        }
        return route;
    }

    /**
     * Fails fast when insertion is invoked without a matching frozen route.
     */
    private TowerEnergyTransferEndpoint selectedInsertionRoute() {
        TowerEnergyTransferEndpoint route = this.selectedInsertionRoute;
        if (route == null) {
            throw new IllegalStateException("Energy insertion route was not frozen before transaction execution");
        }
        return route;
    }
}

package com.fish_dan_.data_energistics.blockentity.tower.network.energy;

import com.fish_dan_.data_energistics.Data_Energistics;
import com.fish_dan_.data_energistics.blockentity.tower.energy.TowerEnergyDirection;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointId;
import com.fish_dan_.data_energistics.blockentity.tower.equalization.TowerEnergyEndpointSnapshot;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * Exposes one primary Grid's reconciled cross-dimensional FE topology as a direct capability port.
 *
 * <p>
 * The port is server-thread confined. It preserves stable endpoint order, simulates every mutation first, isolates a
 * failing route, and never reaches back into the obsolete position-only tower cluster.
 * </p>
 */
public final class SharedTowerEnergyPort {

    private static final int FAILURE_LOG_INTERVAL_TICKS = 100;

    private final LongSupplier gameTime;
    private final Map<AccessKey, Integer> extractionCursors = new HashMap<>();
    private final Map<AccessKey, Integer> insertionCursors = new HashMap<>();
    private final Map<FailureKey, Long> lastFailureLogTicks = new HashMap<>();
    private final Map<AccessKey, TowerEnergyAccessSnapshot> cachedSnapshots = new HashMap<>();
    private List<TowerEnergyTransferEndpoint> endpoints = List.of();
    private long snapshotTick = Long.MIN_VALUE;

    /**
     * Creates a shared port whose cache and diagnostics use the owning Grid's server time.
     *
     * @param gameTime current server game-time supplier
     */
    public SharedTowerEnergyPort(LongSupplier gameTime) {
        this.gameTime = gameTime;
    }

    /**
     * Replaces the complete reconciled topology and clears topology-dependent cursors and snapshots.
     *
     * @param endpoints stable ordered endpoint topology
     */
    public void replaceEndpoints(List<TowerEnergyTransferEndpoint> endpoints) {
        this.endpoints = List.copyOf(endpoints);
        this.extractionCursors.clear();
        this.insertionCursors.clear();
        invalidateSnapshots();
    }

    /**
     * Captures the current aggregate capability state, reusing one immutable result per exclusion and server tick.
     *
     * @param requesterDimension dimension containing the requesting tower
     * @param excludedPosition   adjacent capability owner that must not feed itself, or {@code null}
     * @return current shared energy state
     */
    public TowerEnergyAccessSnapshot snapshot(
                                              ResourceLocation requesterDimension, @Nullable BlockPos excludedPosition) {
        long currentTick = this.gameTime.getAsLong();
        if (this.snapshotTick != currentTick) {
            this.cachedSnapshots.clear();
            this.snapshotTick = currentTick;
        }

        AccessKey accessKey = AccessKey.of(requesterDimension, excludedPosition);
        TowerEnergyAccessSnapshot cached = this.cachedSnapshots.get(accessKey);
        if (cached != null) {
            return cached;
        }

        long stored = 0;
        long sourceCapacity = 0;
        long receivable = 0;
        boolean canExtract = false;
        boolean canReceive = false;
        for (TowerEnergyTransferEndpoint endpoint : this.endpoints) {
            if (accessKey.excludes(endpoint.endpoint())) {
                continue;
            }
            TowerEnergyEndpointSnapshot endpointSnapshot;
            try {
                endpointSnapshot = endpoint.freeze();
            } catch (RuntimeException exception) {
                logFailure(endpoint, Operation.SNAPSHOT, exception);
                continue;
            }
            TowerEnergyDirection direction = endpointSnapshot.direction();
            if (direction.allowsExtract()) {
                canExtract = true;
                stored = saturatingAdd(stored, endpointSnapshot.stored());
                sourceCapacity = saturatingAdd(sourceCapacity, endpointSnapshot.capacity());
            }
            if (direction.allowsReceive()) {
                canReceive = true;
                receivable = saturatingAdd(receivable, endpointSnapshot.receivable());
            }
        }

        TowerEnergyAccessSnapshot result = new TowerEnergyAccessSnapshot(
                stored, sourceCapacity, receivable, canExtract, canReceive);
        this.cachedSnapshots.put(accessKey, result);
        return result;
    }

    /**
     * Inserts FE across the shared topology.
     *
     * @param amount             requested non-negative FE
     * @param simulate           whether to simulate without mutation
     * @param requesterDimension dimension containing the requesting tower
     * @param excludedPosition   adjacent capability owner that must not feed itself, or {@code null}
     * @return accepted FE in {@code [0, amount]}
     */
    public long insert(
                       long amount,
                       boolean simulate,
                       ResourceLocation requesterDimension,
                       @Nullable BlockPos excludedPosition) {
        return transfer(amount, simulate, requesterDimension, excludedPosition, true);
    }

    /**
     * Extracts FE across the shared topology.
     *
     * @param amount             requested non-negative FE
     * @param simulate           whether to simulate without mutation
     * @param requesterDimension dimension containing the requesting tower
     * @param excludedPosition   adjacent capability owner that must not feed itself, or {@code null}
     * @return extracted FE in {@code [0, amount]}
     */
    public long extract(
                        long amount,
                        boolean simulate,
                        ResourceLocation requesterDimension,
                        @Nullable BlockPos excludedPosition) {
        return transfer(amount, simulate, requesterDimension, excludedPosition, false);
    }

    private long transfer(long amount,
                          boolean simulate,
                          ResourceLocation requesterDimension,
                          @Nullable BlockPos excludedPosition,
                          boolean insertion) {
        if (amount < 0) {
            throw new IllegalArgumentException("Tower energy access amount must be non-negative");
        }
        if (amount == 0 || this.endpoints.isEmpty()) {
            return 0;
        }

        AccessKey accessKey = AccessKey.of(requesterDimension, excludedPosition);
        Map<AccessKey, Integer> cursors = insertion ? this.insertionCursors : this.extractionCursors;
        int endpointCount = this.endpoints.size();
        int startIndex = Math.floorMod(cursors.getOrDefault(accessKey, 0), endpointCount);
        int lastSuccessfulIndex = -1;
        long transferredTotal = 0;
        long remaining = amount;
        for (int offset = 0; offset < endpointCount && remaining > 0; offset++) {
            int endpointIndex = (startIndex + offset) % endpointCount;
            TowerEnergyTransferEndpoint endpoint = this.endpoints.get(endpointIndex);
            if (accessKey.excludes(endpoint.endpoint())) {
                continue;
            }

            try {
                TowerEnergyEndpointSnapshot endpointSnapshot = endpoint.freeze();
                TowerEnergyDirection direction = endpointSnapshot.direction();
                if (insertion ? !direction.allowsReceive() : !direction.allowsExtract()) {
                    continue;
                }
                long budget = insertion ? endpointSnapshot.receivable() : endpointSnapshot.extractable();
                long requested = Math.min(remaining, budget);
                if (requested == 0) {
                    continue;
                }
                long accepted = insertion ? endpoint.simulateInsertion(requested) : endpoint.simulateExtraction(requested);
                if (accepted == 0) {
                    continue;
                }
                long transferred = simulate ? accepted : insertion ? endpoint.insert(accepted) : endpoint.extract(accepted);
                if (transferred < 0 || transferred > accepted) {
                    throw new IllegalStateException(
                            "Tower energy endpoint violated its simulated transfer bound: " + endpoint.endpoint());
                }
                if (!simulate && transferred > 0) {
                    publishMutation(endpoint);
                }
                transferredTotal = Math.addExact(transferredTotal, transferred);
                remaining -= transferred;
                if (transferred > 0) {
                    lastSuccessfulIndex = endpointIndex;
                }
            } catch (RuntimeException exception) {
                logFailure(endpoint, insertion ? Operation.INSERT : Operation.EXTRACT, exception);
            }
        }

        if (!simulate && lastSuccessfulIndex >= 0) {
            cursors.put(accessKey, (lastSuccessfulIndex + 1) % endpointCount);
            invalidateSnapshots();
        }
        return transferredTotal;
    }

    private void publishMutation(TowerEnergyTransferEndpoint endpoint) {
        try {
            endpoint.publishMutation();
        } catch (RuntimeException exception) {
            logFailure(endpoint, Operation.PUBLISH, exception);
        }
    }

    private void invalidateSnapshots() {
        this.cachedSnapshots.clear();
        this.snapshotTick = Long.MIN_VALUE;
    }

    private void logFailure(TowerEnergyTransferEndpoint endpoint, Operation operation, RuntimeException exception) {
        long currentTick = this.gameTime.getAsLong();
        FailureKey failureKey = new FailureKey(endpoint.endpoint(), operation);
        Long previousTick = this.lastFailureLogTicks.get(failureKey);
        if (previousTick != null && currentTick - previousTick < FAILURE_LOG_INTERVAL_TICKS) {
            return;
        }
        this.lastFailureLogTicks.put(failureKey, currentTick);
        Data_Energistics.LOGGER.error(
                "Shared tower energy port failed to {} endpoint {}",
                operation.description,
                endpoint.endpoint(),
                exception);
    }

    private static long saturatingAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private enum Operation {

        SNAPSHOT("read"),
        INSERT("insert into"),
        EXTRACT("extract from"),
        PUBLISH("publish");

        private final String description;

        Operation(String description) {
            this.description = description;
        }
    }

    private record AccessKey(ResourceLocation dimensionId, BlockPos position, boolean hasExclusion) {

        private AccessKey {
            position = position.immutable();
        }

        private static AccessKey of(ResourceLocation requesterDimension, @Nullable BlockPos excludedPosition) {
            return excludedPosition == null ? new AccessKey(requesterDimension, BlockPos.ZERO, false) : new AccessKey(requesterDimension, excludedPosition, true);
        }

        private boolean excludes(TowerEnergyEndpointId endpoint) {
            return this.hasExclusion && this.dimensionId.equals(endpoint.dimensionId()) && this.position.equals(endpoint.pos());
        }
    }

    private record FailureKey(TowerEnergyEndpointId endpoint, Operation operation) {}
}

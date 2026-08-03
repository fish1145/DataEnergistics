package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.shard;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.CapacitySlice;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.CapacitySlicePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.CapacitySlicePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.MachineTargetId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fixed fair-lock shard implementation with counted provider-route and exclusive physical-machine reservations.
 */
final class ProviderShardDispatcherImpl implements ProviderShardDispatcher {

    private final ReentrantLock[] shards;
    private final Object reservationLock = new Object();
    private final Map<ProviderRouteKey, Long> reservedByProviderRoute = new HashMap<>();
    private final Map<MachineTargetId, ReservationImpl> reservedMachines = new HashMap<>();

    ProviderShardDispatcherImpl(int shardCount) {
        if (shardCount <= 0) {
            throw new IllegalArgumentException("Provider shard count must be positive");
        }
        this.shards = new ReentrantLock[shardCount];
        for (int index = 0; index < shardCount; index++) {
            this.shards[index] = new ReentrantLock(true);
        }
    }

    @Override
    public Result selectAndReserve(CraftingDispatchProposalRequest request, CapacitySlicePlanner planner) {
        if (request == null) {
            throw new IllegalArgumentException("Provider shard request must not be null");
        }
        if (planner == null) {
            throw new IllegalArgumentException("Provider shard capacity planner must not be null");
        }

        Set<ProviderRouteKey> inspected = new HashSet<>();
        int cursor = request.cursor();
        while (inspected.size() < request.candidates().size()) {
            CapacitySlicePlan plan = planner.plan(
                    maskInspected(request, inspected),
                    request.remainingCrafts(),
                    1,
                    cursor);
            if (plan.slices().isEmpty()) {
                return NoCapacity.INSTANCE;
            }
            CapacitySlice slice = plan.slices().getFirst();
            ProviderCapacitySnapshot target = originalTarget(request, slice.target());
            ProviderRouteKey routeKey = ProviderRouteKey.from(target);
            inspected.add(routeKey);
            cursor = plan.nextCursor();

            ReentrantLock shard = this.shards[shardIndex(target.providerId())];
            shard.lock();
            try {
                long requested = offeredCount(target, slice, request.remainingCrafts().longValueExact());
                ReservationImpl reservation = reserve(routeKey, target, requested);
                if (reservation != null) {
                    return new Reserved(target, reservation.logicalCrafts, cursor, reservation);
                }
            } finally {
                shard.unlock();
            }
        }
        return NoCapacity.INSTANCE;
    }

    private ReservationImpl reserve(ProviderRouteKey routeKey,
                                    ProviderCapacitySnapshot target,
                                    long requested) {
        synchronized (this.reservationLock) {
            long alreadyReserved = this.reservedByProviderRoute.getOrDefault(routeKey, 0L);
            long available = availableCapacity(target.capacity(), alreadyReserved);
            long logicalCrafts = Math.min(requested, available);
            Optional<MachineTargetId> machineTarget = target.machineTargetId();
            if (logicalCrafts <= 0L || machineTarget.filter(this.reservedMachines::containsKey).isPresent()) {
                return null;
            }

            ReservationImpl reservation = new ReservationImpl(
                    this,
                    routeKey,
                    machineTarget,
                    logicalCrafts);
            this.reservedByProviderRoute.put(routeKey, Math.addExact(alreadyReserved, logicalCrafts));
            machineTarget.ifPresent(machine -> this.reservedMachines.put(machine, reservation));
            return reservation;
        }
    }

    private void release(ReservationImpl reservation) {
        synchronized (this.reservationLock) {
            long reserved = this.reservedByProviderRoute.getOrDefault(reservation.routeKey, 0L);
            long remaining = Math.subtractExact(reserved, reservation.logicalCrafts);
            if (remaining == 0L) {
                this.reservedByProviderRoute.remove(reservation.routeKey);
            } else {
                this.reservedByProviderRoute.put(reservation.routeKey, remaining);
            }
            reservation.machineTarget.ifPresent(machine -> {
                if (!this.reservedMachines.remove(machine, reservation)) {
                    throw new IllegalStateException("Provider shard machine reservation ownership was lost");
                }
            });
        }
    }

    private int shardIndex(CraftingProviderId providerId) {
        int hash = 31 * Long.hashCode(providerId.publicationScope()) +
                Long.hashCode(providerId.registrationSequence());
        return Math.floorMod(hash, this.shards.length);
    }

    private static long availableCapacity(DispatchCapacity capacity, long alreadyReserved) {
        if (capacity instanceof DispatchCapacity.Known(long known)) {
            return Math.max(0L, Math.subtractExact(known, alreadyReserved));
        }
        return alreadyReserved == 0L ? 1L : 0L;
    }

    private static long offeredCount(ProviderCapacitySnapshot target,
                                     CapacitySlice slice,
                                     long maximumCount) {
        return switch (target.routingMode()) {
            case TARGETED -> Math.min(slice.logicalCrafts(), maximumCount);
            case AGGREGATE -> maximumCount;
            case ORDERED, UNKNOWN -> 1L;
        };
    }

    private static List<ProviderCapacitySnapshot> maskInspected(
                                                                CraftingDispatchProposalRequest request,
                                                                Set<ProviderRouteKey> inspected) {
        return request.candidates().stream()
                .map(candidate -> inspected.contains(ProviderRouteKey.from(candidate)) ? mask(candidate) : candidate)
                .toList();
    }

    private static ProviderCapacitySnapshot mask(ProviderCapacitySnapshot candidate) {
        return new ProviderCapacitySnapshot(
                candidate.providerId(),
                candidate.route(),
                candidate.machineTargetId(),
                candidate.patternIdentity(),
                candidate.publicationRevision(),
                candidate.capacityRevision(),
                candidate.captureTick(),
                candidate.routingMode(),
                new DispatchCapacity.Known(0L),
                candidate.maximumSingleBatch());
    }

    private static ProviderCapacitySnapshot originalTarget(CraftingDispatchProposalRequest request,
                                                           ProviderCapacitySnapshot selected) {
        for (ProviderCapacitySnapshot candidate : request.candidates()) {
            if (candidate.providerId().equals(selected.providerId()) && candidate.route().equals(selected.route())) {
                return candidate;
            }
        }
        throw new IllegalStateException("Provider shard selected a target outside its request");
    }

    /** Provider-local reservation key; machine identity is independently global. */
    private record ProviderRouteKey(CraftingProviderId providerId, CraftingDispatchTarget route) {

        private static ProviderRouteKey from(ProviderCapacitySnapshot target) {
            return new ProviderRouteKey(target.providerId(), target.route());
        }
    }

    /** Idempotent reservation retained by the proposal ticket until server-thread consumption or cancellation. */
    private static final class ReservationImpl implements Reservation {

        private final ProviderShardDispatcherImpl owner;
        private final ProviderRouteKey routeKey;
        private final Optional<MachineTargetId> machineTarget;
        private final long logicalCrafts;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ReservationImpl(ProviderShardDispatcherImpl owner,
                                ProviderRouteKey routeKey,
                                Optional<MachineTargetId> machineTarget,
                                long logicalCrafts) {
            this.owner = owner;
            this.routeKey = routeKey;
            this.machineTarget = machineTarget;
            this.logicalCrafts = logicalCrafts;
        }

        @Override
        public void close() {
            if (this.closed.compareAndSet(false, true)) {
                this.owner.release(this);
            }
        }
    }
}

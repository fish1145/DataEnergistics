package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.shard;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.DispatchProposalCandidatePlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.DispatchProposalCandidatePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.MachineTargetId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Fixed fair-lock shard implementation with counted provider-route and exclusive physical-machine reservations.
 */
final class ProviderShardDispatcherImpl implements ProviderShardDispatcher {

    private final ProviderShard[] shards;
    private final Map<MachineTargetId, ReservationImpl> reservedMachines = new ConcurrentHashMap<>();

    ProviderShardDispatcherImpl(int shardCount) {
        if (shardCount <= 0) {
            throw new IllegalArgumentException("Provider shard count must be positive");
        }
        this.shards = new ProviderShard[shardCount];
        for (int index = 0; index < shardCount; index++) {
            this.shards[index] = new ProviderShard();
        }
    }

    @Override
    public Result selectAndReserve(
                                   CraftingDispatchProposalRequest request,
                                   DispatchProposalCandidatePlanner planner,
                                   int providerQuantum) {
        if (request == null) {
            throw new IllegalArgumentException("Provider shard request must not be null");
        }
        if (planner == null) {
            throw new IllegalArgumentException("Provider shard capacity planner must not be null");
        }
        if (providerQuantum <= 0) {
            throw new IllegalArgumentException("Provider shard proposal quantum must be positive");
        }

        DispatchProposalCandidatePlan candidatePlan = planner.plan(request);
        for (DispatchProposalCandidatePlan.Candidate candidate : candidatePlan.candidates()) {
            ProviderCapacitySnapshot target = candidate.target();
            ProviderShard shard = this.shards[shardIndex(target.providerId())];
            shard.lock.lock();
            try {
                ReservationImpl reservation = reserve(
                        shard,
                        ProviderRouteKey.from(target),
                        target,
                        candidate.logicalCrafts(),
                        providerQuantum);
                if (reservation != null) {
                    return new Reserved(
                            target,
                            reservation.logicalCrafts,
                            candidate.nextCursor(),
                            reservation);
                }
            } finally {
                shard.lock.unlock();
            }
        }
        return NoCapacity.INSTANCE;
    }

    private ReservationImpl reserve(ProviderShard shard,
                                    ProviderRouteKey routeKey,
                                    ProviderCapacitySnapshot target,
                                    long requested,
                                    int providerQuantum) {
        int reservedProposals = shard.reservedProposalsByProvider.getOrDefault(target.providerId(), 0);
        if (reservedProposals >= providerQuantum) {
            return null;
        }
        long alreadyReserved = shard.reservedByProviderRoute.getOrDefault(routeKey, 0L);
        long available = availableCapacity(target.capacity(), alreadyReserved);
        long logicalCrafts = Math.min(requested, available);
        if (logicalCrafts <= 0L) {
            return null;
        }

        ReservationImpl reservation = new ReservationImpl(
                this,
                routeKey,
                target.providerId(),
                target.machineTargetId(),
                logicalCrafts);
        Optional<MachineTargetId> machineTarget = target.machineTargetId();
        if (machineTarget.isPresent() &&
                this.reservedMachines.putIfAbsent(machineTarget.orElseThrow(), reservation) != null) {
            return null;
        }
        shard.reservedByProviderRoute.put(routeKey, Math.addExact(alreadyReserved, logicalCrafts));
        shard.reservedProposalsByProvider.put(target.providerId(), Math.incrementExact(reservedProposals));
        return reservation;
    }

    private void release(ReservationImpl reservation) {
        ProviderShard shard = this.shards[shardIndex(reservation.providerId)];
        shard.lock.lock();
        try {
            long reserved = shard.reservedByProviderRoute.getOrDefault(reservation.routeKey, 0L);
            long remaining = Math.subtractExact(reserved, reservation.logicalCrafts);
            if (remaining == 0L) {
                shard.reservedByProviderRoute.remove(reservation.routeKey);
            } else {
                shard.reservedByProviderRoute.put(reservation.routeKey, remaining);
            }
            int providerProposals = Math.decrementExact(
                    shard.reservedProposalsByProvider.get(reservation.providerId));
            if (providerProposals == 0) {
                shard.reservedProposalsByProvider.remove(reservation.providerId);
            } else {
                shard.reservedProposalsByProvider.put(reservation.providerId, providerProposals);
            }
            reservation.machineTarget.ifPresent(machine -> {
                if (!this.reservedMachines.remove(machine, reservation)) {
                    throw new IllegalStateException("Provider shard machine reservation ownership was lost");
                }
            });
        } finally {
            shard.lock.unlock();
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

    /**
     * Provider-local reservation key; machine identity is independently global.
     */
    private record ProviderRouteKey(CraftingProviderId providerId, CraftingDispatchTarget route) {

        private static ProviderRouteKey from(ProviderCapacitySnapshot target) {
            return new ProviderRouteKey(target.providerId(), target.route());
        }
    }

    /**
     * Provider route and quantum state guarded by one fair shard lock.
     */
    private static final class ProviderShard {

        private final ReentrantLock lock = new ReentrantLock(true);
        private final Map<ProviderRouteKey, Long> reservedByProviderRoute = new HashMap<>();
        private final Map<CraftingProviderId, Integer> reservedProposalsByProvider = new HashMap<>();
    }

    /**
     * Idempotent reservation retained by the proposal ticket until server-thread consumption or cancellation.
     */
    private static final class ReservationImpl implements Reservation {

        private final ProviderShardDispatcherImpl owner;
        private final ProviderRouteKey routeKey;
        private final CraftingProviderId providerId;
        private final Optional<MachineTargetId> machineTarget;
        private final long logicalCrafts;
        private final AtomicBoolean closed = new AtomicBoolean();

        private ReservationImpl(ProviderShardDispatcherImpl owner,
                                ProviderRouteKey routeKey,
                                CraftingProviderId providerId,
                                Optional<MachineTargetId> machineTarget,
                                long logicalCrafts) {
            this.owner = owner;
            this.routeKey = routeKey;
            this.providerId = providerId;
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

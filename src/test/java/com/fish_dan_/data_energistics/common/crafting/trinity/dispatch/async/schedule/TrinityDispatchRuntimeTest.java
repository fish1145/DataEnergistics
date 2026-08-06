package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchCursor;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.runtime.TrinityWorkerProposalCoordinator;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.shard.ProviderShardDispatcher;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.DispatchProposalCandidatePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.ProviderCapacityCapture;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.ProviderCapacityCaptureKey;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.MachineTargetId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityComputationCache;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityDispatchRuntimeTest {

    private final List<TrinityComputationCache> computationCaches = new ArrayList<>();

    @AfterEach
    void closeComputationCaches() {
        this.computationCaches.forEach(TrinityComputationCache::close);
        this.computationCaches.clear();
    }

    @Test
    void proposalLeaseSelectsOneImmutableTargetAndRejectsASecondWorkerTicket() throws InterruptedException {
        DispatchProposalScheduler scheduler = createScheduler(new DispatchProposalLimits(1, 4, 4, 16));
        try {
            UUID runtimeId = UUID.randomUUID();
            CraftingDispatchProposalRequest request = request(1L, runtimeId, 1, 0L, 6L);
            CountDownLatch completed = new CountDownLatch(1);
            DispatchProposalScheduler.Accepted accepted = assertInstanceOf(
                    DispatchProposalScheduler.Accepted.class,
                    scheduler.submit(request, completed::countDown));

            DispatchProposalScheduler.Rejected duplicate = assertInstanceOf(
                    DispatchProposalScheduler.Rejected.class,
                    scheduler.submit(request, () -> {}));
            assertEquals(DispatchProposalScheduler.RejectionReason.WORKER_BUSY, duplicate.reason());
            assertTrue(completed.await(5L, TimeUnit.SECONDS));
            DispatchProposalTicket.Ready ready = assertInstanceOf(
                    DispatchProposalTicket.Ready.class,
                    accepted.ticket().state());
            assertEquals(request.lease(), ready.proposal().lease());
            assertEquals(request.capacity().snapshots().getFirst(), ready.proposal().target());
            assertEquals(6L, ready.proposal().logicalCrafts());

            accepted.ticket().close();
            DispatchProposalScheduler.Accepted retried = assertInstanceOf(
                    DispatchProposalScheduler.Accepted.class,
                    scheduler.submit(request, () -> {}));
            retried.ticket().close();
            DispatchProposalMetrics metrics = scheduler.snapshotAndResetMetrics(1L);
            assertEquals(2, metrics.admitted());
            assertEquals(1, metrics.rejected());
            assertTrue(metrics.completed() >= 1);
            assertEquals(0, metrics.outstanding());
            assertEquals(4, metrics.queueCapacity());
        } finally {
            scheduler.close();
        }
    }

    @Test
    void queueRejectionReleasesWorkerAdmissionWithoutRunningResourceLogic() throws InterruptedException {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        DispatchProposalCandidatePlanner delegate = createCandidatePlanner();
        DispatchProposalCandidatePlanner blockingPlanner = request -> {
            running.countDown();
            try {
                if (!release.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out while holding the dispatch proposal worker");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Dispatch proposal worker was interrupted", exception);
            }
            return delegate.plan(request);
        };
        DispatchProposalSchedulerImpl scheduler = new DispatchProposalSchedulerImpl(
                new DispatchProposalLimits(1, 2, 4, 16),
                blockingPlanner);
        try {
            UUID runtimeId = UUID.randomUUID();
            DispatchProposalScheduler.Accepted first = assertInstanceOf(
                    DispatchProposalScheduler.Accepted.class,
                    scheduler.submit(request(2L, runtimeId, 1, 0L, 1L), () -> {}));
            assertTrue(running.await(5L, TimeUnit.SECONDS));
            DispatchProposalScheduler.Accepted second = assertInstanceOf(
                    DispatchProposalScheduler.Accepted.class,
                    scheduler.submit(request(2L, runtimeId, 2, 0L, 1L), () -> {}));
            DispatchProposalScheduler.Rejected highWater = assertInstanceOf(
                    DispatchProposalScheduler.Rejected.class,
                    scheduler.submit(
                            request(2L, runtimeId, 4, 0L, 1L),
                            () -> {},
                            new DispatchProposalPolicy(4, 4, 1, true)));
            assertEquals(DispatchProposalScheduler.RejectionReason.HIGH_WATER, highWater.reason());
            DispatchProposalScheduler.Accepted third = assertInstanceOf(
                    DispatchProposalScheduler.Accepted.class,
                    scheduler.submit(request(2L, runtimeId, 4, 0L, 1L), () -> {}));
            TrinityWorkerProposalCoordinator deferredWorker = TrinityWorkerProposalCoordinator.create(() -> scheduler);
            CraftingDispatchProposalRequest deferredRequest = request(2L, runtimeId, 3, 0L, 1L);
            TrinityWorkerProposalCoordinator.Deferred full = assertInstanceOf(
                    TrinityWorkerProposalCoordinator.Deferred.class,
                    deferredWorker.submit(deferredRequest, deferredRequest, () -> {}));
            assertEquals(TrinityWorkerProposalCoordinator.DeferredReason.QUEUE_FULL, full.reason());

            release.countDown();
            first.ticket().close();
            second.ticket().close();
            third.ticket().close();
            assertInstanceOf(
                    TrinityWorkerProposalCoordinator.Pending.class,
                    deferredWorker.submit(deferredRequest, deferredRequest, () -> {}));
            deferredWorker.cancel();
        } finally {
            release.countDown();
            scheduler.close();
        }
    }

    @Test
    void governorPolicyControlsAsyncAdmissionAndProviderQuantumWithoutSplittingLogicalCrafts()
                                                                                               throws InterruptedException {
        DispatchProposalScheduler scheduler = createScheduler(new DispatchProposalLimits(2, 8, 8, 16));
        try {
            UUID runtimeId = UUID.randomUUID();
            CraftingDispatchProposalRequest disabledRequest = request(5L, runtimeId, 1, 0L, 6L);
            DispatchProposalScheduler.Rejected disabled = assertInstanceOf(
                    DispatchProposalScheduler.Rejected.class,
                    scheduler.submit(
                            disabledRequest,
                            () -> {},
                            new DispatchProposalPolicy(1, 1, 1, false)));
            assertEquals(DispatchProposalScheduler.RejectionReason.DISABLED, disabled.reason());

            DispatchProposalPolicy singleActor = new DispatchProposalPolicy(1, 8, 8, true);
            DispatchProposalScheduler.Accepted actorOwner = submitAndAwait(scheduler, disabledRequest, singleActor);
            DispatchProposalScheduler.Rejected actorLimit = assertInstanceOf(
                    DispatchProposalScheduler.Rejected.class,
                    scheduler.submit(
                            request(5L, runtimeId, 2, 0L, 1L),
                            () -> {},
                            singleActor));
            assertEquals(DispatchProposalScheduler.RejectionReason.GRID_LIMIT, actorLimit.reason());
            actorOwner.ticket().close();

            ProviderCapacitySnapshot sharedProvider = new ProviderCapacitySnapshot(
                    new CraftingProviderId(6L, 100L),
                    new CraftingDispatchTarget("governed-provider"),
                    Optional.empty(),
                    "pattern",
                    1L,
                    1L,
                    0L,
                    ProviderRoutingMode.TARGETED,
                    new DispatchCapacity.Known(8L),
                    new DispatchCapacity.Known(8L));
            DispatchProposalPolicy singleProviderProposal = new DispatchProposalPolicy(4, 1, 8, true);
            DispatchProposalScheduler.Accepted providerOwner = submitAndAwait(
                    scheduler,
                    request(6L, runtimeId, 3, 0L, 6L, sharedProvider),
                    singleProviderProposal);
            DispatchProposalTicket.Ready fullLogicalOffer = assertInstanceOf(
                    DispatchProposalTicket.Ready.class,
                    providerOwner.ticket().state());
            assertEquals(6L, fullLogicalOffer.proposal().logicalCrafts());

            DispatchProposalScheduler.Accepted providerDeferred = submitAndAwait(
                    scheduler,
                    request(6L, runtimeId, 4, 0L, 2L, sharedProvider),
                    singleProviderProposal);
            assertInstanceOf(DispatchProposalTicket.NoCapacity.class, providerDeferred.ticket().state());
            providerOwner.ticket().close();
            providerDeferred.ticket().close();
        } finally {
            scheduler.close();
        }
    }

    @Test
    void coordinatorDiscardsACompletedProposalWhenAnyGenerationLeaseChanges() throws InterruptedException {
        DispatchProposalScheduler scheduler = createScheduler(new DispatchProposalLimits(1, 4, 4, 16));
        try {
            UUID runtimeId = UUID.randomUUID();
            TrinityWorkerProposalCoordinator coordinator = TrinityWorkerProposalCoordinator.create(() -> scheduler);
            CraftingDispatchProposalRequest request = request(3L, runtimeId, 1, 0L, 2L);
            Object workIdentity = new Object();
            CountDownLatch completed = new CountDownLatch(1);
            assertInstanceOf(
                    TrinityWorkerProposalCoordinator.Pending.class,
                    coordinator.submit(request, workIdentity, completed::countDown));
            assertTrue(completed.await(5L, TimeUnit.SECONDS));

            CraftingDispatchLease changedRoute = new CraftingDispatchLease(
                    request.lease().gridGeneration(),
                    runtimeId,
                    request.lease().runtimeGeneration(),
                    request.lease().workerNumber(),
                    request.lease().jobId(),
                    request.lease().jobRevision(),
                    request.lease().workGeneration(),
                    request.lease().routeLeaseEpoch(),
                    Math.incrementExact(request.lease().membershipGeneration()));
            assertInstanceOf(
                    TrinityWorkerProposalCoordinator.Empty.class,
                    coordinator.poll(changedRoute, workIdentity));
            assertInstanceOf(
                    TrinityWorkerProposalCoordinator.Pending.class,
                    coordinator.submit(request, workIdentity, () -> {}));
            coordinator.cancel();
        } finally {
            scheduler.close();
        }
    }

    @Test
    void busyCandidateFallsThroughWithOneImmutablePlannerPass() {
        AtomicInteger plannerCalls = new AtomicInteger();
        DispatchProposalCandidatePlanner delegate = createCandidatePlanner();
        DispatchProposalCandidatePlanner countingPlanner = request -> {
            plannerCalls.incrementAndGet();
            return delegate.plan(request);
        };
        ProviderShardDispatcher dispatcher = ProviderShardDispatcher.create(4);
        UUID runtimeId = UUID.randomUUID();
        MachineTargetId machine = MachineTargetId.forBlockTarget(
                Level.OVERWORLD,
                new BlockPos(7, 8, 9),
                Direction.SOUTH);
        ProviderCapacitySnapshot busy = new ProviderCapacitySnapshot(
                new CraftingProviderId(7L, 100L),
                new CraftingDispatchTarget("busy-route"),
                Optional.of(machine),
                "pattern",
                1L,
                1L,
                0L,
                ProviderRoutingMode.TARGETED,
                new DispatchCapacity.Known(8L),
                new DispatchCapacity.Known(8L));
        ProviderCapacitySnapshot available = new ProviderCapacitySnapshot(
                new CraftingProviderId(7L, 101L),
                new CraftingDispatchTarget("available-route"),
                Optional.empty(),
                "pattern",
                1L,
                1L,
                0L,
                ProviderRoutingMode.TARGETED,
                new DispatchCapacity.Known(8L),
                new DispatchCapacity.Known(8L));

        ProviderShardDispatcher.Reserved holder = assertInstanceOf(
                ProviderShardDispatcher.Reserved.class,
                dispatcher.selectAndReserve(
                        request(7L, runtimeId, 1, 0L, 1L, busy),
                        countingPlanner,
                        8));
        plannerCalls.set(0);
        ProviderShardDispatcher.Reserved selected = assertInstanceOf(
                ProviderShardDispatcher.Reserved.class,
                dispatcher.selectAndReserve(
                        request(7L, runtimeId, 2, 0L, 6L, List.of(busy, available)),
                        countingPlanner,
                        8));
        try {
            assertEquals(1, plannerCalls.get());
            assertEquals(available, selected.target());
            assertEquals(6L, selected.logicalCrafts());
            assertEquals(new CraftingDispatchCursor(0, 1L), selected.nextCursor());
        } finally {
            selected.reservation().close();
            holder.reservation().close();
        }
    }

    @Test
    void providerShardsDoNotOversellProviderRoutesOrSharedMachines() throws InterruptedException {
        DispatchProposalScheduler scheduler = createScheduler(new DispatchProposalLimits(2, 4, 4, 16));
        try {
            UUID runtimeId = UUID.randomUUID();
            MachineTargetId firstMachine = MachineTargetId.forBlockTarget(
                    Level.OVERWORLD,
                    new BlockPos(4, 5, 6),
                    Direction.NORTH);
            MachineTargetId secondMachine = MachineTargetId.forBlockTarget(
                    Level.OVERWORLD,
                    new BlockPos(4, 5, 6),
                    Direction.NORTH);
            CraftingDispatchProposalRequest firstRequest = request(
                    4L,
                    runtimeId,
                    1,
                    0L,
                    4L,
                    Optional.of(firstMachine));
            CraftingDispatchProposalRequest secondRequest = request(
                    4L,
                    runtimeId,
                    2,
                    0L,
                    4L,
                    Optional.of(secondMachine));

            CountDownLatch firstCompleted = new CountDownLatch(1);
            DispatchProposalScheduler.Accepted first = assertInstanceOf(
                    DispatchProposalScheduler.Accepted.class,
                    scheduler.submit(firstRequest, firstCompleted::countDown));
            CountDownLatch secondCompleted = new CountDownLatch(1);
            DispatchProposalScheduler.Accepted second = assertInstanceOf(
                    DispatchProposalScheduler.Accepted.class,
                    scheduler.submit(secondRequest, secondCompleted::countDown));
            assertTrue(firstCompleted.await(5L, TimeUnit.SECONDS));
            assertTrue(secondCompleted.await(5L, TimeUnit.SECONDS));
            boolean firstReserved = first.ticket().state() instanceof DispatchProposalTicket.Ready;
            boolean secondReserved = second.ticket().state() instanceof DispatchProposalTicket.Ready;
            assertTrue(firstReserved ^ secondReserved);

            first.ticket().close();
            second.ticket().close();
            CountDownLatch releasedCompleted = new CountDownLatch(1);
            DispatchProposalScheduler.Accepted released = assertInstanceOf(
                    DispatchProposalScheduler.Accepted.class,
                    scheduler.submit(secondRequest, releasedCompleted::countDown));
            assertTrue(releasedCompleted.await(5L, TimeUnit.SECONDS));
            assertInstanceOf(DispatchProposalTicket.Ready.class, released.ticket().state());
            released.ticket().close();

            ProviderCapacitySnapshot sharedProvider = new ProviderCapacitySnapshot(
                    new CraftingProviderId(4L, 100L),
                    new CraftingDispatchTarget("shared-route"),
                    Optional.empty(),
                    "pattern",
                    1L,
                    1L,
                    0L,
                    ProviderRoutingMode.TARGETED,
                    new DispatchCapacity.Known(4L),
                    new DispatchCapacity.Known(4L));
            CountDownLatch routeFirstCompleted = new CountDownLatch(1);
            DispatchProposalScheduler.Accepted routeFirst = assertInstanceOf(
                    DispatchProposalScheduler.Accepted.class,
                    scheduler.submit(
                            request(4L, runtimeId, 3, 0L, 3L, sharedProvider),
                            routeFirstCompleted::countDown));
            CountDownLatch routeSecondCompleted = new CountDownLatch(1);
            DispatchProposalScheduler.Accepted routeSecond = assertInstanceOf(
                    DispatchProposalScheduler.Accepted.class,
                    scheduler.submit(
                            request(4L, runtimeId, 4, 0L, 3L, sharedProvider),
                            routeSecondCompleted::countDown));
            assertTrue(routeFirstCompleted.await(5L, TimeUnit.SECONDS));
            assertTrue(routeSecondCompleted.await(5L, TimeUnit.SECONDS));
            DispatchProposalTicket.Ready routeFirstReady = assertInstanceOf(
                    DispatchProposalTicket.Ready.class,
                    routeFirst.ticket().state());
            DispatchProposalTicket.Ready routeSecondReady = assertInstanceOf(
                    DispatchProposalTicket.Ready.class,
                    routeSecond.ticket().state());
            assertEquals(
                    4L,
                    Math.addExact(
                            routeFirstReady.proposal().logicalCrafts(),
                            routeSecondReady.proposal().logicalCrafts()));

            DispatchProposalScheduler.Accepted routeFull = submitAndAwait(
                    scheduler,
                    request(4L, runtimeId, 5, 0L, 1L, sharedProvider));
            assertInstanceOf(DispatchProposalTicket.NoCapacity.class, routeFull.ticket().state());
            routeFirst.ticket().close();
            routeSecond.ticket().close();
            routeFull.ticket().close();
        } finally {
            scheduler.close();
        }
    }

    private static CraftingDispatchProposalRequest request(long scope,
                                                           UUID runtimeId,
                                                           int workerNumber,
                                                           long membershipGeneration,
                                                           long remainingCrafts) {
        return request(scope, runtimeId, workerNumber, membershipGeneration, remainingCrafts, Optional.empty());
    }

    private static CraftingDispatchProposalRequest request(long scope,
                                                           UUID runtimeId,
                                                           int workerNumber,
                                                           long membershipGeneration,
                                                           long remainingCrafts,
                                                           Optional<MachineTargetId> machineTarget) {
        ProviderCapacitySnapshot target = new ProviderCapacitySnapshot(
                new CraftingProviderId(scope, workerNumber),
                new CraftingDispatchTarget("target-" + workerNumber),
                machineTarget,
                "pattern",
                1L,
                1L,
                0L,
                ProviderRoutingMode.TARGETED,
                new DispatchCapacity.Known(8L),
                new DispatchCapacity.Known(8L));
        return request(scope, runtimeId, workerNumber, membershipGeneration, remainingCrafts, target);
    }

    private static CraftingDispatchProposalRequest request(long scope,
                                                           UUID runtimeId,
                                                           int workerNumber,
                                                           long membershipGeneration,
                                                           long remainingCrafts,
                                                           ProviderCapacitySnapshot target) {
        return request(
                scope,
                runtimeId,
                workerNumber,
                membershipGeneration,
                remainingCrafts,
                List.of(target));
    }

    private static CraftingDispatchProposalRequest request(long scope,
                                                           UUID runtimeId,
                                                           int workerNumber,
                                                           long membershipGeneration,
                                                           long remainingCrafts,
                                                           List<ProviderCapacitySnapshot> targets) {
        CraftingDispatchLease lease = new CraftingDispatchLease(
                scope,
                runtimeId,
                1L,
                workerNumber,
                UUID.randomUUID(),
                1L,
                1L,
                1L,
                membershipGeneration);
        return new CraftingDispatchProposalRequest(
                lease,
                new ProviderCapacityCapture(
                        new ProviderCapacityCaptureKey(
                                scope,
                                1L,
                                1L,
                                0L,
                                targets.stream()
                                        .map(ProviderCapacitySnapshot::providerId)
                                        .distinct()
                                        .toList(),
                                "pattern",
                                List.of(),
                                remainingCrafts),
                        targets),
                BigInteger.valueOf(remainingCrafts),
                CraftingDispatchCursor.initial());
    }

    private DispatchProposalScheduler createScheduler(DispatchProposalLimits limits) {
        TrinityComputationCache cache = createComputationCache();
        return DispatchProposalScheduler.create(limits, () -> cache);
    }

    private DispatchProposalCandidatePlanner createCandidatePlanner() {
        TrinityComputationCache cache = createComputationCache();
        return DispatchProposalCandidatePlanner.create(() -> cache);
    }

    private TrinityComputationCache createComputationCache() {
        TrinityComputationCache cache = TrinityComputationCache.create(Runnable::run);
        this.computationCaches.add(cache);
        return cache;
    }

    private static DispatchProposalScheduler.Accepted submitAndAwait(
                                                                     DispatchProposalScheduler scheduler,
                                                                     CraftingDispatchProposalRequest request)
                                                                                                              throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        DispatchProposalScheduler.Accepted accepted = assertInstanceOf(
                DispatchProposalScheduler.Accepted.class,
                scheduler.submit(request, completed::countDown));
        assertTrue(completed.await(5L, TimeUnit.SECONDS));
        return accepted;
    }

    private static DispatchProposalScheduler.Accepted submitAndAwait(
                                                                     DispatchProposalScheduler scheduler,
                                                                     CraftingDispatchProposalRequest request,
                                                                     DispatchProposalPolicy policy)
                                                                                                    throws InterruptedException {
        CountDownLatch completed = new CountDownLatch(1);
        DispatchProposalScheduler.Accepted accepted = assertInstanceOf(
                DispatchProposalScheduler.Accepted.class,
                scheduler.submit(request, completed::countDown, policy));
        assertTrue(completed.await(5L, TimeUnit.SECONDS));
        return accepted;
    }
}

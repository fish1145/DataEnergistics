package com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.schedule;

import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchLease;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.model.CraftingDispatchProposalRequest;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.async.runtime.TrinityWorkerProposalCoordinator;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.capacity.CapacitySlicePlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingDispatchTarget;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.CraftingProviderId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.DispatchCapacity;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.MachineTargetId;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderCapacitySnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.dispatch.model.ProviderRoutingMode;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityDispatchRuntimeTest {

    @Test
    void proposalLeaseSelectsOneImmutableTargetAndRejectsASecondWorkerTicket() throws InterruptedException {
        DispatchProposalScheduler scheduler = DispatchProposalScheduler.create(new DispatchProposalLimits(1, 4, 4, 16));
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
            assertEquals(request.candidates().getFirst(), ready.proposal().target());
            assertEquals(6L, ready.proposal().logicalCrafts());

            accepted.ticket().close();
            DispatchProposalScheduler.Accepted retried = assertInstanceOf(
                    DispatchProposalScheduler.Accepted.class,
                    scheduler.submit(request, () -> {}));
            retried.ticket().close();
        } finally {
            scheduler.close();
        }
    }

    @Test
    void queueRejectionReleasesWorkerAdmissionWithoutRunningResourceLogic() throws InterruptedException {
        CountDownLatch running = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CapacitySlicePlanner delegate = CapacitySlicePlanner.create();
        CapacitySlicePlanner blockingPlanner = (snapshots, remainingCrafts, physicalCallLimit, cursor) -> {
            running.countDown();
            try {
                if (!release.await(5L, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out while holding the dispatch proposal worker");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Dispatch proposal worker was interrupted", exception);
            }
            return delegate.plan(snapshots, remainingCrafts, physicalCallLimit, cursor);
        };
        DispatchProposalSchedulerImpl scheduler = new DispatchProposalSchedulerImpl(
                new DispatchProposalLimits(1, 1, 4, 16),
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
            TrinityWorkerProposalCoordinator deferredWorker =
                    TrinityWorkerProposalCoordinator.create(() -> scheduler);
            CraftingDispatchProposalRequest deferredRequest = request(2L, runtimeId, 3, 0L, 1L);
            TrinityWorkerProposalCoordinator.Deferred full = assertInstanceOf(
                    TrinityWorkerProposalCoordinator.Deferred.class,
                    deferredWorker.submit(deferredRequest, deferredRequest, () -> {}));
            assertEquals(TrinityWorkerProposalCoordinator.DeferredReason.QUEUE_FULL, full.reason());

            release.countDown();
            first.ticket().close();
            second.ticket().close();
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
    void coordinatorDiscardsACompletedProposalWhenAnyGenerationLeaseChanges() throws InterruptedException {
        DispatchProposalScheduler scheduler = DispatchProposalScheduler.create(new DispatchProposalLimits(1, 4, 4, 16));
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
    void providerShardsDoNotOversellProviderRoutesOrSharedMachines() throws InterruptedException {
        DispatchProposalScheduler scheduler = DispatchProposalScheduler.create(new DispatchProposalLimits(2, 4, 4, 16));
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
            assertTrue(firstCompleted.await(5L, TimeUnit.SECONDS));
            assertInstanceOf(DispatchProposalTicket.Ready.class, first.ticket().state());

            CountDownLatch secondCompleted = new CountDownLatch(1);
            DispatchProposalScheduler.Accepted second = assertInstanceOf(
                    DispatchProposalScheduler.Accepted.class,
                    scheduler.submit(secondRequest, secondCompleted::countDown));
            assertTrue(secondCompleted.await(5L, TimeUnit.SECONDS));
            assertInstanceOf(DispatchProposalTicket.NoCapacity.class, second.ticket().state());

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
            DispatchProposalScheduler.Accepted routeFirst = submitAndAwait(
                    scheduler,
                    request(4L, runtimeId, 3, 0L, 3L, sharedProvider));
            DispatchProposalTicket.Ready routeFirstReady = assertInstanceOf(
                    DispatchProposalTicket.Ready.class,
                    routeFirst.ticket().state());
            assertEquals(3L, routeFirstReady.proposal().logicalCrafts());

            DispatchProposalScheduler.Accepted routeSecond = submitAndAwait(
                    scheduler,
                    request(4L, runtimeId, 4, 0L, 3L, sharedProvider));
            DispatchProposalTicket.Ready routeSecondReady = assertInstanceOf(
                    DispatchProposalTicket.Ready.class,
                    routeSecond.ticket().state());
            assertEquals(1L, routeSecondReady.proposal().logicalCrafts());

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
                List.of(target),
                BigInteger.valueOf(remainingCrafts),
                0);
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
}

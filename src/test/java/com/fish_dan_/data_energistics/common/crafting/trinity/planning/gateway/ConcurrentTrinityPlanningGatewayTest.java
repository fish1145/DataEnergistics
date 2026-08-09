package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.accessor.CraftingPlanTiming;
import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;

import net.minecraft.network.chat.Component;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class ConcurrentTrinityPlanningGatewayTest {

    private static final long AE2_CALCULATION_NANOS = 12_345_678L;
    private static final AEKey TARGET = DataKey.of();
    private static final AEKey INPUT = DataFlowKey.of();

    private ExecutorService executor;

    @AfterEach
    void stopExecutor() {
        if (this.executor != null) {
            this.executor.shutdownNow();
        }
    }

    @Test
    void startsBothTracksAndPrefersValidTrinityPlan() throws Exception {
        this.executor = Executors.newSingleThreadExecutor();
        TrinityPlanningGateway gateway = new ConcurrentTrinityPlanningGateway(this.executor, false);
        TrinityCraftingPlan trinityPlan = trinityPlan();
        CompletableFuture<ICraftingPlan> ae2 = new CompletableFuture<>();
        AtomicBoolean ae2Started = new AtomicBoolean();

        Future<ICraftingPlan> selected = gateway.begin(
                true,
                1L,
                1L,
                () -> TrinityPlanningAttempt.success(trinityPlan),
                () -> {
                    ae2Started.set(true);
                    return ae2;
                });

        assertTrue(ae2Started.get());
        assertSame(trinityPlan, selected.get(1L, TimeUnit.SECONDS));
        assertTrue(ae2.isCancelled());
    }

    @Test
    void skipsTrinityTrackWhenNoQualifiedCpuExists() throws Exception {
        this.executor = Executors.newSingleThreadExecutor();
        TrinityPlanningGateway gateway = new ConcurrentTrinityPlanningGateway(this.executor, false);
        AtomicBoolean trinityStarted = new AtomicBoolean();
        ICraftingPlan ae2 = ae2Plan(false);

        ICraftingPlan selected = gateway.begin(
                false,
                1L,
                1L,
                () -> {
                    trinityStarted.set(true);
                    return TrinityPlanningAttempt.success(trinityPlan());
                },
                () -> CompletableFuture.completedFuture(ae2)).get();

        assertSame(ae2, selected);
        assertFalse(trinityStarted.get());
    }

    @Test
    void retainsAe2SimulationAndAddsTrinityDiagnostic() throws Exception {
        this.executor = Executors.newSingleThreadExecutor();
        TrinityPlanningGateway gateway = new ConcurrentTrinityPlanningGateway(this.executor, false);
        ICraftingPlan simulation = ae2Plan(true);
        TrinityPlanningDiagnostic diagnostic = TrinityPlanningDiagnostic.ofLiteral(
                TrinityPlanningDiagnosticCode.NO_PRODUCTIVE_CYCLE,
                "No productive cycle reaches the target");

        ICraftingPlan selected = gateway.begin(
                true,
                1L,
                1L,
                () -> TrinityPlanningAttempt.failure(diagnostic),
                () -> CompletableFuture.completedFuture(simulation)).get();

        TrinityDiagnosedCraftingPlan diagnosed = assertInstanceOf(TrinityDiagnosedCraftingPlan.class, selected);
        assertSame(simulation, diagnosed.delegate());
        assertSame(simulation.missingItems(), diagnosed.missingItems());
        assertEquals(AE2_CALCULATION_NANOS, diagnosed.calculationNanos());
        assertEquals(TrinityPlanningDiagnosticCode.NO_PRODUCTIVE_CYCLE, diagnosed.diagnostic().code());
    }

    @Test
    void publishesExactTrinityShortageWithoutWaitingForAe2() throws Exception {
        ManualExecutor manualExecutor = new ManualExecutor();
        this.executor = manualExecutor;
        TrinityPlanningGateway gateway = new ConcurrentTrinityPlanningGateway(manualExecutor, false);
        TrinityPlanningDiagnostic diagnostic = new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                Component.literal("material shortage"),
                Map.of(),
                new TrinityPlanningDiagnostic.InputShortage(
                        INPUT,
                        BigInteger.valueOf(8_792_000_000L),
                        BigInteger.valueOf(2_147_483_821L),
                        BigInteger.valueOf(6_644_516_179L)));
        TrinityDiagnosedCraftingPlan trinitySimulation = TrinityDiagnosedCraftingPlan.forInputShortage(
                new GenericStack(TARGET, 1_256_000_000L),
                diagnostic);
        CompletableFuture<ICraftingPlan> ae2 = new CompletableFuture<>();

        Future<ICraftingPlan> selected = gateway.begin(
                true,
                1L,
                1L,
                () -> TrinityPlanningAttempt.authoritativeSimulation(trinitySimulation),
                () -> ae2);
        manualExecutor.runNext();

        assertTrue(selected.isDone());
        assertSame(trinitySimulation, selected.get());
        assertTrue(ae2.isCancelled());
    }

    @Test
    void unsupportedTrinityResultStillWaitsForAe2() throws Exception {
        ManualExecutor manualExecutor = new ManualExecutor();
        this.executor = manualExecutor;
        TrinityPlanningGateway gateway = new ConcurrentTrinityPlanningGateway(manualExecutor, false);
        TrinityPlanningDiagnostic diagnostic = TrinityPlanningDiagnostic.ofLiteral(
                TrinityPlanningDiagnosticCode.NO_PRODUCTIVE_CYCLE,
                "unsupported by Trinity");
        CompletableFuture<ICraftingPlan> ae2 = new CompletableFuture<>();
        Future<ICraftingPlan> selected = gateway.begin(
                true,
                1L,
                1L,
                () -> TrinityPlanningAttempt.failure(diagnostic),
                () -> ae2);
        manualExecutor.runNext();

        assertFalse(selected.isDone());
        assertFalse(ae2.isCancelled());
        ICraftingPlan ae2Plan = ae2Plan(false);
        ae2.complete(ae2Plan);
        assertSame(ae2Plan, selected.get());
    }

    @Test
    void callerTimeoutDoesNotPrematurelyAdoptAe2Result() {
        this.executor = Executors.newSingleThreadExecutor();
        TrinityPlanningGateway gateway = new ConcurrentTrinityPlanningGateway(this.executor, false);
        CountDownLatch release = new CountDownLatch(1);
        Future<ICraftingPlan> selected = gateway.begin(
                true,
                1L,
                1L,
                () -> {
                    release.await();
                    return TrinityPlanningAttempt.success(trinityPlan());
                },
                () -> CompletableFuture.completedFuture(ae2Plan(false)));

        assertThrows(TimeoutException.class, () -> selected.get(5L, TimeUnit.MILLISECONDS));
        assertTrue(selected.cancel(true));
    }

    @Test
    void cancellationCancelsAe2CallerWithoutInterruptingSharedTrinityWork() throws Exception {
        this.executor = Executors.newSingleThreadExecutor();
        TrinityPlanningGateway gateway = new ConcurrentTrinityPlanningGateway(this.executor, false);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        CompletableFuture<ICraftingPlan> ae2 = new CompletableFuture<>();
        Future<ICraftingPlan> selected = gateway.begin(
                true,
                1L,
                1L,
                () -> {
                    entered.countDown();
                    try {
                        release.await();
                        return TrinityPlanningAttempt.success(trinityPlan());
                    } catch (InterruptedException exception) {
                        interrupted.set(true);
                        throw exception;
                    } finally {
                        finished.countDown();
                    }
                },
                () -> ae2);

        assertTrue(entered.await(1L, TimeUnit.SECONDS));
        assertTrue(selected.cancel(true));
        assertTrue(selected.isCancelled());
        assertTrue(ae2.isCancelled());
        release.countDown();
        assertTrue(finished.await(1L, TimeUnit.SECONDS));
        assertFalse(interrupted.get());
    }

    @Test
    void queueRejectionProducesSimulationDiagnosticInsteadOfDroppingAe2() throws Exception {
        ExecutorService rejecting = new RejectingExecutor();
        TrinityPlanningGateway gateway = new ConcurrentTrinityPlanningGateway(rejecting, false);

        ICraftingPlan selected = gateway.begin(
                true,
                1L,
                1L,
                () -> TrinityPlanningAttempt.success(trinityPlan()),
                () -> CompletableFuture.completedFuture(ae2Plan(true))).get();

        TrinityDiagnosedCraftingPlan diagnosed = assertInstanceOf(TrinityDiagnosedCraftingPlan.class, selected);
        assertEquals(TrinityPlanningDiagnosticCode.PLANNER_QUEUE_FULL, diagnosed.diagnostic().code());
    }

    @Test
    void submitsTrinityOnlyContinuationThroughSharedBoundedExecutor() throws Exception {
        this.executor = Executors.newSingleThreadExecutor();
        TrinityPlanningGateway gateway = new ConcurrentTrinityPlanningGateway(this.executor, false);
        TrinityCraftingPlan plan = trinityPlan();

        TrinityPlanningAttempt attempt = gateway.beginTrinity(1L, 1L,
                () -> TrinityPlanningAttempt.success(plan)).get(1L, TimeUnit.SECONDS);

        assertTrue(attempt.successful());
        assertSame(plan, attempt.plan());
    }

    @Test
    void reportsQueueFullForRejectedTrinityOnlyContinuation() throws Exception {
        TrinityPlanningGateway gateway = new ConcurrentTrinityPlanningGateway(new RejectingExecutor(), false);

        TrinityPlanningAttempt attempt = gateway.beginTrinity(1L, 1L,
                () -> TrinityPlanningAttempt.success(trinityPlan())).get();

        assertFalse(attempt.successful());
        assertEquals(TrinityPlanningDiagnosticCode.PLANNER_QUEUE_FULL, attempt.diagnostic().code());
    }

    @Test
    void diagnosedPlanRejectsExecutableAe2Delegate() {
        TrinityPlanningDiagnostic diagnostic = TrinityPlanningDiagnostic.ofLiteral(
                TrinityPlanningDiagnosticCode.INTERNAL_ERROR,
                "diagnostic");
        assertThrows(IllegalArgumentException.class,
                () -> new TrinityDiagnosedCraftingPlan(ae2Plan(false), diagnostic));
    }

    private static TrinityCraftingPlan trinityPlan() {
        TrinityPatternIdentity pattern = new TrinityPatternIdentity("definition", "publication");
        TrinityPlanStage stage = new TrinityPlanStage(
                0,
                false,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(pattern, TARGET, 0, BigInteger.ONE)),
                Map.of(INPUT, BigInteger.ONE),
                Map.of(INPUT, BigInteger.ONE.negate(), TARGET, BigInteger.ONE));
        return TrinityCraftingPlan.builder()
                .finalOutput(new GenericStack(TARGET, 1L))
                .bytes(1L)
                .catalogRevision(1L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(INPUT, BigInteger.ONE))
                .patternFirings(Map.of(pattern, BigInteger.ONE))
                .stages(List.of(stage))
                .stageOrder(List.of(0))
                .cycleRepeatBlocks(List.of())
                .minimumSeed(Map.of())
                .targetNetChange(Map.of(INPUT, BigInteger.ONE.negate(), TARGET, BigInteger.ONE))
                .build();
    }

    private static ICraftingPlan ae2Plan(boolean simulation) {
        KeyCounter missing = new KeyCounter();
        if (simulation) {
            missing.add(INPUT, 3L);
        }
        return new TimedCraftingPlan(
                new GenericStack(TARGET, 1L),
                1L,
                simulation,
                false,
                new KeyCounter(),
                new KeyCounter(),
                missing,
                Map.of(),
                AE2_CALCULATION_NANOS);
    }

    private record TimedCraftingPlan(
                                     GenericStack finalOutput,
                                     long bytes,
                                     boolean simulation,
                                     boolean multiplePaths,
                                     KeyCounter usedItems,
                                     KeyCounter emittedItems,
                                     KeyCounter missingItems,
                                     Map<IPatternDetails, Long> patternTimes,
                                     long calculationNanos)
            implements ICraftingPlan, CraftingPlanTiming {

        @Override
        public long dataEnergistics$calculationNanos() {
            return this.calculationNanos;
        }
    }

    private static final class RejectingExecutor extends AbstractExecutorService {

        private boolean shutdown;

        @Override
        public void shutdown() {
            this.shutdown = true;
        }

        @Override
        public @NotNull List<Runnable> shutdownNow() {
            this.shutdown = true;
            return List.of();
        }

        @Override
        public boolean isShutdown() {
            return this.shutdown;
        }

        @Override
        public boolean isTerminated() {
            return this.shutdown;
        }

        @Override
        public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) {
            return this.shutdown;
        }

        @Override
        public void execute(@NotNull Runnable command) {
            throw new RejectedExecutionException("full");
        }
    }

    private static final class ManualExecutor extends AbstractExecutorService {

        private final ArrayDeque<Runnable> commands = new ArrayDeque<>();
        private boolean shutdown;

        void runNext() {
            Runnable command = this.commands.pollFirst();
            if (command == null) {
                throw new IllegalStateException("No queued Trinity planning command");
            }
            command.run();
        }

        @Override
        public void shutdown() {
            this.shutdown = true;
        }

        @Override
        public @NotNull List<Runnable> shutdownNow() {
            this.shutdown = true;
            List<Runnable> remaining = List.copyOf(this.commands);
            this.commands.clear();
            return remaining;
        }

        @Override
        public boolean isShutdown() {
            return this.shutdown;
        }

        @Override
        public boolean isTerminated() {
            return this.shutdown && this.commands.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, @NotNull TimeUnit unit) {
            return isTerminated();
        }

        @Override
        public void execute(@NotNull Runnable command) {
            if (this.shutdown) {
                throw new RejectedExecutionException("shutdown");
            }
            this.commands.addLast(command);
        }
    }
}

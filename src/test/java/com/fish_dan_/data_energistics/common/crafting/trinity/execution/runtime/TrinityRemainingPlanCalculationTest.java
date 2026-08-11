package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningComputationResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache.TrinityPlanningInput;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGateway;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.configuration.snapshot.TrinityCraftingSettings;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.material.Fluids;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEFluidKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TrinityRemainingPlanCalculationTest {

    private static AEKey target;
    private static AEKey input;

    @BeforeAll
    static void initialize() {
        TrinityPlanningGraphTestBootstrap.initialize();
        target = AEFluidKey.of(Fluids.WATER);
        input = AEFluidKey.of(Fluids.LAVA);
    }

    @Test
    void capturesOneRevisionAndPublishesItsCompletedPlan() {
        RecordingGateway gateway = new RecordingGateway();
        TrinityRemainingPlanCalculation calculation = TrinityRemainingPlanCalculation.create(() -> gateway);
        int[] captures = { 0 };

        TrinityRemainingPlanCalculation.Result waiting = calculation.advance(
                snapshot(7L),
                1L,
                () -> {
                    captures[0]++;
                    return Map.of(target, BigInteger.ONE);
                },
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingSettings.defaults(),
                0L);

        assertInstanceOf(TrinityRemainingPlanCalculation.Waiting.class, waiting);
        assertEquals(1, gateway.submissions);
        assertEquals(1, captures[0]);

        TrinityCraftingPlan replacement = plan(7L);
        gateway.pending.complete(TrinityPlanningAttempt.success(replacement));
        TrinityRemainingPlanCalculation.Ready ready = assertInstanceOf(
                TrinityRemainingPlanCalculation.Ready.class,
                calculation.advance(
                        snapshot(7L),
                        1L,
                        () -> {
                            captures[0]++;
                            return Map.of();
                        },
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        TrinityCraftingSettings.defaults(),
                        1L));

        assertEquals(replacement, ready.plan());
        assertEquals(7L, ready.revision());
        assertEquals(1, captures[0]);
    }

    @Test
    void rejectedRevisionWaitsUntilTheCatalogChanges() {
        RecordingGateway gateway = new RecordingGateway();
        TrinityRemainingPlanCalculation calculation = TrinityRemainingPlanCalculation.create(() -> gateway);
        Supplier<Map<AEKey, BigInteger>> available = Map::of;

        calculation.advance(
                snapshot(7L),
                1L,
                available,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingSettings.defaults(),
                0L);
        gateway.pending.complete(TrinityPlanningAttempt.failure(new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                Component.literal("no order"),
                Map.of())));
        assertInstanceOf(
                TrinityRemainingPlanCalculation.Rejected.class,
                calculation.advance(
                        snapshot(7L),
                        1L,
                        available,
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        TrinityCraftingSettings.defaults(),
                        1L));

        assertInstanceOf(
                TrinityRemainingPlanCalculation.Waiting.class,
                calculation.advance(
                        snapshot(7L),
                        1L,
                        available,
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        TrinityCraftingSettings.defaults(),
                        2L));
        assertEquals(1, gateway.submissions);

        calculation.advance(
                snapshot(8L),
                1L,
                available,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingSettings.defaults(),
                3L);
        assertEquals(2, gateway.submissions);
    }

    @Test
    void transientOutcomesRetryTheSameRevisionWithBackoff() {
        RecordingGateway gateway = new RecordingGateway();
        TrinityRemainingPlanCalculation calculation = TrinityRemainingPlanCalculation.create(() -> gateway);
        Supplier<Map<AEKey, BigInteger>> available = Map::of;

        calculation.advance(
                snapshot(7L),
                1L,
                available,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingSettings.defaults(),
                100L);
        gateway.pending.completeExceptionally(new IllegalStateException("transient planner failure"));
        TrinityRemainingPlanCalculation.Fault fault = assertInstanceOf(
                TrinityRemainingPlanCalculation.Fault.class,
                calculation.advance(
                        snapshot(7L),
                        1L,
                        available,
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        TrinityCraftingSettings.defaults(),
                        100L));

        assertEquals(7L, fault.revision());
        calculation.advance(
                snapshot(7L),
                1L,
                available,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingSettings.defaults(),
                100L);
        assertEquals(1, gateway.submissions);
        calculation.advance(
                snapshot(7L),
                1L,
                available,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingSettings.defaults(),
                101L);
        assertEquals(2, gateway.submissions);

        TrinityPlanningDiagnostic queueFull = new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.PLANNER_QUEUE_FULL,
                Component.literal("queue full"),
                Map.of());
        gateway.pending.complete(TrinityPlanningAttempt.failure(queueFull));
        TrinityRemainingPlanCalculation.Rejected rejected = assertInstanceOf(
                TrinityRemainingPlanCalculation.Rejected.class,
                calculation.advance(
                        snapshot(7L),
                        1L,
                        available,
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        TrinityCraftingSettings.defaults(),
                        101L));
        assertEquals(TrinityPlanningDiagnosticCode.PLANNER_QUEUE_FULL, rejected.diagnostic().code());

        calculation.advance(
                snapshot(7L),
                1L,
                available,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingSettings.defaults(),
                102L);
        assertEquals(2, gateway.submissions);
        calculation.advance(
                snapshot(7L),
                1L,
                available,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingSettings.defaults(),
                103L);
        assertEquals(3, gateway.submissions);

        gateway.pending.complete(TrinityPlanningAttempt.success(plan(7L)));
        TrinityRemainingPlanCalculation.Ready ready = assertInstanceOf(
                TrinityRemainingPlanCalculation.Ready.class,
                calculation.advance(
                        snapshot(7L),
                        1L,
                        available,
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        TrinityCraftingSettings.defaults(),
                        103L));
        calculation.retrySameRevision(ready.revision(), 103L, 200);

        calculation.advance(
                snapshot(7L),
                1L,
                available,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingSettings.defaults(),
                106L);
        assertEquals(3, gateway.submissions);
        calculation.advance(
                snapshot(7L),
                1L,
                available,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingSettings.defaults(),
                107L);
        assertEquals(4, gateway.submissions);

        gateway.pending.complete(TrinityPlanningAttempt.success(plan(7L)));
        TrinityRemainingPlanCalculation.Ready retried = assertInstanceOf(
                TrinityRemainingPlanCalculation.Ready.class,
                calculation.advance(
                        snapshot(7L),
                        1L,
                        available,
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        TrinityCraftingSettings.defaults(),
                        107L));
        calculation.acceptRevision(retried.revision());
        calculation.advance(
                snapshot(7L),
                1L,
                available,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingSettings.defaults(),
                108L);
        assertEquals(5, gateway.submissions);
    }

    @Test
    void cancelPropagatesToThePendingFuture() {
        RecordingGateway gateway = new RecordingGateway();
        TrinityRemainingPlanCalculation calculation = TrinityRemainingPlanCalculation.create(() -> gateway);

        calculation.advance(
                snapshot(7L),
                1L,
                Map::of,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingSettings.defaults(),
                0L);
        calculation.cancel();

        assertTrue(gateway.pending.isCancelled());
    }

    private static Optional<TrinityCraftingGraphSnapshot> snapshot(long revision) {
        return Optional.of(new TrinityCraftingGraphSnapshot(revision, List.of()));
    }

    private static TrinityCraftingPlan plan(long revision) {
        TrinityPatternIdentity identity = new TrinityPatternIdentity("definition", "publication");
        TrinityPlanStage stage = new TrinityPlanStage(
                0,
                false,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(identity, target, 0, BigInteger.ONE)),
                Map.of(input, BigInteger.ONE),
                Map.of(input, BigInteger.ONE.negate(), target, BigInteger.ONE));
        return TrinityCraftingPlan.builder()
                .finalOutput(new GenericStack(target, 1L))
                .bytes(1L)
                .catalogRevision(revision)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(input, BigInteger.ONE))
                .patternFirings(Map.of(identity, BigInteger.ONE))
                .stages(List.of(stage))
                .stageOrder(List.of(0))
                .cycleRepeatBlocks(List.of())
                .minimumSeed(Map.of())
                .targetNetChange(Map.of(input, BigInteger.ONE.negate(), target, BigInteger.ONE))
                .build();
    }

    private static final class RecordingGateway implements TrinityPlanningGateway {

        private CompletableFuture<TrinityPlanningAttempt> pending = new CompletableFuture<>();
        private int submissions;

        @Override
        public Future<ICraftingPlan> begin(boolean qualifiedTrinityCpu,
                                           long gridScope,
                                           long graphRevision,
                                           GenericStack requestedOutput,
                                           Callable<TrinityPlanningAttempt> trinityCalculation,
                                           Supplier<Future<ICraftingPlan>> ae2Calculation) {
            throw new UnsupportedOperationException("Parallel initial planning is outside this test");
        }

        @Override
        public Future<TrinityPlanningAttempt> beginTrinity(
                                                           long gridScope,
                                                           long graphRevision,
                                                           Callable<TrinityPlanningAttempt> trinityCalculation) {
            this.submissions++;
            if (this.pending.isDone()) {
                this.pending = new CompletableFuture<>();
            }
            return this.pending;
        }

        @Override
        public TrinityPlanningComputationResult calculateTrinity(TrinityPlanningInput input) {
            throw new UnsupportedOperationException("Recorded futures do not execute planning callbacks");
        }

        @Override
        public void clearGrid(long gridScope) {}

        @Override
        public void close() {}
    }
}

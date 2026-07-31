package com.fish_dan_.data_energistics.common.crafting.trinity.execution.runtime;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningAttempt;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway.TrinityPlanningGateway;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlanImpl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;

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
                () -> {
                    captures[0]++;
                    return Map.of(target, BigInteger.ONE);
                },
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingConfig.settings());

        assertInstanceOf(TrinityRemainingPlanCalculation.Waiting.class, waiting);
        assertEquals(1, gateway.submissions);
        assertEquals(1, captures[0]);

        TrinityCraftingPlan replacement = plan(7L);
        gateway.pending.complete(TrinityPlanningAttempt.success(replacement));
        TrinityRemainingPlanCalculation.Ready ready = assertInstanceOf(
                TrinityRemainingPlanCalculation.Ready.class,
                calculation.advance(
                        snapshot(7L),
                        () -> {
                            captures[0]++;
                            return Map.of();
                        },
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        TrinityCraftingConfig.settings()));

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
                available,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingConfig.settings());
        gateway.pending.complete(TrinityPlanningAttempt.failure(new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                Component.literal("no order"),
                Map.of())));
        assertInstanceOf(
                TrinityRemainingPlanCalculation.Rejected.class,
                calculation.advance(
                        snapshot(7L),
                        available,
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        TrinityCraftingConfig.settings()));

        assertInstanceOf(
                TrinityRemainingPlanCalculation.Waiting.class,
                calculation.advance(
                        snapshot(7L),
                        available,
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        TrinityCraftingConfig.settings()));
        assertEquals(1, gateway.submissions);

        calculation.advance(
                snapshot(8L),
                available,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingConfig.settings());
        assertEquals(2, gateway.submissions);
    }

    @Test
    void cancelPropagatesToThePendingFuture() {
        RecordingGateway gateway = new RecordingGateway();
        TrinityRemainingPlanCalculation calculation = TrinityRemainingPlanCalculation.create(() -> gateway);

        calculation.advance(
                snapshot(7L),
                Map::of,
                target,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                TrinityCraftingConfig.settings());
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
        return TrinityCraftingPlanImpl.builder()
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
                                           Callable<TrinityPlanningAttempt> trinityCalculation,
                                           Supplier<Future<ICraftingPlan>> ae2Calculation) {
            throw new UnsupportedOperationException("Parallel initial planning is outside this test");
        }

        @Override
        public Future<TrinityPlanningAttempt> beginTrinity(Callable<TrinityPlanningAttempt> trinityCalculation) {
            this.submissions++;
            if (this.pending.isDone()) {
                this.pending = new CompletableFuture<>();
            }
            return this.pending;
        }

        @Override
        public void close() {}
    }
}

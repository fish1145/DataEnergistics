package com.fish_dan_.data_energistics.common.crafting.trinity.planning.cache;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityCompiledGraph;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanningPipeline;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.trinity.pattern.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.configuration.api.DataEnergisticsSettings.TrinityCrafting;
import com.fish_dan_.data_energistics.configuration.snapshot.TrinityCraftingSettings;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TrinityPlanningComputationTest {

    private static final long TIMEOUT_SECONDS = 5L;
    private static AEKey inputKey;
    private static AEKey targetKey;
    private static AEKey unrelatedKey;

    private ExecutorService executor;
    private TrinityComputationCache cache;

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
        inputKey = AEItemKey.of(Items.REDSTONE);
        targetKey = AEItemKey.of(Items.DIAMOND);
        unrelatedKey = AEItemKey.of(Items.EMERALD);
    }

    @AfterEach
    void closeResources() {
        if (this.cache != null) {
            this.cache.close();
        }
        if (this.executor != null) {
            this.executor.shutdownNow();
        }
    }

    @Test
    void exactHitIgnoresUnrelatedInventoryButRelevantChangesResolveFromStructure() throws Exception {
        CountingPipeline pipeline = new CountingPipeline();
        TrinityPlanningComputation computation = computation(pipeline, 2);
        TrinityPlanningInput initial = input(1L, 1L, BigInteger.valueOf(4L), Map.of(inputKey, BigInteger.valueOf(4L)));

        TrinityPlanningComputationResult first = get(computation.begin(initial));
        TrinityPlanningComputationResult exact = get(computation.begin(initial));
        TrinityPlanningComputationResult unrelated = get(computation.begin(input(
                1L,
                1L,
                BigInteger.valueOf(4L),
                Map.of(inputKey, BigInteger.valueOf(4L), unrelatedKey, BigInteger.valueOf(999L)))));
        TrinityPlanningComputationResult relevant = get(computation.begin(input(
                1L,
                1L,
                BigInteger.valueOf(4L),
                Map.of(inputKey, BigInteger.valueOf(3L)))));
        TrinityPlanningComputationResult quantity = get(computation.begin(input(
                1L,
                1L,
                BigInteger.valueOf(5L),
                Map.of(inputKey, BigInteger.valueOf(4L)))));

        assertEquals(PlanningCachePath.MISS, first.cachePath());
        assertEquals(PlanningCachePath.EXACT_HIT, exact.cachePath());
        assertEquals(PlanningCachePath.EXACT_HIT, unrelated.cachePath());
        assertEquals(PlanningCachePath.STRUCTURE_HIT, relevant.cachePath());
        assertEquals(PlanningCachePath.STRUCTURE_HIT, quantity.cachePath());
        assertEquals(1, pipeline.compilations.get());
        assertEquals(3, pipeline.solves.get());
    }

    @Test
    void revisionChangeReusesSemanticStructureAndPublishesCurrentRevisionPlan() throws Exception {
        CountingPipeline pipeline = new CountingPipeline();
        pipeline.compileEntered = new CountDownLatch(1);
        pipeline.releaseCompile = new CountDownLatch(1);
        TrinityPlanningComputation computation = computation(pipeline, 2);

        Future<TrinityPlanningComputationResult> obsolete = computation.begin(input(
                7L,
                10L,
                BigInteger.ONE,
                Map.of(inputKey, BigInteger.ONE)));
        assertTrue(pipeline.compileEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Future<TrinityPlanningComputationResult> revisedFuture = computation.begin(input(
                7L,
                11L,
                BigInteger.ONE,
                Map.of(inputKey, BigInteger.ONE)));
        assertTrue(obsolete.isCancelled());
        pipeline.releaseCompile.countDown();

        TrinityPlanningComputationResult revised = get(revisedFuture);
        TrinityPlanningComputationResult nextRevision = get(computation.begin(input(
                7L,
                12L,
                BigInteger.ONE,
                Map.of(inputKey, BigInteger.ONE))));

        assertEquals(PlanningCachePath.STRUCTURE_HIT, revised.cachePath());
        assertEquals(PlanningCachePath.STRUCTURE_HIT, nextRevision.cachePath());
        assertTrue(revised.result().successful());
        assertEquals(11L, revised.result().value().catalogRevision());
        assertEquals(12L, nextRevision.result().value().catalogRevision());
        assertEquals(1, pipeline.compilations.get());
        assertEquals(2, pipeline.solves.get());
    }

    @Test
    void changedPatternSemanticsOrStructuralBoundsRequireCompilation() throws Exception {
        CountingPipeline pipeline = new CountingPipeline();
        TrinityPlanningComputation computation = computation(pipeline, 2);
        TrinityCraftingGraphSnapshot firstGraph = graph(1L, "first", Items.PAPER);
        TrinityCraftingGraphSnapshot changedGraph = graph(2L, "changed", Items.MAP);

        assertEquals(PlanningCachePath.MISS, get(computation.begin(input(firstGraph, settings(64)))).cachePath());
        assertEquals(PlanningCachePath.MISS, get(computation.begin(input(changedGraph, settings(64)))).cachePath());
        assertEquals(PlanningCachePath.MISS, get(computation.begin(input(changedGraph, settings(32)))).cachePath());

        assertEquals(3, pipeline.compilations.get());
        assertEquals(3, pipeline.solves.get());
    }

    @Test
    void concurrentIdenticalRequestsExecuteOneCompileAndOneSolve() throws Exception {
        CountingPipeline pipeline = new CountingPipeline();
        pipeline.compileEntered = new CountDownLatch(1);
        pipeline.releaseCompile = new CountDownLatch(1);
        TrinityPlanningComputation computation = computation(pipeline, 2);
        TrinityPlanningInput input = input(9L, 1L, BigInteger.ONE, Map.of(inputKey, BigInteger.ONE));

        Future<TrinityPlanningComputationResult> first = computation.begin(input);
        assertTrue(pipeline.compileEntered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        Future<TrinityPlanningComputationResult> second = computation.begin(input);
        pipeline.releaseCompile.countDown();

        TrinityPlanningComputationResult firstResult = get(first);
        TrinityPlanningComputationResult secondResult = get(second);
        assertTrue(firstResult.result().successful());
        assertTrue(secondResult.result().successful());
        assertEquals(1, pipeline.compilations.get());
        assertEquals(1, pipeline.solves.get());
        assertTrue(List.of(firstResult.cachePath(), secondResult.cachePath()).contains(PlanningCachePath.MISS));
    }

    @Test
    void deterministicRejectionIsCachedButTimeoutIsRecomputed() throws Exception {
        CountingPipeline deterministic = new CountingPipeline();
        deterministic.solveFailure = TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT;
        TrinityPlanningComputation computation = computation(deterministic, 1);
        TrinityPlanningInput input = input(1L, 1L, BigInteger.ONE, Map.of(inputKey, BigInteger.ONE));

        assertFalse(get(computation.begin(input)).result().successful());
        assertEquals(PlanningCachePath.EXACT_HIT, get(computation.begin(input)).cachePath());
        assertEquals(1, deterministic.solves.get());

        this.cache.close();
        this.executor.shutdownNow();
        this.cache = null;
        this.executor = null;

        CountingPipeline transientFailure = new CountingPipeline();
        transientFailure.solveFailure = TrinityPlanningDiagnosticCode.MIP_TIMEOUT;
        TrinityPlanningComputation transientComputation = computation(transientFailure, 1);
        assertFalse(get(transientComputation.begin(input)).result().successful());
        assertFalse(get(transientComputation.begin(input)).result().successful());
        assertEquals(2, transientFailure.solves.get());
    }

    private TrinityPlanningComputation computation(CountingPipeline pipeline, int threads) {
        this.executor = Executors.newFixedThreadPool(threads);
        this.cache = TrinityComputationCache.create(this.executor, 32);
        return TrinityPlanningComputation.create(this.cache, pipeline);
    }

    private static TrinityPlanningInput input(
                                              long gridScope,
                                              long revision,
                                              BigInteger amount,
                                              Map<AEKey, BigInteger> available) {
        return new TrinityPlanningInput(
                gridScope,
                graph(revision, "linear", Items.PAPER),
                targetKey,
                amount,
                CraftingQuantityMode.NET_NEW,
                available,
                settings(64));
    }

    private static TrinityPlanningInput input(
                                              TrinityCraftingGraphSnapshot graph,
                                              TrinityCrafting settings) {
        return new TrinityPlanningInput(
                3L,
                graph,
                targetKey,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(inputKey, BigInteger.ONE),
                settings);
    }

    private static TrinityCraftingGraphSnapshot graph(long revision, String identity, Item definition) {
        TrinityPatternPublicationSignature signature = new TrinityPatternPublicationSignature(
                AEItemKey.of(definition),
                List.of(new TrinityPatternPublicationSignature.Input(
                        1L,
                        List.of(new TrinityPatternPublicationSignature.Alternative(
                                new GenericStack(inputKey, 1L),
                                null)))),
                List.of(new GenericStack(targetKey, 1L)),
                false);
        return new TrinityCraftingGraphSnapshot(
                revision,
                List.of(new TrinityCraftingGraphPattern(
                        new TrinityPatternIdentity("definition-" + identity, "publication-" + identity),
                        signature)));
    }

    private static TrinityCrafting settings(int maxSccKeys) {
        return new TrinityCraftingSettings(
                maxSccKeys,
                32,
                500_000,
                4,
                2,
                128,
                200,
                CraftingQuantityMode.NET_NEW);
    }

    private static TrinityPlanningComputationResult get(Future<TrinityPlanningComputationResult> future)
                                                                                                         throws Exception {
        return future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static final class CountingPipeline implements TrinityGraphPlanningPipeline {

        private final TrinityGraphPlanningPipeline delegate = TrinityGraphPlanner.pipeline();
        private final AtomicInteger compilations = new AtomicInteger();
        private final AtomicInteger solves = new AtomicInteger();
        private CountDownLatch compileEntered;
        private CountDownLatch releaseCompile;
        private TrinityPlanningDiagnosticCode solveFailure;

        @Override
        public TrinityAlgorithmResult<TrinityCompiledGraph> compile(
                                                                    TrinityCraftingGraphSnapshot reachableSnapshot,
                                                                    AEKey target,
                                                                    int maxBindingVariants,
                                                                    int maxSccKeys,
                                                                    TrinityPlanningControl control) {
            this.compilations.incrementAndGet();
            if (this.compileEntered != null) {
                this.compileEntered.countDown();
                try {
                    assertTrue(this.releaseCompile.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return TrinityAlgorithmResult.failure(diagnostic(TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED));
                }
            }
            return this.delegate.compile(reachableSnapshot, target, maxBindingVariants, maxSccKeys, control);
        }

        @Override
        public TrinityAlgorithmResult<TrinityCraftingPlan> solve(
                                                                 TrinityCompiledGraph compiled,
                                                                 long catalogRevision,
                                                                 BigInteger requestedAmount,
                                                                 CraftingQuantityMode quantityMode,
                                                                 Map<AEKey, BigInteger> available,
                                                                 TrinityCrafting settings,
                                                                 TrinityPlanningControl control) {
            this.solves.incrementAndGet();
            return this.solveFailure == null ?
                    this.delegate.solve(
                            compiled,
                            catalogRevision,
                            requestedAmount,
                            quantityMode,
                            available,
                            settings,
                            control) :
                    TrinityAlgorithmResult.failure(diagnostic(this.solveFailure));
        }

        @Override
        public TrinityAlgorithmResult<TrinityCraftingPlan> plan(
                                                                TrinityCraftingGraphSnapshot snapshot,
                                                                AEKey target,
                                                                BigInteger requestedAmount,
                                                                CraftingQuantityMode quantityMode,
                                                                Map<AEKey, BigInteger> available,
                                                                TrinityCrafting settings,
                                                                TrinityPlanningControl control) {
            return this.delegate.plan(snapshot, target, requestedAmount, quantityMode, available, settings, control);
        }

        private static TrinityPlanningDiagnostic diagnostic(TrinityPlanningDiagnosticCode code) {
            return new TrinityPlanningDiagnostic(code, Component.literal(code.name()), Map.of());
        }
    }
}

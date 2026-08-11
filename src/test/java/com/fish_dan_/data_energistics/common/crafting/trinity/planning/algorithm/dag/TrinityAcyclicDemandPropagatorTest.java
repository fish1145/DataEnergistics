package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.dag;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.schedule.TrinityVariantFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityCraftingTopology;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityGraphTopologyAnalyzer;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;

import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmTestPatterns.amounts;
import static com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmTestPatterns.variant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityAcyclicDemandPropagatorTest {

    private static final int MAX_SEARCH_STATES = 1_000;

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void propagatesHugeDemandInGraphBoundedStates() {
        AEKey raw = AEItemKey.of(Items.COBBLESTONE);
        AEKey intermediate = AEItemKey.of(Items.IRON_INGOT);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityPatternVariant rawToIntermediate = variant(
                "raw-intermediate",
                amounts(raw, BigInteger.valueOf(2L)),
                amounts(intermediate, BigInteger.valueOf(3L)));
        TrinityPatternVariant intermediateToTarget = variant(
                "intermediate-target",
                amounts(intermediate, BigInteger.valueOf(5L)),
                amounts(target, BigInteger.valueOf(2L)));
        List<TrinityPatternVariant> variants = List.of(rawToIntermediate, intermediateToTarget);
        TrinityCraftingTopology topology = TrinityGraphTopologyAnalyzer.create()
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), variants, 8)
                .value();
        BigInteger request = BigInteger.TEN.pow(100);
        BigInteger availableRaw = request.multiply(BigInteger.TEN);

        TrinityAcyclicPlan plan = TrinityAcyclicDemandPropagator.create()
                .propagate(
                        topology,
                        variants,
                        target,
                        request,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(raw, availableRaw),
                        MAX_SEARCH_STATES,
                        control())
                .value();

        BigInteger targetFirings = request.divide(BigInteger.TWO);
        BigInteger intermediateFirings = targetFirings
                .multiply(BigInteger.valueOf(5L))
                .add(BigInteger.TWO)
                .divide(BigInteger.valueOf(3L));
        assertEquals(targetFirings, plan.firings().get(intermediateToTarget));
        assertEquals(intermediateFirings, plan.firings().get(rawToIntermediate));
        assertEquals(intermediateFirings.multiply(BigInteger.TWO), plan.externalInputs().get(raw));
        assertEquals(2, plan.statesVisited());
        assertEquals(List.of(rawToIntermediate, intermediateToTarget),
                plan.executionOrder().stream().map(TrinityVariantFiring::variant).toList());
    }

    @Test
    void stateCountDoesNotGrowWithRequestedAmount() {
        AEKey oak = AEItemKey.of(Items.OAK_PLANKS);
        AEKey crimson = AEItemKey.of(Items.CRIMSON_PLANKS);
        AEKey table = AEItemKey.of(Items.CRAFTING_TABLE);
        AEKey stick = AEItemKey.of(Items.STICK);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityPatternVariant oakTable = variant(
                "a-oak-table",
                amounts(oak, BigInteger.valueOf(4L)),
                amounts(table, BigInteger.ONE, stick, BigInteger.ONE));
        TrinityPatternVariant crimsonTable = variant(
                "b-crimson-table",
                amounts(crimson, BigInteger.valueOf(4L)),
                amounts(table, BigInteger.ONE, stick, BigInteger.ONE));
        TrinityPatternVariant finish = variant(
                "c-finish",
                amounts(table, BigInteger.TWO, stick, BigInteger.ONE),
                amounts(target, BigInteger.ONE));
        List<TrinityPatternVariant> variants = List.of(oakTable, crimsonTable, finish);
        TrinityCraftingTopology topology = TrinityGraphTopologyAnalyzer.create()
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), variants, 8)
                .value();
        TrinityAcyclicDemandPropagator propagator = TrinityAcyclicDemandPropagator.create();

        TrinityAcyclicPlan one = propagator
                .propagate(
                        topology,
                        variants,
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(oak, BigInteger.valueOf(4L), crimson, BigInteger.valueOf(4L)),
                        MAX_SEARCH_STATES,
                        control())
                .value();
        BigInteger largeRequest = BigInteger.valueOf(1_000L);
        TrinityAcyclicPlan huge = propagator
                .propagate(
                        topology,
                        variants,
                        target,
                        largeRequest,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(
                                oak, largeRequest.multiply(BigInteger.valueOf(4L)),
                                crimson, largeRequest.multiply(BigInteger.valueOf(4L))),
                        MAX_SEARCH_STATES,
                        control())
                .value();

        assertEquals(one.statesVisited(), huge.statesVisited());
        assertEquals(2, huge.statesVisited());
        assertEquals(largeRequest, huge.firings().get(oakTable));
        assertEquals(largeRequest, huge.firings().get(crimsonTable));
        assertEquals(largeRequest, huge.firings().get(finish));
        assertFalse(huge.externalInputs().containsKey(stick));
        assertEquals(List.of(oakTable, crimsonTable, finish),
                huge.executionOrder().stream().map(TrinityVariantFiring::variant).toList());
    }

    @Test
    void selectsInventoryBackedBindingInsteadOfStableFirstUnavailableBinding() {
        AEKey oakLog = AEItemKey.of(Items.OAK_LOG);
        AEKey crimsonStem = AEItemKey.of(Items.CRIMSON_STEM);
        AEKey oak = AEItemKey.of(Items.OAK_PLANKS);
        AEKey crimson = AEItemKey.of(Items.CRIMSON_PLANKS);
        AEKey target = AEItemKey.of(Items.CRAFTING_TABLE);
        TrinityPatternVariant oakPlanks = variant(
                "a-oak-planks",
                amounts(oakLog, BigInteger.ONE),
                amounts(oak, BigInteger.valueOf(4L)));
        TrinityPatternVariant crimsonPlanks = variant(
                "b-crimson-planks",
                amounts(crimsonStem, BigInteger.ONE),
                amounts(crimson, BigInteger.valueOf(4L)));
        TrinityPatternVariant stableFirstOak = variant(
                "c-oak-table",
                amounts(oak, BigInteger.valueOf(4L)),
                amounts(target, BigInteger.ONE));
        TrinityPatternVariant availableCrimson = variant(
                "d-crimson-table",
                amounts(crimson, BigInteger.valueOf(4L)),
                amounts(target, BigInteger.ONE));
        List<TrinityPatternVariant> variants = List.of(
                oakPlanks,
                crimsonPlanks,
                stableFirstOak,
                availableCrimson);
        TrinityCraftingTopology topology = TrinityGraphTopologyAnalyzer.create()
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), variants, 8)
                .value();

        TrinityAcyclicPlan plan = TrinityAcyclicDemandPropagator.create()
                .propagate(
                        topology,
                        variants,
                        target,
                        BigInteger.valueOf(128L),
                        CraftingQuantityMode.NET_NEW,
                        Map.of(crimsonStem, BigInteger.valueOf(128L)),
                        MAX_SEARCH_STATES,
                        control())
                .value();

        assertFalse(plan.firings().containsKey(oakPlanks));
        assertFalse(plan.firings().containsKey(stableFirstOak));
        assertEquals(BigInteger.valueOf(128L), plan.firings().get(crimsonPlanks));
        assertEquals(BigInteger.valueOf(128L), plan.firings().get(availableCrimson));
        assertEquals(BigInteger.valueOf(128L), plan.externalInputs().get(crimsonStem));
        assertEquals(List.of(crimsonPlanks, availableCrimson),
                plan.executionOrder().stream().map(TrinityVariantFiring::variant).toList());
        assertEquals(2, plan.statesVisited());
    }

    @Test
    void plansUniformBindingsWithoutSequentialMipPasses() {
        AEKey oak = AEItemKey.of(Items.OAK_PLANKS);
        AEKey crimson = AEItemKey.of(Items.CRIMSON_PLANKS);
        AEKey mangrove = AEItemKey.of(Items.MANGROVE_PLANKS);
        AEKey target = AEItemKey.of(Items.CRAFTING_TABLE);
        TrinityPatternIdentity identity = new TrinityPatternIdentity(
                "definition-table-bindings",
                "publication-table-bindings");
        TrinityPatternVariant stableFirstOak = bindingVariant(identity, 0, oak, target);
        TrinityPatternVariant availableCrimson = bindingVariant(identity, 1, crimson, target);
        TrinityPatternVariant availableMangrove = bindingVariant(identity, 2, mangrove, target);
        List<TrinityPatternVariant> variants = List.of(
                stableFirstOak,
                availableCrimson,
                availableMangrove);
        TrinityCraftingTopology topology = TrinityGraphTopologyAnalyzer.create()
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), variants, 8)
                .value();

        TrinityAlgorithmResult<TrinityAcyclicPlan> result = TrinityAcyclicDemandPropagator.create()
                .propagate(
                        topology,
                        variants,
                        target,
                        BigInteger.TEN,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(
                                crimson, BigInteger.valueOf(16L),
                                mangrove, BigInteger.valueOf(24L)),
                        variants.size(),
                        control());

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityAcyclicPlan plan = result.value();
        assertFalse(plan.firings().containsKey(stableFirstOak));
        assertEquals(BigInteger.valueOf(4L), plan.firings().get(availableCrimson));
        assertEquals(BigInteger.valueOf(6L), plan.firings().get(availableMangrove));
        assertEquals(BigInteger.valueOf(16L), plan.externalInputs().get(crimson));
        assertEquals(BigInteger.valueOf(24L), plan.externalInputs().get(mangrove));
        assertEquals(2, plan.statesVisited());
    }

    @Test
    void appliesTargetInventoryOnlyToFinalTotalSemantics() {
        AEKey raw = AEItemKey.of(Items.COBBLESTONE);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityPatternVariant producer = variant(
                "producer",
                amounts(raw, BigInteger.ONE),
                amounts(target, BigInteger.ONE));
        List<TrinityPatternVariant> variants = List.of(producer);
        TrinityCraftingTopology topology = TrinityGraphTopologyAnalyzer.create()
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), variants, 8)
                .value();
        TrinityAcyclicDemandPropagator propagator = TrinityAcyclicDemandPropagator.create();

        TrinityAcyclicPlan netNew = propagator
                .propagate(
                        topology,
                        variants,
                        target,
                        BigInteger.TEN,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(target, BigInteger.valueOf(7L), raw, BigInteger.TEN),
                        MAX_SEARCH_STATES,
                        control())
                .value();
        TrinityAcyclicPlan finalTotal = propagator
                .propagate(
                        topology,
                        variants,
                        target,
                        BigInteger.TEN,
                        CraftingQuantityMode.FINAL_TOTAL,
                        Map.of(target, BigInteger.valueOf(7L), raw, BigInteger.TEN),
                        MAX_SEARCH_STATES,
                        control())
                .value();

        assertEquals(BigInteger.TEN, netNew.firings().get(producer));
        assertEquals(BigInteger.valueOf(3L), finalTotal.firings().get(producer));
        assertEquals(BigInteger.valueOf(3L), finalTotal.externalInputs().get(raw));
    }

    @Test
    void finalTotalStillRunsOneCompleteProductionWhenTargetInventoryAlreadySuffices() {
        AEKey raw = AEItemKey.of(Items.COBBLESTONE);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityPatternVariant producer = variant(
                "producer",
                amounts(raw, BigInteger.ONE),
                amounts(target, BigInteger.TWO));
        List<TrinityPatternVariant> variants = List.of(producer);
        TrinityCraftingTopology topology = TrinityGraphTopologyAnalyzer.create()
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), variants, 8)
                .value();

        TrinityAcyclicPlan plan = TrinityAcyclicDemandPropagator.create()
                .propagate(
                        topology,
                        variants,
                        target,
                        BigInteger.TEN,
                        CraftingQuantityMode.FINAL_TOTAL,
                        Map.of(target, BigInteger.valueOf(20L), raw, BigInteger.ONE),
                        MAX_SEARCH_STATES,
                        control())
                .value();

        assertEquals(BigInteger.ONE, plan.firings().get(producer));
        assertEquals(BigInteger.TEN, plan.externalInputs().get(target));
        assertEquals(BigInteger.ONE, plan.externalInputs().get(raw));
    }

    @Test
    void diagnosesAllExternalLeafShortagesOnTheBestOfMultipleRoutes() {
        AEKey cobblestone = AEItemKey.of(Items.COBBLESTONE);
        AEKey coal = AEItemKey.of(Items.COAL);
        AEKey iron = AEItemKey.of(Items.IRON_INGOT);
        AEKey gold = AEItemKey.of(Items.GOLD_INGOT);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityPatternVariant stableFirstExpensiveRoute = variant(
                "a-expensive-route",
                amounts(cobblestone, BigInteger.valueOf(4L), coal, BigInteger.valueOf(4L)),
                amounts(target, BigInteger.ONE));
        TrinityPatternVariant laterCheaperRoute = variant(
                "b-cheaper-route",
                amounts(iron, BigInteger.valueOf(3L), gold, BigInteger.TWO),
                amounts(target, BigInteger.ONE));

        List<TrinityPatternVariant> variants = List.of(stableFirstExpensiveRoute, laterCheaperRoute);
        TrinityCraftingTopology topology = TrinityGraphTopologyAnalyzer.create()
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), variants, 8)
                .value();

        TrinityAlgorithmResult<TrinityAcyclicPlan> result = TrinityAcyclicDemandPropagator.create()
                .propagate(
                        topology,
                        variants,
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(iron, BigInteger.ONE, gold, BigInteger.ONE),
                        MAX_SEARCH_STATES,
                        control());

        assertFalse(result.successful());
        assertEquals(TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT, result.diagnostic().code());
        var partial = result.diagnostic().partialPlan().orElseThrow();
        assertEquals(BigInteger.ONE, partial.usedItems().get(iron));
        assertEquals(BigInteger.ONE, partial.usedItems().get(gold));
        assertEquals(BigInteger.TWO, partial.missingItems().get(iron));
        assertEquals(BigInteger.ONE, partial.missingItems().get(gold));
        assertEquals(BigInteger.valueOf(3L), partial.inputRequirements().get(iron).required());
        assertEquals(BigInteger.ONE, partial.inputRequirements().get(iron).available());
        assertEquals(BigInteger.TWO, partial.inputRequirements().get(iron).missing());
        assertEquals(BigInteger.TWO, partial.inputRequirements().get(gold).required());
        assertEquals(BigInteger.ONE, partial.inputRequirements().get(gold).available());
        assertEquals(BigInteger.ONE, partial.inputRequirements().get(gold).missing());
        assertFalse(partial.missingItems().containsKey(cobblestone));
        assertFalse(partial.missingItems().containsKey(coal));
        assertEquals(BigInteger.ONE, partial.emittedItems().get(target));
    }

    @Test
    void rejectsUncraftableSourceShortageInsteadOfReturningExecutablePlan() {
        AEKey raw = AEItemKey.of(Items.COBBLESTONE);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityPatternVariant producer = variant(
                "producer",
                amounts(raw, BigInteger.valueOf(5L)),
                amounts(target, BigInteger.ONE));
        List<TrinityPatternVariant> variants = List.of(producer);
        TrinityCraftingTopology topology = TrinityGraphTopologyAnalyzer.create()
                .analyze(new TrinityCraftingGraphSnapshot(1L, List.of()), variants, 8)
                .value();

        TrinityAlgorithmResult<TrinityAcyclicPlan> result = TrinityAcyclicDemandPropagator.create()
                .propagate(
                        topology,
                        variants,
                        target,
                        BigInteger.ONE,
                        CraftingQuantityMode.NET_NEW,
                        Map.of(raw, BigInteger.valueOf(3L)),
                        MAX_SEARCH_STATES,
                        control());

        assertFalse(result.successful());
        assertEquals(TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT, result.diagnostic().code());
        assertEquals("5", result.diagnostic().metadata().get("required"));
        assertEquals("3", result.diagnostic().metadata().get("available"));
    }

    private static TrinityPlanningControl control() {
        return TrinityPlanningControl.create(() -> false, () -> 0L, Long.MAX_VALUE);
    }

    private static TrinityPatternVariant bindingVariant(TrinityPatternIdentity identity,
                                                        int ordinal,
                                                        AEKey input,
                                                        AEKey output) {
        BigInteger inputAmount = BigInteger.valueOf(4L);
        return new TrinityPatternVariant(
                identity,
                output,
                ordinal,
                List.of(),
                List.of(),
                amounts(input, inputAmount),
                amounts(output, BigInteger.ONE),
                amounts(input, inputAmount.negate(), output, BigInteger.ONE));
    }
}

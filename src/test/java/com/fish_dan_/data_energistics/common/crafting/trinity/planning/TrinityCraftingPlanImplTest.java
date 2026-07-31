package com.fish_dan_.data_energistics.common.crafting.trinity.planning;

import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityCraftingPlanImplTest {

    private static final AEKey TARGET = DataKey.of();
    private static final AEKey INPUT = DataFlowKey.of();
    private static final TrinityPatternIdentity PATTERN = new TrinityPatternIdentity("definition", "publication");

    @Test
    void buildsCompactDagPlanWithoutRetainingMutablePatternDetails() {
        TrinityCraftingPlan plan = dagPlan();

        assertEquals(new GenericStack(TARGET, 4L), plan.finalOutput());
        assertEquals(12L, plan.bytes());
        assertEquals(7L, plan.catalogRevision());
        assertEquals(CraftingQuantityMode.NET_NEW, plan.quantityMode());
        assertEquals(BigInteger.valueOf(4L), plan.patternFirings().get(PATTERN));
        assertEquals(List.of(0), plan.stageOrder());
        assertTrue(plan.patternTimes().isEmpty());
        assertFalse(plan.simulation());
        assertTrue(plan.missingItems().isEmpty());
        assertEquals(4L, plan.usedItems().get(INPUT));

        plan.usedItems().add(INPUT, 100L);
        assertEquals(4L, plan.usedItems().get(INPUT));
        assertThrows(UnsupportedOperationException.class,
                () -> plan.patternFirings().put(PATTERN, BigInteger.TEN));
    }

    @Test
    void multipliesCycleStageFiringsByCompactRepeatCount() {
        TrinityPlanStage cycle = new TrinityPlanStage(
                0,
                true,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(PATTERN, 0, BigInteger.ONE)),
                Map.of(TARGET, BigInteger.ONE),
                Map.of(TARGET, BigInteger.ONE));
        TrinityCycleRepeatBlock repeat = new TrinityCycleRepeatBlock(
                0,
                List.of(0),
                BigInteger.valueOf(5L),
                Map.of(TARGET, BigInteger.ONE),
                Map.of(TARGET, BigInteger.valueOf(5L)));

        TrinityCraftingPlan plan = TrinityCraftingPlanImpl.builder()
                .finalOutput(new GenericStack(TARGET, 5L))
                .bytes(16L)
                .catalogRevision(8L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(TARGET, BigInteger.ONE))
                .patternFirings(Map.of(PATTERN, BigInteger.valueOf(5L)))
                .stages(List.of(cycle))
                .stageOrder(List.of(0))
                .cycleRepeatBlocks(List.of(repeat))
                .minimumSeed(Map.of(TARGET, BigInteger.ONE))
                .targetNetChange(Map.of(TARGET, BigInteger.valueOf(5L)))
                .build();

        assertEquals(BigInteger.valueOf(5L), plan.patternFirings().get(PATTERN));
        assertEquals(BigInteger.valueOf(5L), plan.cycleRepeatBlocks().getFirst().repetitions());
    }

    @Test
    void finalTotalAllowsSeedToCoverDeliveryButStillRequiresProductiveCycle() {
        TrinityPlanStage cycle = new TrinityPlanStage(
                0,
                true,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(PATTERN, 0, BigInteger.ONE)),
                Map.of(TARGET, BigInteger.TEN),
                Map.of(TARGET, BigInteger.ONE));

        TrinityCraftingPlan plan = TrinityCraftingPlanImpl.builder()
                .finalOutput(new GenericStack(TARGET, 10L))
                .bytes(16L)
                .catalogRevision(9L)
                .quantityMode(CraftingQuantityMode.FINAL_TOTAL)
                .initialExpectedInputs(Map.of(TARGET, BigInteger.TEN))
                .patternFirings(Map.of(PATTERN, BigInteger.ONE))
                .stages(List.of(cycle))
                .stageOrder(List.of(0))
                .cycleRepeatBlocks(List.of(new TrinityCycleRepeatBlock(
                        0,
                        List.of(0),
                        BigInteger.ONE,
                        Map.of(TARGET, BigInteger.TEN),
                        Map.of(TARGET, BigInteger.ONE))))
                .minimumSeed(Map.of(TARGET, BigInteger.TEN))
                .targetNetChange(Map.of(TARGET, BigInteger.ONE))
                .build();

        assertEquals(CraftingQuantityMode.FINAL_TOTAL, plan.quantityMode());
        assertEquals(BigInteger.ONE, plan.targetNetChange().get(TARGET));
    }

    @Test
    void rejectsStageAggregationSeedAndAe2AmountBoundaryViolations() {
        TrinityCraftingPlanImpl.Builder wrongAggregation = baseBuilder()
                .patternFirings(Map.of(PATTERN, BigInteger.ONE));
        assertThrows(IllegalArgumentException.class, wrongAggregation::build);

        TrinityCraftingPlanImpl.Builder missingSeedOwnership = baseBuilder()
                .minimumSeed(Map.of(INPUT, BigInteger.valueOf(5L)));
        assertThrows(IllegalArgumentException.class, missingSeedOwnership::build);

        TrinityCraftingPlanImpl.Builder overflowingInput = baseBuilder()
                .initialExpectedInputs(Map.of(INPUT, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)));
        assertThrows(ArithmeticException.class, overflowingInput::build);
    }

    private static TrinityCraftingPlan dagPlan() {
        return baseBuilder().build();
    }

    private static TrinityCraftingPlanImpl.Builder baseBuilder() {
        TrinityPlanStage stage = new TrinityPlanStage(
                0,
                false,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(PATTERN, 0, BigInteger.valueOf(4L))),
                Map.of(INPUT, BigInteger.valueOf(4L)),
                Map.of(INPUT, BigInteger.valueOf(-4L), TARGET, BigInteger.valueOf(4L)));
        return TrinityCraftingPlanImpl.builder()
                .finalOutput(new GenericStack(TARGET, 4L))
                .bytes(12L)
                .multiplePaths(false)
                .catalogRevision(7L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(INPUT, BigInteger.valueOf(4L)))
                .patternFirings(Map.of(PATTERN, BigInteger.valueOf(4L)))
                .stages(List.of(stage))
                .stageOrder(List.of(0))
                .cycleRepeatBlocks(List.of())
                .minimumSeed(Map.of())
                .targetNetChange(Map.of(INPUT, BigInteger.valueOf(-4L), TARGET, BigInteger.valueOf(4L)))
                .emittedItems(Map.of())
                .statistics(new TrinityPlanningStatistics(1, 1, 10L, 0L, 1));
    }
}

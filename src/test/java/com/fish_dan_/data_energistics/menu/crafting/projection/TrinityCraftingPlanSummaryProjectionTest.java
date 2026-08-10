package com.fish_dan_.data_energistics.menu.crafting.projection;

import com.fish_dan_.data_energistics.ae2.key.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.key.DataKey;
import com.fish_dan_.data_energistics.ae2.key.EchoKey;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.integration.ae2ct.TrinityCraftingTreeProjection;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.menu.me.crafting.CraftingPlanSummaryEntry;
import com.neuvillette.ae2ct.api.RecipeHelper;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TrinityCraftingPlanSummaryProjectionTest {

    private static final TrinityPatternIdentity FIRST = new TrinityPatternIdentity("first", "first-publication");
    private static final TrinityPatternIdentity SECOND = new TrinityPatternIdentity("second", "second-publication");

    @Test
    void projectsNativeSummaryAndCraftingTreeFromTheSamePlan() {
        AEKey input = DataFlowKey.of();
        AEKey intermediate = EchoKey.of();
        AEKey target = DataKey.of();
        TrinityPlanStage first = new TrinityPlanStage(
                0,
                false,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(
                        FIRST,
                        intermediate,
                        0,
                        BigInteger.TWO,
                        Map.of(input, BigInteger.ONE),
                        Map.of(intermediate, BigInteger.ONE))),
                Map.of(input, BigInteger.TWO),
                Map.of(input, BigInteger.TWO.negate(), intermediate, BigInteger.TWO));
        TrinityPlanStage second = new TrinityPlanStage(
                1,
                false,
                Set.of(0),
                List.of(new TrinityPlanPatternFiring(
                        SECOND,
                        target,
                        0,
                        BigInteger.TWO,
                        Map.of(intermediate, BigInteger.ONE),
                        Map.of(target, BigInteger.TWO))),
                Map.of(intermediate, BigInteger.TWO),
                Map.of(intermediate, BigInteger.TWO.negate(), target, BigInteger.valueOf(4L)));
        TrinityCraftingPlan plan = TrinityCraftingPlan.builder()
                .finalOutput(new GenericStack(target, 4L))
                .bytes(32L)
                .catalogRevision(1L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(input, BigInteger.TWO))
                .patternFirings(Map.of(FIRST, BigInteger.TWO, SECOND, BigInteger.TWO))
                .stages(List.of(first, second))
                .stageOrder(List.of(0, 1))
                .cycleRepeatBlocks(List.of())
                .minimumSeed(Map.of())
                .targetNetChange(Map.of(input, BigInteger.TWO.negate(), target, BigInteger.valueOf(4L)))
                .build();

        Map<AEKey, CraftingPlanSummaryEntry> entries = TrinityCraftingPlanSummaryProjection.create(plan)
                .getEntries()
                .stream()
                .collect(Collectors.toMap(CraftingPlanSummaryEntry::getWhat, Function.identity()));

        assertEquals(2L, entries.get(input).getStoredAmount());
        assertEquals(0L, entries.get(input).getCraftAmount());
        assertEquals(2L, entries.get(intermediate).getCraftAmount());
        assertEquals(4L, entries.get(target).getCraftAmount());
        assertEquals(0L, entries.get(target).getStoredAmount());

        RecipeHelper tree = TrinityCraftingTreeProjection.create(plan);
        assertEquals(plan.finalOutput(), tree.output);
        assertEquals(2, tree.recipes.size());
        assertEquals(List.of(new GenericStack(input, 1L)), tree.recipes.getFirst().inputs());
        assertEquals(List.of(new GenericStack(intermediate, 1L)), tree.recipes.get(1).inputs());
        assertEquals(2L, tree.recipes.getFirst().times());
        assertEquals(2L, tree.recipes.get(1).times());
    }
}

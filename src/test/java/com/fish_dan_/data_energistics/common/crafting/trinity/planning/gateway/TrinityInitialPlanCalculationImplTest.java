package com.fish_dan_.data_energistics.common.crafting.trinity.planning.gateway;

import com.fish_dan_.data_energistics.ae2.DataFlowKey;
import com.fish_dan_.data_energistics.ae2.DataKey;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration.TrinityGraphPlanner;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlanImpl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanPatternFiring;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.configuration.snapshot.TrinityCraftingSettings;

import net.minecraft.network.chat.Component;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TrinityInitialPlanCalculationImplTest {

    private static final AEKey TARGET = DataKey.of();
    private static final AEKey INPUT = DataFlowKey.of();

    @Test
    void rejectsPlanThatExceedsEveryEligibleTrinityCpuCapturedAtRequestStart() {
        TrinityCraftingPlan oversizedPlan = oversizedPlan();
        TrinityGraphPlanner planner = (snapshot, target, requestedAmount, quantityMode, available, settings, control) -> {
            assertFalse(control.deadlineConfigured());
            return TrinityAlgorithmResult.success(oversizedPlan);
        };
        TrinityInitialPlanningRequest request = TrinityInitialPlanningRequest.builder()
                .requestId(7L)
                .graph(new TrinityCraftingGraphSnapshot(19L, List.of()))
                .target(TARGET)
                .requestedAmount(BigInteger.ONE)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .available(Map.of())
                .settings(TrinityCraftingSettings.defaults(4))
                .maxTrinityBytes(10L)
                .build();

        TrinityPlanningAttempt attempt = new TrinityInitialPlanCalculationImpl(planner).calculate(request);

        assertFalse(attempt.successful());
        assertEquals(TrinityPlanningDiagnosticCode.NO_ELIGIBLE_TRINITY_CPU, attempt.diagnostic().code());
        assertEquals("11", attempt.diagnostic().metadata().get("planBytes"));
        assertEquals("10", attempt.diagnostic().metadata().get("maxTrinityBytes"));
    }

    @Test
    void turnsExactCycleShortageIntoAStandaloneDiagnosticSimulation() {
        BigInteger requested = BigInteger.valueOf(1_256_000_000L);
        BigInteger required = BigInteger.valueOf(8_792_000_000L);
        BigInteger available = BigInteger.valueOf(2_147_483_821L);
        BigInteger missing = BigInteger.valueOf(6_644_516_179L);
        TrinityPlanningDiagnostic diagnostic = new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.INSUFFICIENT_INPUT,
                Component.literal("material shortage"),
                Map.of(),
                new TrinityPlanningDiagnostic.InputShortage(INPUT, required, available, missing));
        TrinityGraphPlanner planner = (snapshot, target, requestedAmount, quantityMode, inventory, settings, control) -> TrinityAlgorithmResult.failure(diagnostic);
        TrinityInitialPlanningRequest request = TrinityInitialPlanningRequest.builder()
                .requestId(8L)
                .graph(new TrinityCraftingGraphSnapshot(20L, List.of()))
                .target(TARGET)
                .requestedAmount(requested)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .available(Map.of(INPUT, available))
                .settings(TrinityCraftingSettings.defaults(4))
                .maxTrinityBytes(Long.MAX_VALUE)
                .build();

        TrinityPlanningAttempt attempt = new TrinityInitialPlanCalculationImpl(planner).calculate(request);

        assertFalse(attempt.successful());
        TrinityDiagnosedCraftingPlan simulation = attempt.authoritativeSimulation().orElseThrow();
        assertFalse(simulation.ae2FallbackEstimate());
        assertTrue(simulation.simulation());
        assertEquals(requested.longValueExact(), simulation.finalOutput().amount());
        assertEquals(available.longValueExact(), simulation.usedItems().get(INPUT));
        assertEquals(missing.longValueExact(), simulation.missingItems().get(INPUT));
    }

    private static TrinityCraftingPlan oversizedPlan() {
        TrinityPatternIdentity identity = new TrinityPatternIdentity("definition", "publication");
        TrinityPlanStage stage = new TrinityPlanStage(
                0,
                false,
                Set.of(),
                List.of(new TrinityPlanPatternFiring(identity, TARGET, 0, BigInteger.ONE)),
                Map.of(INPUT, BigInteger.ONE),
                Map.of(INPUT, BigInteger.ONE.negate(), TARGET, BigInteger.ONE));
        return TrinityCraftingPlanImpl.builder()
                .finalOutput(new GenericStack(TARGET, 1L))
                .bytes(11L)
                .catalogRevision(19L)
                .quantityMode(CraftingQuantityMode.NET_NEW)
                .initialExpectedInputs(Map.of(INPUT, BigInteger.ONE))
                .patternFirings(Map.of(identity, BigInteger.ONE))
                .stages(List.of(stage))
                .stageOrder(List.of(0))
                .cycleRepeatBlocks(List.of())
                .minimumSeed(Map.of())
                .targetNetChange(Map.of(INPUT, BigInteger.ONE.negate(), TARGET, BigInteger.ONE))
                .build();
    }
}

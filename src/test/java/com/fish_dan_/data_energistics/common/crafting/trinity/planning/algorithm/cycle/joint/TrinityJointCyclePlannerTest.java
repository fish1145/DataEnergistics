package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.joint;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle.TrinityCycleDemand;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityExactConservationVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.optimization.TrinityIntegerResultVerifier;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.topology.TrinityStronglyConnectedComponent;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternVariant;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.ojalgo.optimisation.integer.IntegerStrategy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import static com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmTestPatterns.amounts;
import static com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmTestPatterns.variant;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityJointCyclePlannerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void missingScheduleStateMetadataFailsFast() {
        TrinityPlanningDiagnostic diagnostic = new TrinityPlanningDiagnostic(
                TrinityPlanningDiagnosticCode.NO_EXECUTABLE_ORDER,
                Component.literal("missing states"),
                Map.of());

        assertThrows(
                IllegalStateException.class,
                () -> TrinityJointCyclePlanner.diagnosticStates(diagnostic));
    }

    @Test
    void sequentialObjectivesMinimizeExternalInputThenSeedThenFirings() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey fuel = AEItemKey.of(Items.COAL);
        TrinityPatternVariant slow = variant(
                "a-slow",
                amounts(a, BigInteger.ONE, fuel, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        TrinityPatternVariant fast = variant(
                "b-fast",
                amounts(a, BigInteger.ONE, fuel, BigInteger.TWO),
                amounts(a, BigInteger.valueOf(3L)));

        TrinityJointCyclePlan plan = solve(
                component(a, slow, fast),
                a,
                BigInteger.TEN,
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.ONE, fuel, BigInteger.TEN),
                1000)
                .value();

        assertEquals(Map.of(fast, BigInteger.valueOf(5L)), plan.firings());
        assertEquals(BigInteger.TEN, plan.externalInputs().get(fuel));
        assertEquals(BigInteger.ONE, plan.minimumSeed().get(a));
        assertEquals(BigInteger.TEN, plan.netChange().get(a));
        assertTrue(plan.solverPasses() > 0);
    }

    @ParameterizedTest
    @CsvSource({
            "1, seed",
            "1000000, seed",
            "1, avoidable_external",
            "1000000, avoidable_external",
            "1, required_external",
            "1000000, required_external"
    })
    void trueObjectivesAndExternalPrefixCutsDoNotEnumerateQuantity(long requestedAmount, String routeMode) {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey b = AEItemKey.of(Items.GOLD_INGOT);
        AEKey catalyst = AEItemKey.of(Items.BUCKET);
        boolean directNeedsExternal = !routeMode.equals("seed");
        boolean cycleNeedsExternal = routeMode.equals("required_external");
        TrinityPatternVariant direct = variant(
                "a-direct",
                directNeedsExternal ?
                        amounts(a, BigInteger.TWO, catalyst, BigInteger.ONE) : amounts(a, BigInteger.TWO),
                directNeedsExternal ?
                        amounts(a, BigInteger.valueOf(3L), catalyst, BigInteger.ONE) :
                        amounts(a, BigInteger.valueOf(3L)));
        TrinityPatternVariant aToB = variant(
                "b-a-to-b",
                amounts(a, BigInteger.ONE),
                amounts(b, BigInteger.ONE));
        TrinityPatternVariant bToA = variant(
                "c-b-to-a",
                cycleNeedsExternal ?
                        amounts(b, BigInteger.ONE, catalyst, BigInteger.ONE) : amounts(b, BigInteger.ONE),
                cycleNeedsExternal ?
                        amounts(a, BigInteger.TWO, catalyst, BigInteger.ONE) : amounts(a, BigInteger.TWO));
        TrinityStronglyConnectedComponent component = new TrinityStronglyConnectedComponent(
                0,
                List.of(a, b),
                true,
                List.of(direct, aToB, bToA),
                List.of(),
                List.of());
        BigInteger requested = BigInteger.valueOf(requestedAmount);
        Map<AEKey, BigInteger> available = directNeedsExternal ?
                Map.of(a, BigInteger.TWO, catalyst, BigInteger.ONE) : Map.of(a, BigInteger.TWO);

        TrinityAlgorithmResult<TrinityJointCyclePlan> result = solve(
                component,
                a,
                requested,
                CraftingQuantityMode.NET_NEW,
                available,
                1000);
        assertTrue(result.successful(), () -> result.diagnostic().toString());
        TrinityJointCyclePlan plan = result.value();

        Map<TrinityPatternVariant, BigInteger> expected = routeMode.equals("avoidable_external") ?
                Map.of(aToB, requested, bToA, requested) :
                requested.equals(BigInteger.ONE) ?
                        Map.of(aToB, BigInteger.ONE, bToA, BigInteger.ONE) :
                        Map.of(
                                direct, requested.subtract(BigInteger.ONE),
                                aToB, BigInteger.ONE,
                                bToA, BigInteger.ONE);
        assertEquals(expected, plan.firings(), plan::toString);
        assertEquals(
                cycleNeedsExternal ? Map.of(catalyst, BigInteger.ONE) : Map.of(),
                plan.externalInputs());
        assertEquals(BigInteger.ONE, plan.minimumSeed().get(a));
        assertTrue(plan.searchStates() <= 64, () -> "compressed states=" + plan.searchStates());
    }

    @Test
    void stableIdentityBreaksACompleteLexicographicTie() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        TrinityPatternVariant first = variant(
                "a-first",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        TrinityPatternVariant second = variant(
                "b-second",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO));

        TrinityJointCyclePlan plan = solve(
                component(a, first, second),
                a,
                BigInteger.valueOf(3L),
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.ONE),
                100)
                .value();

        assertEquals(Map.of(first, BigInteger.valueOf(3L)), plan.firings());
    }

    @Test
    void producibleBoundaryInputsAreDemandsInsteadOfCurrentInventoryCaps() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey fuel = AEItemKey.of(Items.COAL);
        TrinityPatternVariant fuelled = variant(
                "fuelled",
                amounts(a, BigInteger.ONE, fuel, BigInteger.ONE),
                amounts(a, BigInteger.TWO));

        TrinityAlgorithmResult<TrinityJointCyclePlan> result = TrinityJointCyclePlanner.create().plan(
                component(a, fuelled),
                a,
                BigInteger.valueOf(100L),
                CraftingQuantityMode.NET_NEW,
                Map.of(),
                Set.of(a, fuel),
                100,
                TrinityPlanningControl.create(() -> false, () -> 0L, 1_000_000_000L));

        assertTrue(result.successful());
        assertEquals(BigInteger.valueOf(100L), result.value().externalInputs().get(fuel));
        assertEquals(BigInteger.ONE, result.value().minimumSeed().get(a));
        assertEquals(BigInteger.valueOf(100L), result.value().initialInputs().get(fuel));
        assertEquals(BigInteger.ONE, result.value().initialInputs().get(a));
        assertTrue(result.value().schedule().statesVisited() <= 2);
    }

    @Test
    void finalTotalUsesExistingTargetButStillRequiresOneProductiveFiring() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        TrinityPatternVariant multiply = variant(
                "multiply",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO));

        TrinityJointCyclePlan plan = solve(
                component(a, multiply),
                a,
                BigInteger.valueOf(5L),
                CraftingQuantityMode.FINAL_TOTAL,
                Map.of(a, BigInteger.TEN),
                100)
                .value();

        assertEquals(BigInteger.valueOf(4L), plan.firings().get(multiply));
        assertEquals(BigInteger.ONE, plan.minimumSeed().get(a));
        assertEquals(BigInteger.ONE, plan.initialInputs().get(a));
        assertEquals(BigInteger.valueOf(5L),
                plan.initialInputs().get(a).add(plan.netChange().get(a)));
    }

    @Test
    void solvesAllFinalBalanceAxesOfOneComponentTogether() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey b = AEItemKey.of(Items.GOLD_INGOT);
        TrinityPatternVariant aToB = variant(
                "a-to-b",
                amounts(a, BigInteger.ONE),
                amounts(b, BigInteger.TWO));
        TrinityPatternVariant bToA = variant(
                "b-to-a",
                amounts(b, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        TrinityStronglyConnectedComponent component = new TrinityStronglyConnectedComponent(
                0,
                List.of(a, b),
                true,
                List.of(aToB, bToA),
                List.of(),
                List.of());
        TrinityCycleDemand demand = new TrinityCycleDemand(
                Map.of(a, BigInteger.ONE, b, BigInteger.valueOf(3L)),
                Map.of(b, BigInteger.valueOf(3L)));

        TrinityAlgorithmResult<TrinityJointCyclePlan> result = TrinityJointCyclePlanner.create().plan(
                component,
                demand,
                Map.of(a, BigInteger.ONE),
                100,
                TrinityPlanningControl.create(() -> false, () -> 0L, 1_000_000_000L));

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityJointCyclePlan plan = result.value();
        assertEquals(Map.of(aToB, BigInteger.TWO, bToA, BigInteger.ONE), plan.firings());
        assertEquals(Map.of(a, BigInteger.ONE), plan.initialInputs());
        assertEquals(Map.of(b, BigInteger.valueOf(3L)), plan.netChange());
        assertEquals(
                Map.of(a, BigInteger.ONE, b, BigInteger.valueOf(3L)),
                plan.schedule().finalBalances());
    }

    @Test
    void radixModelSolvesLongMaxDemandWithoutNarrowingExactTotals() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey b = AEItemKey.of(Items.GOLD_INGOT);
        TrinityPatternVariant aToB = variant(
                "a-to-b",
                amounts(a, BigInteger.ONE),
                amounts(b, BigInteger.TWO));
        TrinityPatternVariant bToA = variant(
                "b-to-a",
                amounts(b, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        TrinityStronglyConnectedComponent component = new TrinityStronglyConnectedComponent(
                0,
                List.of(a, b),
                true,
                List.of(aToB, bToA),
                List.of(),
                List.of());
        BigInteger target = BigInteger.valueOf(Long.MAX_VALUE);
        TrinityCycleDemand demand = new TrinityCycleDemand(Map.of(), Map.of(a, target));

        TrinityAlgorithmResult<TrinityJointCyclePlan> result = TrinityJointCyclePlanner.create().plan(
                component,
                demand,
                Map.of(b, BigInteger.ONE),
                1000,
                TrinityPlanningControl.create(() -> false, () -> 0L, Long.MAX_VALUE));

        assertTrue(result.successful(), () -> result.diagnostic().message().getString() + result.diagnostic().metadata());
        TrinityJointCyclePlan plan = result.value();
        BigInteger aToBFirings = target.add(BigInteger.TWO).divide(BigInteger.valueOf(3L));
        BigInteger bToAFirings = aToBFirings.multiply(BigInteger.TWO);
        assertEquals(Map.of(aToB, aToBFirings, bToA, bToAFirings), plan.firings());
        assertEquals(Map.of(b, BigInteger.ONE), plan.minimumSeed());
        assertEquals(Map.of(b, BigInteger.ONE), plan.initialInputs());
        assertEquals(Map.of(a, aToBFirings.multiply(BigInteger.valueOf(3L))), plan.netChange());
        assertEquals(
                Map.of(a, aToBFirings.multiply(BigInteger.valueOf(3L)), b, BigInteger.ONE),
                plan.schedule().finalBalances());
    }

    @Test
    void solvesBoundaryOutputNetDemandWithoutConsumingTheInternalSeed() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityPatternVariant growthWithBoundaryOutput = variant(
                "growth-with-boundary-output",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO, target, BigInteger.ONE));
        TrinityCycleDemand demand = new TrinityCycleDemand(
                Map.of(),
                Map.of(target, BigInteger.ONE));

        TrinityAlgorithmResult<TrinityJointCyclePlan> result = TrinityJointCyclePlanner.create().plan(
                component(a, growthWithBoundaryOutput),
                demand,
                Map.of(a, BigInteger.ONE),
                100,
                TrinityPlanningControl.create(() -> false, () -> 0L, 1_000_000_000L));

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityJointCyclePlan plan = result.value();
        assertEquals(Map.of(growthWithBoundaryOutput, BigInteger.ONE), plan.firings());
        assertEquals(Map.of(a, BigInteger.ONE), plan.minimumSeed());
        assertEquals(Map.of(a, BigInteger.ONE), plan.initialInputs());
        assertEquals(Map.of(a, BigInteger.ONE, target, BigInteger.ONE), plan.netChange());
        assertEquals(Map.of(a, BigInteger.TWO, target, BigInteger.ONE), plan.schedule().finalBalances());
    }

    @Test
    void reportsInfeasibleCancellationTimeoutAndCandidateLimit() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey fuel = AEItemKey.of(Items.COAL);
        TrinityPatternVariant fuelled = variant(
                "fuelled",
                amounts(a, BigInteger.ONE, fuel, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        TrinityAlgorithmResult<TrinityJointCyclePlan> infeasible = solve(
                component(a, fuelled),
                a,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.ONE),
                100);
        TrinityAlgorithmResult<TrinityJointCyclePlan> cancelled = TrinityJointCyclePlanner.create().plan(
                component(a, fuelled),
                a,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.ONE, fuel, BigInteger.ONE),
                100,
                TrinityPlanningControl.create(() -> true, () -> 0L, 1L));
        AtomicLong clock = new AtomicLong();
        TrinityAlgorithmResult<TrinityJointCyclePlan> timedOut = TrinityJointCyclePlanner.create().plan(
                component(a, fuelled),
                a,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.ONE, fuel, BigInteger.ONE),
                100,
                TrinityPlanningControl.create(() -> false, () -> clock.getAndAdd(10L), 5L));
        TrinityPatternVariant highSeed = variant(
                "a-high-seed",
                amounts(a, BigInteger.TEN),
                amounts(a, BigInteger.valueOf(11L)));
        TrinityPatternVariant lowSeed = variant(
                "b-low-seed",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        TrinityAlgorithmResult<TrinityJointCyclePlan> limited = solve(
                component(a, highSeed, lowSeed),
                a,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.TEN),
                1);

        assertFalse(infeasible.successful());
        assertEquals(TrinityPlanningDiagnosticCode.MIP_NO_INTEGER_SOLUTION, infeasible.diagnostic().code());
        assertEquals(TrinityPlanningDiagnosticCode.CALCULATION_CANCELLED, cancelled.diagnostic().code());
        assertEquals(TrinityPlanningDiagnosticCode.MIP_TIMEOUT, timedOut.diagnostic().code());
        assertEquals(TrinityPlanningDiagnosticCode.ORDER_SEARCH_LIMIT, limited.diagnostic().code());
    }

    @Test
    void integerVerifierAcceptsSolverNoiseButRejectsFractionalValues() {
        TrinityIntegerResultVerifier verifier = TrinityIntegerResultVerifier.create();

        TrinityAlgorithmResult<List<BigInteger>> exact = verifier.verify(
                List.of(new BigDecimal("23.99999999999995"), new BigDecimal("320.0000000000018")),
                IntegerStrategy.DEFAULT.getIntegralityTolerance());
        TrinityAlgorithmResult<List<BigInteger>> inexact = verifier.verify(
                List.of(new BigDecimal("1"), new BigDecimal("2.0000001")),
                IntegerStrategy.DEFAULT.getIntegralityTolerance());

        assertEquals(List.of(BigInteger.valueOf(24L), BigInteger.valueOf(320L)), exact.value());
        assertFalse(inexact.successful());
        assertEquals(TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT, inexact.diagnostic().code());
    }

    @Test
    void exactConservationVerifierRejectsAnIntegralConstraintViolation() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey fuel = AEItemKey.of(Items.COAL);
        TrinityPatternVariant fuelled = variant(
                "fuelled",
                amounts(a, BigInteger.ONE, fuel, BigInteger.ONE),
                amounts(a, BigInteger.TWO));

        TrinityAlgorithmResult<Map<AEKey, BigInteger>> forged = TrinityExactConservationVerifier.create().verify(
                List.of(fuelled),
                Map.of(fuelled, BigInteger.ONE),
                Map.of(a, BigInteger.ONE),
                Map.of(a, BigInteger.ONE, fuel, BigInteger.ONE),
                Map.of(),
                a,
                BigInteger.ONE);

        assertFalse(forged.successful());
        assertEquals(TrinityPlanningDiagnosticCode.MIP_INEXACT_RESULT, forged.diagnostic().code());
        assertEquals("conservation", forged.diagnostic().metadata().get("constraint"));
    }

    @Test
    void jointPlanRejectsForgedConservation() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        TrinityPatternVariant multiply = variant(
                "multiply",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        TrinityJointCyclePlan valid = solve(
                component(a, multiply),
                a,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.ONE),
                100)
                .value();

        assertThrows(IllegalArgumentException.class, () -> new TrinityJointCyclePlan(
                valid.firings(),
                valid.externalInputs(),
                valid.minimumSeed(),
                valid.initialInputs(),
                Map.of(a, BigInteger.TWO),
                valid.schedule(),
                valid.searchStates(),
                valid.solverPasses(),
                valid.solverNanos()));
    }

    private static TrinityAlgorithmResult<TrinityJointCyclePlan> solve(
                                                                       TrinityStronglyConnectedComponent component,
                                                                       AEKey target,
                                                                       BigInteger amount,
                                                                       CraftingQuantityMode mode,
                                                                       Map<AEKey, BigInteger> available,
                                                                       int maxStates) {
        return TrinityJointCyclePlanner.create().plan(
                component,
                target,
                amount,
                mode,
                available,
                maxStates,
                TrinityPlanningControl.create(() -> false, () -> 0L, Long.MAX_VALUE));
    }

    private static TrinityStronglyConnectedComponent component(
                                                               AEKey key,
                                                               TrinityPatternVariant... variants) {
        return new TrinityStronglyConnectedComponent(
                0,
                List.of(key),
                true,
                List.of(variants),
                List.of(),
                List.of());
    }
}

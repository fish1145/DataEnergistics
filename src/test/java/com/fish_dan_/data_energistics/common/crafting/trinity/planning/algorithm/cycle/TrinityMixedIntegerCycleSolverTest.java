package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.cycle;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnostic;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
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

public final class TrinityMixedIntegerCycleSolverTest {

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
                () -> TrinityMixedIntegerCycleSolverImpl.diagnosticStates(diagnostic));
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

        TrinityMipCyclePlan plan = solve(
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
        assertTrue(plan.solverPasses() >= 3);
    }

    @Test
    void acceptsExecutablePrefixSeedAboveTheConservationLowerBound() {
        AEKey certus = AEItemKey.of(Items.QUARTZ);
        AEKey chargedCertus = AEItemKey.of(Items.AMETHYST_SHARD);
        AEKey certusDust = AEItemKey.of(Items.REDSTONE);
        AEKey water = AEItemKey.of(Items.WATER_BUCKET);

        TrinityAlgorithmResult<TrinityMipCyclePlan> result = solve(
                certusGrowthComponent(certus, chargedCertus, certusDust, water),
                certus,
                BigInteger.valueOf(256L),
                CraftingQuantityMode.NET_NEW,
                Map.of(certus, BigInteger.valueOf(1_024L), water, BigInteger.valueOf(100_000L)),
                500_000);

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        assertEquals(BigInteger.valueOf(256L), result.value().netChange().get(certus));
        assertEquals(BigInteger.valueOf(80L), result.value().minimumSeed().get(certus));
        assertEquals(BigInteger.valueOf(6_000L), result.value().externalInputs().get(water));
    }

    @Test
    void solvesChargedDustReactionGrowthCycleFromAvailableIntermediateSeeds() {
        AEKey certus = AEItemKey.of(Items.QUARTZ);
        AEKey chargedCertus = AEItemKey.of(Items.AMETHYST_SHARD);
        AEKey certusDust = AEItemKey.of(Items.REDSTONE);
        AEKey water = AEItemKey.of(Items.WATER_BUCKET);

        TrinityAlgorithmResult<TrinityMipCyclePlan> result = solve(
                certusGrowthComponent(certus, chargedCertus, certusDust, water),
                certus,
                BigInteger.valueOf(1_728L),
                CraftingQuantityMode.NET_NEW,
                Map.of(
                        chargedCertus, BigInteger.valueOf(256L),
                        certusDust, BigInteger.valueOf(320L),
                        water, BigInteger.valueOf(2_147_483_647_000L)),
                500_000);

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        assertEquals(BigInteger.valueOf(1_728L), result.value().netChange().get(certus));
        assertEquals(
                Map.of(
                        chargedCertus, BigInteger.valueOf(256L),
                        certusDust, BigInteger.valueOf(320L)),
                result.value().minimumSeed());
        assertEquals(BigInteger.valueOf(23_000L), result.value().externalInputs().get(water));
    }

    @Test
    void exactPrefixSeedParticipatesInRouteSelectionBeforeStableIdentity() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        TrinityPatternVariant identityFirstButHighSeed = variant(
                "a-high-seed",
                amounts(a, BigInteger.TEN),
                amounts(a, BigInteger.valueOf(11L)));
        TrinityPatternVariant identitySecondButLowSeed = variant(
                "b-low-seed",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO));

        TrinityMipCyclePlan plan = solve(
                component(a, identityFirstButHighSeed, identitySecondButLowSeed),
                a,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.TEN),
                100)
                .value();

        assertEquals(Map.of(identitySecondButLowSeed, BigInteger.ONE), plan.firings());
        assertEquals(BigInteger.ONE, plan.minimumSeed().get(a));
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

        TrinityMipCyclePlan plan = solve(
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
    void externalObjectiveIncludesAZeroNetCatalystPrefix() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey bucket = AEItemKey.of(Items.BUCKET);
        TrinityPatternVariant catalytic = variant(
                "catalytic",
                amounts(a, BigInteger.ONE, bucket, BigInteger.ONE),
                amounts(a, BigInteger.TWO, bucket, BigInteger.ONE));

        TrinityMipCyclePlan plan = solve(
                component(a, catalytic),
                a,
                BigInteger.valueOf(100L),
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.ONE, bucket, BigInteger.ONE),
                100)
                .value();

        assertEquals(BigInteger.ONE, plan.externalInputs().get(bucket));
        assertEquals(BigInteger.ONE, plan.minimumSeed().get(a));
        assertEquals(BigInteger.valueOf(100L), plan.firings().get(catalytic));
        assertTrue(plan.schedule().statesVisited() <= 2);
    }

    @Test
    void producibleBoundaryInputsAreDemandsInsteadOfCurrentInventoryCaps() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey fuel = AEItemKey.of(Items.COAL);
        TrinityPatternVariant fuelled = variant(
                "fuelled",
                amounts(a, BigInteger.ONE, fuel, BigInteger.ONE),
                amounts(a, BigInteger.TWO));

        TrinityAlgorithmResult<TrinityMipCyclePlan> result = TrinityMixedIntegerCycleSolver.create().solve(
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

        TrinityMipCyclePlan plan = solve(
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
    void reportsInfeasibleCancellationTimeoutAndCandidateLimit() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        AEKey fuel = AEItemKey.of(Items.COAL);
        TrinityPatternVariant fuelled = variant(
                "fuelled",
                amounts(a, BigInteger.ONE, fuel, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        TrinityAlgorithmResult<TrinityMipCyclePlan> infeasible = solve(
                component(a, fuelled),
                a,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.ONE),
                100);
        TrinityAlgorithmResult<TrinityMipCyclePlan> cancelled = TrinityMixedIntegerCycleSolver.create().solve(
                component(a, fuelled),
                a,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.ONE, fuel, BigInteger.ONE),
                100,
                TrinityPlanningControl.create(() -> true, () -> 0L, 1L));
        AtomicLong clock = new AtomicLong();
        TrinityAlgorithmResult<TrinityMipCyclePlan> timedOut = TrinityMixedIntegerCycleSolver.create().solve(
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
        TrinityAlgorithmResult<TrinityMipCyclePlan> limited = solve(
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
    void exactIntegerVerifierRejectsFractionalSolverValuesWithoutRounding() {
        TrinityIntegerResultVerifier verifier = TrinityIntegerResultVerifier.create();

        TrinityAlgorithmResult<List<BigInteger>> exact = verifier.verify(List.of(new BigDecimal("1.0000"), new BigDecimal("2")));
        TrinityAlgorithmResult<List<BigInteger>> inexact = verifier.verify(List.of(new BigDecimal("1"), new BigDecimal("2.0000001")));

        assertEquals(List.of(BigInteger.ONE, BigInteger.TWO), exact.value());
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
    void mipValueRejectsForgedConservation() {
        AEKey a = AEItemKey.of(Items.IRON_INGOT);
        TrinityPatternVariant multiply = variant(
                "multiply",
                amounts(a, BigInteger.ONE),
                amounts(a, BigInteger.TWO));
        TrinityMipCyclePlan valid = solve(
                component(a, multiply),
                a,
                BigInteger.ONE,
                CraftingQuantityMode.NET_NEW,
                Map.of(a, BigInteger.ONE),
                100)
                .value();

        assertThrows(IllegalArgumentException.class, () -> new TrinityMipCyclePlan(
                valid.firings(),
                valid.externalInputs(),
                valid.minimumSeed(),
                valid.initialInputs(),
                Map.of(a, BigInteger.TWO),
                valid.schedule(),
                valid.solverPasses(),
                valid.solverNanos()));
    }

    private static TrinityStronglyConnectedComponent certusGrowthComponent(
                                                                           AEKey certus,
                                                                           AEKey chargedCertus,
                                                                           AEKey certusDust,
                                                                           AEKey water) {
        TrinityPatternVariant charge = variant(
                "charge",
                amounts(certus, BigInteger.valueOf(64L), water, BigInteger.valueOf(1_000L)),
                amounts(chargedCertus, BigInteger.valueOf(64L)));
        TrinityPatternVariant pulverize = variant(
                "pulverize",
                amounts(certus, BigInteger.ONE),
                amounts(certusDust, BigInteger.ONE));
        TrinityPatternVariant react = variant(
                "react",
                amounts(
                        chargedCertus, BigInteger.valueOf(16L),
                        certusDust, BigInteger.valueOf(16L),
                        water, BigInteger.valueOf(500L)),
                amounts(certus, BigInteger.valueOf(64L)));
        return new TrinityStronglyConnectedComponent(
                0,
                List.of(certus, chargedCertus, certusDust),
                true,
                List.of(charge, pulverize, react),
                List.of(),
                List.of());
    }

    private static TrinityAlgorithmResult<TrinityMipCyclePlan> solve(
                                                                     TrinityStronglyConnectedComponent component,
                                                                     AEKey target,
                                                                     BigInteger amount,
                                                                     CraftingQuantityMode mode,
                                                                     Map<AEKey, BigInteger> available,
                                                                     int maxStates) {
        return TrinityMixedIntegerCycleSolver.create().solve(
                component,
                target,
                amount,
                mode,
                available,
                maxStates,
                TrinityPlanningControl.create(() -> false, () -> 0L, 1_000_000_000L));
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

package com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.orchestration;

import com.fish_dan_.data_energistics.common.crafting.trinity.planning.CraftingQuantityMode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.TrinityPlanningDiagnosticCode;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityAlgorithmResult;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.algorithm.TrinityPlanningControl;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphPattern;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityCraftingGraphSnapshot;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPatternIdentity;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.graph.TrinityPlanningGraphTestBootstrap;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityCraftingPlan;
import com.fish_dan_.data_energistics.common.crafting.trinity.planning.plan.TrinityPlanStage;
import com.fish_dan_.data_energistics.common.trinity.TrinityPatternPublicationSignature;
import com.fish_dan_.data_energistics.config.TrinityCraftingConfig;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TrinityGraphPlannerTest {

    @BeforeAll
    static void bootstrapRegistries() {
        TrinityPlanningGraphTestBootstrap.initialize();
    }

    @Test
    void composesDagProducedSeedAndFuelCycleBeforeDownstreamDag() {
        AEKey rawSeed = AEItemKey.of(Items.REDSTONE);
        AEKey rawFuel = AEItemKey.of(Items.STICK);
        AEKey seed = AEItemKey.of(Items.IRON_INGOT);
        AEKey fuel = AEItemKey.of(Items.COAL);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern seedPattern = pattern(
                "a-seed",
                Items.PAPER,
                List.of(stack(rawSeed, 1L)),
                List.of(stack(seed, 1L)));
        TrinityCraftingGraphPattern fuelPattern = pattern(
                "b-fuel",
                Items.MAP,
                List.of(stack(rawFuel, 1L)),
                List.of(stack(fuel, 1L)));
        TrinityCraftingGraphPattern cyclePattern = pattern(
                "c-cycle",
                Items.BOOK,
                List.of(stack(seed, 1L), stack(fuel, 1L)),
                List.of(stack(seed, 2L)));
        TrinityCraftingGraphPattern downstreamPattern = pattern(
                "d-downstream",
                Items.COMPASS,
                List.of(stack(seed, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                41L,
                List.of(seedPattern, fuelPattern, cyclePattern, downstreamPattern));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.valueOf(100L),
                CraftingQuantityMode.NET_NEW,
                Map.of(rawSeed, BigInteger.ONE, rawFuel, BigInteger.valueOf(100L)),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful());
        TrinityCraftingPlan plan = result.value();
        assertEquals(BigInteger.ONE, plan.initialExpectedInputs().get(rawSeed));
        assertEquals(BigInteger.valueOf(100L), plan.initialExpectedInputs().get(rawFuel));
        assertFalse(plan.initialExpectedInputs().containsKey(seed));
        assertFalse(plan.initialExpectedInputs().containsKey(fuel));
        assertEquals(BigInteger.ONE, plan.minimumSeed().get(seed));
        assertEquals(BigInteger.valueOf(100L), plan.minimumSeed().get(fuel));
        assertEquals(BigInteger.valueOf(100L), plan.targetNetChange().get(target));
        assertEquals(BigInteger.valueOf(100L), plan.patternFirings().get(cyclePattern.identity()));
        assertEquals(BigInteger.valueOf(100L), plan.patternFirings().get(downstreamPattern.identity()));
        assertEquals(1, plan.cycleRepeatBlocks().size());
        assertTrue(plan.statistics().scheduleStates() < 32);

        int seedStage = stageIndex(plan, seedPattern.identity());
        int fuelStage = stageIndex(plan, fuelPattern.identity());
        int cycleStage = stageIndex(plan, cyclePattern.identity());
        int downstreamStage = stageIndex(plan, downstreamPattern.identity());
        assertEquals(
                target,
                plan.stages().stream()
                        .filter(stage -> stage.index() == downstreamStage)
                        .findFirst()
                        .orElseThrow()
                        .firings()
                        .getFirst()
                        .primaryOutput());
        assertTrue(seedStage < cycleStage);
        assertTrue(fuelStage < cycleStage);
        assertTrue(cycleStage < downstreamStage);
        assertTrue(plan.stages().get(cycleStage).cycleStage());
    }

    @Test
    void treatsUpstreamMultiStepCycleDemandAsFinalBalanceForADagTarget() {
        AEKey material = AEItemKey.of(Items.IRON_INGOT);
        AEKey charged = AEItemKey.of(Items.GOLD_INGOT);
        AEKey dust = AEItemKey.of(Items.REDSTONE);
        AEKey fuel = AEItemKey.of(Items.STICK);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern charge = pattern(
                "upstream-charge",
                Items.PAPER,
                List.of(stack(material, 1L)),
                List.of(stack(charged, 1L)));
        TrinityCraftingGraphPattern pulverize = pattern(
                "upstream-pulverize",
                Items.MAP,
                List.of(stack(material, 1L)),
                List.of(stack(dust, 1L)));
        TrinityCraftingGraphPattern grow = pattern(
                "upstream-grow",
                Items.BOOK,
                List.of(stack(charged, 1L), stack(dust, 1L), stack(fuel, 1L)),
                List.of(stack(material, 4L)));
        TrinityCraftingGraphPattern finish = pattern(
                "downstream-finish",
                Items.COMPASS,
                List.of(stack(material, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                42L,
                List.of(charge, pulverize, grow, finish));

        TrinityAlgorithmResult<TrinityCraftingPlan> fullyStocked = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.valueOf(2L),
                CraftingQuantityMode.NET_NEW,
                Map.of(material, BigInteger.valueOf(2L)),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertTrue(fullyStocked.successful(), () -> fullyStocked.diagnostic().message().getString());
        assertTrue(fullyStocked.value().cycleRepeatBlocks().isEmpty());
        assertEquals(BigInteger.valueOf(2L), fullyStocked.value().initialExpectedInputs().get(material));
        assertEquals(BigInteger.valueOf(2L), fullyStocked.value().patternFirings().get(finish.identity()));
        assertFalse(fullyStocked.value().patternFirings().containsKey(grow.identity()));

        TrinityAlgorithmResult<TrinityCraftingPlan> partiallyStocked = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.valueOf(4L),
                CraftingQuantityMode.NET_NEW,
                Map.of(material, BigInteger.valueOf(2L), fuel, BigInteger.ONE),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertTrue(partiallyStocked.successful(), () -> partiallyStocked.diagnostic().message().getString());
        TrinityCraftingPlan partialPlan = partiallyStocked.value();
        assertEquals(BigInteger.ONE, partialPlan.patternFirings().get(charge.identity()));
        assertEquals(BigInteger.ONE, partialPlan.patternFirings().get(pulverize.identity()));
        assertEquals(BigInteger.ONE, partialPlan.patternFirings().get(grow.identity()));
        assertEquals(BigInteger.valueOf(4L), partialPlan.patternFirings().get(finish.identity()));
        assertEquals(BigInteger.valueOf(2L), partialPlan.initialExpectedInputs().get(material));
        assertEquals(BigInteger.ONE, partialPlan.initialExpectedInputs().get(fuel));
        assertEquals(1, partialPlan.cycleRepeatBlocks().size());
        assertTrue(stageIndex(partialPlan, grow.identity()) < stageIndex(partialPlan, finish.identity()));
    }

    @Test
    void composesSerialAndParallelCycleComponentsWithoutConsumingAnUpstreamSeedEarly() {
        AEKey first = AEItemKey.of(Items.IRON_INGOT);
        AEKey firstFuel = AEItemKey.of(Items.STICK);
        AEKey second = AEItemKey.of(Items.GOLD_INGOT);
        AEKey parallel = AEItemKey.of(Items.COPPER_INGOT);
        AEKey parallelFuel = AEItemKey.of(Items.COAL);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern firstGrowth = pattern(
                "first-growth",
                Items.PAPER,
                List.of(stack(first, 1L), stack(firstFuel, 1L)),
                List.of(stack(first, 2L)));
        TrinityCraftingGraphPattern secondGrowth = pattern(
                "second-growth",
                Items.MAP,
                List.of(stack(second, 1L), stack(first, 1L)),
                List.of(stack(second, 2L)));
        TrinityCraftingGraphPattern parallelGrowth = pattern(
                "parallel-growth",
                Items.BOOK,
                List.of(stack(parallel, 1L), stack(parallelFuel, 1L)),
                List.of(stack(parallel, 2L)));
        TrinityCraftingGraphPattern finish = pattern(
                "multi-cycle-finish",
                Items.COMPASS,
                List.of(stack(second, 1L), stack(parallel, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                43L,
                List.of(firstGrowth, secondGrowth, parallelGrowth, finish));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.valueOf(3L),
                CraftingQuantityMode.NET_NEW,
                Map.of(
                        first, BigInteger.ONE,
                        firstFuel, BigInteger.ONE,
                        second, BigInteger.ONE,
                        parallel, BigInteger.ONE,
                        parallelFuel, BigInteger.TWO),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityCraftingPlan plan = result.value();
        assertEquals(3, plan.cycleRepeatBlocks().size());
        assertEquals(BigInteger.ONE, plan.patternFirings().get(firstGrowth.identity()));
        assertEquals(BigInteger.TWO, plan.patternFirings().get(secondGrowth.identity()));
        assertEquals(BigInteger.TWO, plan.patternFirings().get(parallelGrowth.identity()));
        assertEquals(BigInteger.valueOf(3L), plan.patternFirings().get(finish.identity()));
        assertTrue(stageIndex(plan, firstGrowth.identity()) < stageIndex(plan, secondGrowth.identity()));
        assertTrue(stageIndex(plan, secondGrowth.identity()) < stageIndex(plan, finish.identity()));
        assertTrue(stageIndex(plan, parallelGrowth.identity()) < stageIndex(plan, finish.identity()));
    }

    @ParameterizedTest
    @ValueSource(longs = { 1L, 100_000_000L })
    void jointlySolvesMultipleSccAxesAndBoundaryOutputsForADagTarget(long requestedAmount) {
        BigInteger requested = BigInteger.valueOf(requestedAmount);
        AEKey first = AEItemKey.of(Items.IRON_INGOT);
        AEKey second = AEItemKey.of(Items.GOLD_INGOT);
        AEKey boundary = AEItemKey.of(Items.REDSTONE);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern firstToSecond = pattern(
                "joint-first-to-second",
                Items.PAPER,
                List.of(stack(first, 1L)),
                List.of(stack(second, 2L), stack(boundary, 1L)));
        TrinityCraftingGraphPattern secondToFirst = pattern(
                "joint-second-to-first",
                Items.MAP,
                List.of(stack(second, 1L)),
                List.of(stack(first, 2L)));
        TrinityCraftingGraphPattern finish = pattern(
                "joint-boundary-finish",
                Items.COMPASS,
                List.of(stack(first, 1L), stack(second, 3L), stack(boundary, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                44L,
                List.of(firstToSecond, secondToFirst, finish));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                requested,
                CraftingQuantityMode.NET_NEW,
                Map.of(first, BigInteger.ONE),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityCraftingPlan plan = result.value();
        BigInteger firstFirings = requested.multiply(BigInteger.valueOf(7L))
                .subtract(BigInteger.ONE)
                .add(BigInteger.TWO)
                .divide(BigInteger.valueOf(3L));
        BigInteger secondFirings = firstFirings.add(requested).subtract(BigInteger.ONE)
                .add(BigInteger.ONE)
                .divide(BigInteger.TWO);
        assertEquals(firstFirings, plan.patternFirings().get(firstToSecond.identity()));
        assertEquals(secondFirings, plan.patternFirings().get(secondToFirst.identity()));
        assertEquals(requested, plan.patternFirings().get(finish.identity()));
        assertEquals(BigInteger.ONE, plan.initialExpectedInputs().get(first));
        assertEquals(1, plan.cycleRepeatBlocks().size());
        assertTrue(stageIndex(plan, firstToSecond.identity()) < stageIndex(plan, finish.identity()));
    }

    @Test
    void backtracksFromAnUnavailableMixedRouteToACycleBackedRoute() {
        AEKey unavailable = AEItemKey.of(Items.EMERALD);
        AEKey material = AEItemKey.of(Items.IRON_INGOT);
        AEKey fuel = AEItemKey.of(Items.STICK);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern unavailableRoute = pattern(
                "a-unavailable-route",
                Items.PAPER,
                List.of(stack(unavailable, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphPattern growth = pattern(
                "material-growth",
                Items.MAP,
                List.of(stack(material, 1L), stack(fuel, 1L)),
                List.of(stack(material, 2L)));
        TrinityCraftingGraphPattern cycleBackedRoute = pattern(
                "z-cycle-backed-route",
                Items.COMPASS,
                List.of(stack(material, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                45L,
                List.of(unavailableRoute, growth, cycleBackedRoute));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.TWO,
                CraftingQuantityMode.NET_NEW,
                Map.of(material, BigInteger.ONE, fuel, BigInteger.ONE),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityCraftingPlan plan = result.value();
        assertFalse(plan.patternFirings().containsKey(unavailableRoute.identity()));
        assertEquals(BigInteger.ONE, plan.patternFirings().get(growth.identity()));
        assertEquals(BigInteger.TWO, plan.patternFirings().get(cycleBackedRoute.identity()));
    }

    @Test
    void plansLargeAcyclicRequestWithGraphBoundedStateCount() {
        AEKey raw = AEItemKey.of(Items.REDSTONE);
        AEKey intermediate = AEItemKey.of(Items.IRON_INGOT);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern upstream = pattern(
                "large-upstream",
                Items.PAPER,
                List.of(stack(raw, 3L)),
                List.of(stack(intermediate, 2L)));
        TrinityCraftingGraphPattern downstream = pattern(
                "large-downstream",
                Items.MAP,
                List.of(stack(intermediate, 5L)),
                List.of(stack(target, 1L)));
        List<TrinityCraftingGraphPattern> graphPatterns = new ArrayList<>(List.of(upstream, downstream));
        for (int index = 0; index < 600; index++) {
            graphPatterns.add(pattern(
                    "unrelated-" + index,
                    Items.STICK,
                    List.of(stack(AEItemKey.of(Items.DIRT), 1L)),
                    List.of(stack(AEItemKey.of(Items.COAL), 1L))));
        }
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(42L, graphPatterns);
        BigInteger requested = BigInteger.valueOf(1_000_000L);

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                requested,
                CraftingQuantityMode.NET_NEW,
                Map.of(raw, BigInteger.valueOf(7_500_000L)),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful());
        TrinityCraftingPlan plan = result.value();
        assertEquals(BigInteger.valueOf(2_500_000L), plan.patternFirings().get(upstream.identity()));
        assertEquals(requested, plan.patternFirings().get(downstream.identity()));
        assertEquals(BigInteger.valueOf(7_500_000L), plan.initialExpectedInputs().get(raw));
        assertEquals(requested, plan.targetNetChange().get(target));
        assertEquals(2, plan.statistics().variantCount());
        assertTrue(plan.statistics().scheduleStates() < 16);
    }

    @ParameterizedTest
    @ValueSource(longs = { 1L, 10_000L, 1_256_000_000L, Integer.MAX_VALUE })
    void plansAnAcyclicTargetOverMultiStepGrowthAcrossThePlayerRequestDomain(long requestedAmount) {
        BigInteger requested = BigInteger.valueOf(requestedAmount);
        BigInteger repetitions = requested.add(BigInteger.valueOf(127L)).divide(BigInteger.valueOf(128L));
        AEKey certus = AEItemKey.of(Items.QUARTZ);
        AEKey chargedCertus = AEItemKey.of(Items.AMETHYST_SHARD);
        AEKey certusDust = AEItemKey.of(Items.REDSTONE);
        AEKey water = AEItemKey.of(Items.WATER_BUCKET);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphPattern charge = pattern(
                "growth-charge",
                Items.PAPER,
                List.of(stack(certus, 64L), stack(water, 1_000L)),
                List.of(stack(chargedCertus, 64L)));
        TrinityCraftingGraphPattern pulverize = pattern(
                "growth-pulverize",
                Items.MAP,
                List.of(stack(certus, 1L)),
                List.of(stack(certusDust, 1L)));
        TrinityCraftingGraphPattern react = pattern(
                "growth-react",
                Items.BOOK,
                List.of(stack(chargedCertus, 16L), stack(certusDust, 16L), stack(water, 500L)),
                List.of(stack(certus, 64L)));
        TrinityCraftingGraphPattern finish = pattern(
                "growth-finish",
                Items.COMPASS,
                List.of(stack(certus, 1L)),
                List.of(stack(target, 1L)));
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                43L,
                List.of(charge, pulverize, react, finish));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                requested,
                CraftingQuantityMode.NET_NEW,
                Map.of(
                        chargedCertus, BigInteger.valueOf(256L),
                        certusDust, BigInteger.valueOf(320L),
                        water, BigInteger.valueOf(2_147_483_647_000L)),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertTrue(result.successful(), () -> result.diagnostic().message().getString());
        TrinityCraftingPlan plan = result.value();
        assertEquals(repetitions, plan.patternFirings().get(charge.identity()));
        assertEquals(repetitions.multiply(BigInteger.valueOf(64L)), plan.patternFirings().get(pulverize.identity()));
        assertEquals(repetitions.multiply(BigInteger.valueOf(4L)), plan.patternFirings().get(react.identity()));
        assertEquals(requested, plan.patternFirings().get(finish.identity()));
        assertEquals(requested, plan.targetNetChange().get(target));
        assertEquals(
                repetitions.multiply(BigInteger.valueOf(128L)).subtract(requested),
                plan.targetNetChange().getOrDefault(certus, BigInteger.ZERO));
        assertEquals(repetitions.multiply(BigInteger.valueOf(3_000L)), plan.initialExpectedInputs().get(water));
        assertEquals(BigInteger.valueOf(64L), plan.minimumSeed().get(chargedCertus));
        assertEquals(BigInteger.valueOf(64L), plan.minimumSeed().get(certusDust));
        assertFalse(plan.targetNetChange().containsKey(chargedCertus));
        assertFalse(plan.targetNetChange().containsKey(certusDust));
        assertEquals(1, plan.cycleRepeatBlocks().size());
        assertTrue(stageIndex(plan, react.identity()) < stageIndex(plan, finish.identity()));
        assertTrue(
                plan.statistics().scheduleStates() <= 128,
                () -> "compressed states=" + plan.statistics().scheduleStates());
    }

    @Test
    void reportsArithmeticOverflowBeforeCrossingAnAe2LongBoundary() {
        AEKey raw = AEItemKey.of(Items.REDSTONE);
        AEKey target = AEItemKey.of(Items.DIAMOND);
        TrinityCraftingGraphSnapshot snapshot = new TrinityCraftingGraphSnapshot(
                42L,
                List.of(pattern(
                        "linear",
                        Items.PAPER,
                        List.of(stack(raw, 1L)),
                        List.of(stack(target, 1L)))));

        TrinityAlgorithmResult<TrinityCraftingPlan> result = TrinityGraphPlanner.create().plan(
                snapshot,
                target,
                BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE),
                CraftingQuantityMode.NET_NEW,
                Map.of(raw, BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE)),
                TrinityCraftingConfig.Settings.defaults(4),
                unlimitedControl());

        assertFalse(result.successful());
        assertEquals(TrinityPlanningDiagnosticCode.ARITHMETIC_OVERFLOW, result.diagnostic().code());
    }

    private static int stageIndex(TrinityCraftingPlan plan, TrinityPatternIdentity identity) {
        for (TrinityPlanStage stage : plan.stages()) {
            if (stage.firings().stream().anyMatch(firing -> firing.patternIdentity().equals(identity))) {
                return stage.index();
            }
        }
        throw new AssertionError("Pattern stage is absent: " + identity);
    }

    private static TrinityCraftingGraphPattern pattern(
                                                       String name,
                                                       Item definition,
                                                       List<GenericStack> inputs,
                                                       List<GenericStack> outputs) {
        ArrayList<TrinityPatternPublicationSignature.Input> capturedInputs = new ArrayList<>();
        for (GenericStack input : inputs) {
            capturedInputs.add(new TrinityPatternPublicationSignature.Input(
                    1L,
                    List.of(new TrinityPatternPublicationSignature.Alternative(input, null))));
        }
        TrinityPatternPublicationSignature publication = new TrinityPatternPublicationSignature(
                AEItemKey.of(definition),
                capturedInputs,
                outputs,
                false);
        return new TrinityCraftingGraphPattern(
                new TrinityPatternIdentity("definition-" + name, "publication-" + name),
                publication);
    }

    private static GenericStack stack(AEKey key, long amount) {
        return new GenericStack(key, amount);
    }

    private static TrinityPlanningControl unlimitedControl() {
        return TrinityPlanningControl.create(() -> false, () -> 0L, Long.MAX_VALUE);
    }
}
